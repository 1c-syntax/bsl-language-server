/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Fedkin <nixel2007@gmail.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * BSL Language Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * BSL Language Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BSL Language Server.
 */
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SemanticTokensProvider {

  private final SemanticTokensLegend legend;

  public SemanticTokens getSemanticTokensFull(DocumentContext documentContext, @SuppressWarnings("unused") SemanticTokensParams params) {
    List<TokenEntry> entries = new ArrayList<>();

    // 1) Symbols: methods/functions, variables, parameters
    var symbolTree = documentContext.getSymbolTree();
    for (MethodSymbol method : symbolTree.getMethods()) {
      addRange(entries, method.getSubNameRange(), method.isFunction() ? SemanticTokenTypes.Function : SemanticTokenTypes.Method);
      for (ParameterDefinition parameter : method.getParameters()) {
        addRange(entries, parameter.getRange(), SemanticTokenTypes.Parameter);
      }
    }
    for (VariableSymbol variableSymbol : symbolTree.getVariables()) {
      addRange(entries, variableSymbol.getVariableNameRange(), SemanticTokenTypes.Variable);
    }

    // 2) Comments (lexer type LINE_COMMENT)
    for (Token commentToken : documentContext.getComments()) {
      addTokenLike(entries, commentToken.getLine(), commentToken.getCharPositionInLine(), commentToken.getText(), SemanticTokenTypes.Comment);
    }

    // 3) AST-driven annotations and compiler directives
    addAnnotationsFromAst(entries, documentContext);
    addPreprocessorFromAst(entries, documentContext);

    // 4) Lexical tokens on default channel: strings, numbers, macros, operators, keywords
    List<Token> tokens = documentContext.getTokensFromDefaultChannel();
    for (Token token : tokens) {
      final int tokenTypeInt = token.getType();
      final String tokenText = Objects.toString(token.getText(), "");
      if (tokenText.isEmpty()) {
        continue;
      }

      int zeroBasedLine = token.getLine() - 1;

      // strings
      if (STRING_TYPES.contains(tokenTypeInt)) {
        addTokenLike(entries, token.getLine(), token.getCharPositionInLine(), tokenText, SemanticTokenTypes.String);
        continue;
      }

      // date literals in single quotes
      if (tokenTypeInt == BSLLexer.DATETIME) {
        addToken(entries, zeroBasedLine, token.getCharPositionInLine(), tokenText.length(), SemanticTokenTypes.String);
        continue;
      }

      // numbers
      if (NUMBER_TYPES.contains(tokenTypeInt)) {
        addToken(entries, zeroBasedLine, token.getCharPositionInLine(), tokenText.length(), SemanticTokenTypes.Number);
        continue;
      }

      // operators and punctuators
      if (OPERATOR_TYPES.contains(tokenTypeInt)) {
        addToken(entries, zeroBasedLine, token.getCharPositionInLine(), tokenText.length(), SemanticTokenTypes.Operator);
        continue;
      }

      // Skip '&' and all ANNOTATION_* symbol tokens here to avoid duplicate Decorator emission (handled via AST)
      if (tokenTypeInt == BSLLexer.AMPERSAND || ANNOTATION_TOKENS.contains(tokenTypeInt)) {
        continue;
      }

      // specific literals as keywords: undefined/boolean/null
      if (tokenTypeInt == BSLLexer.UNDEFINED || tokenTypeInt == BSLLexer.TRUE || tokenTypeInt == BSLLexer.FALSE || tokenTypeInt == BSLLexer.NULL) {
        addToken(entries, zeroBasedLine, token.getCharPositionInLine(), tokenText.length(), SemanticTokenTypes.Keyword);
        continue;
      }

      // keywords (by symbolic name suffix), skip PREPROC_* (handled via AST)
      String symbolicName = BSLLexer.VOCABULARY.getSymbolicName(tokenTypeInt);
      if (symbolicName != null && symbolicName.endsWith("_KEYWORD") && !symbolicName.startsWith("PREPROC_")) {
        addToken(entries, zeroBasedLine, token.getCharPositionInLine(), tokenText.length(), SemanticTokenTypes.Keyword);
      }
    }

    // 5) Build delta-encoded data
    List<Integer> data = toDeltaEncoded(entries);
    return new SemanticTokens(data);
  }

  private void addAnnotationsFromAst(List<TokenEntry> entries, DocumentContext documentContext) {
    ParseTree parseTree = documentContext.getAst();

    // compiler directives: single Decorator from '&' through directive symbol
    for (var compilerDirectiveRule : Trees.findAllRuleNodes(parseTree, BSLParser.RULE_compilerDirective)) {
      var compilerDirective = (BSLParser.CompilerDirectiveContext) compilerDirectiveRule;
      Token ampersandToken = compilerDirective.getStart(); // '&'
      if (compilerDirective.compilerDirectiveSymbol() != null) {
        Token symbolToken = compilerDirective.compilerDirectiveSymbol().getStart();
        addRange(entries, Ranges.create(ampersandToken, symbolToken), SemanticTokenTypes.Decorator);
      } else {
        addRange(entries, Ranges.create(ampersandToken), SemanticTokenTypes.Decorator);
      }
    }

    // annotations: single Decorator from '&' through annotation name; params identifiers as Parameter
    for (var annotationRule : Trees.findAllRuleNodes(parseTree, BSLParser.RULE_annotation)) {
      var annotation = (BSLParser.AnnotationContext) annotationRule;
      Token ampersandToken = annotation.getStart(); // '&'
      if (annotation.annotationName() != null) {
        Token annotationNameToken = annotation.annotationName().getStart();
        addRange(entries, Ranges.create(ampersandToken, annotationNameToken), SemanticTokenTypes.Decorator);
      } else {
        addRange(entries, Ranges.create(ampersandToken), SemanticTokenTypes.Decorator);
      }

      var annotationParams = annotation.annotationParams();
      if (annotationParams != null) {
        for (var nameRule : Trees.findAllRuleNodes(annotationParams, BSLParser.RULE_annotationParamName)) {
          var annotationParamName = (ParserRuleContext) nameRule;
          addRange(entries, Ranges.create(annotationParamName.getStart()), SemanticTokenTypes.Parameter);
        }
      }
    }
  }

  private void addPreprocessorFromAst(List<TokenEntry> entries, DocumentContext documentContext) {
    ParseTree parseTree = documentContext.getAst();

    // 1) Regions as Namespace: handle all regionStart and regionEnd nodes explicitly
    for (var regionStartRuleNode : Trees.findAllRuleNodes(parseTree, BSLParser.RULE_regionStart)) {
      addNamespaceForPreprocessorNode(entries, (ParserRuleContext) regionStartRuleNode);
    }
    for (var regionEndRuleNode : Trees.findAllRuleNodes(parseTree, BSLParser.RULE_regionEnd)) {
      addNamespaceForPreprocessorNode(entries, (ParserRuleContext) regionEndRuleNode);
    }

    // 1.1) Use directives as Namespace: #Использовать ...
    for (var useRuleNode : Trees.findAllRuleNodes(parseTree, BSLParser.RULE_use)) {
      addNamespaceForUse(entries, (BSLParser.UseContext) useRuleNode);
    }

    // 2) Other preprocessor directives: Macro for each HASH and PREPROC_* token, excluding region/use
    for (var preprocessorRule : Trees.findAllRuleNodes(parseTree, BSLParser.RULE_preprocessor)) {
      var preprocessor = (BSLParser.PreprocessorContext) preprocessorRule;
      boolean containsRegionOrUse = !Trees.findAllRuleNodes(preprocessor, BSLParser.RULE_regionStart).isEmpty()
        || !Trees.findAllRuleNodes(preprocessor, BSLParser.RULE_regionEnd).isEmpty()
        || !Trees.findAllRuleNodes(preprocessor, BSLParser.RULE_use).isEmpty();
      if (containsRegionOrUse) {
        continue; // already handled as Namespace
      }
      for (Token token : Trees.getTokens(preprocessor)) {
        if (token.getChannel() != Token.DEFAULT_CHANNEL) continue;
        String symbolicName = BSLLexer.VOCABULARY.getSymbolicName(token.getType());
        if ("HASH".equals(symbolicName) || (symbolicName != null && symbolicName.startsWith("PREPROC_"))) {
          addRange(entries, Ranges.create(token), SemanticTokenTypes.Macro);
        }
      }
    }
  }

  private void addNamespaceForPreprocessorNode(List<TokenEntry> entries, ParserRuleContext preprocessorChildNode) {
    var preprocessor = (BSLParser.PreprocessorContext) Trees.getAncestorByRuleIndex((BSLParserRuleContext) preprocessorChildNode, BSLParser.RULE_preprocessor);
    if (preprocessor == null) {
      return;
    }
    Token hashToken = preprocessor.getStart();
    Token endToken = preprocessorChildNode.getStop();
    if (hashToken == null) {
      return;
    }
    addRange(entries, Ranges.create(hashToken, endToken), SemanticTokenTypes.Namespace);
  }

  private void addNamespaceForUse(List<TokenEntry> entries, BSLParser.UseContext useCtx) {
    TerminalNode hashNode = useCtx.HASH();
    TerminalNode useNode = useCtx.PREPROC_USE_KEYWORD();

    if (hashNode != null && useNode != null) {
      addRange(entries, Ranges.create(hashNode, useNode), SemanticTokenTypes.Namespace);
    } else if (hashNode != null) {
      addRange(entries, Ranges.create(hashNode), SemanticTokenTypes.Namespace);
    }

    Optional.ofNullable(useCtx.usedLib())
      .map(BSLParser.UsedLibContext::PREPROC_IDENTIFIER)
      .ifPresent(id -> addRange(entries, Ranges.create(id), SemanticTokenTypes.Variable));
  }

  private void addRange(List<TokenEntry> entries, Range range, String type) {
    if (Ranges.isEmpty(range)) {
      return;
    }
    int typeIdx = legend.getTokenTypes().indexOf(type);
    if (typeIdx < 0) {
      return;
    }
    int line = range.getStart().getLine();
    int start = range.getStart().getCharacter();
    int length = Math.max(0, range.getEnd().getCharacter() - range.getStart().getCharacter());
    if (length > 0) {
      entries.add(new TokenEntry(line, start, length, typeIdx, 0));
    }
  }

  private void addTokenLike(List<TokenEntry> entries, int antlrStartLine1, int startChar, String text, String type) {
    // Split by newlines to keep single-line semantic tokens
    String[] parts = text.split("\r?\n|\r", -1);
    int line = antlrStartLine1 - 1; // LSP lines are 0-based
    int col = startChar;
    for (String part : parts) {
      if (!part.isEmpty()) {
        addToken(entries, line, col, part.length(), type);
      }
      // Subsequent lines start at column 0
      line++;
      col = 0;
    }
  }

  private void addToken(List<TokenEntry> entries, int line, int start, int length, String type) {
    int typeIdx = legend.getTokenTypes().indexOf(type);
    if (typeIdx < 0) {
      return; // type not announced in legend
    }
    entries.add(new TokenEntry(line, start, length, typeIdx, 0));
  }

  private List<Integer> toDeltaEncoded(List<TokenEntry> entries) {
    // de-dup and sort
    Set<TokenEntry> uniq = new HashSet<>(entries);
    List<TokenEntry> sorted = new ArrayList<>(uniq);
    sorted.sort(Comparator
      .comparingInt(TokenEntry::line)
      .thenComparingInt(TokenEntry::start));

    List<Integer> data = new ArrayList<>(sorted.size() * 5);
    int prevLine = 0;
    int prevChar = 0;
    boolean first = true;

    for (TokenEntry tokenEntry : sorted) {
      int deltaLine = first ? tokenEntry.line : tokenEntry.line - prevLine;
      int deltaStart = first ? tokenEntry.start : tokenEntry.start - (deltaLine == 0 ? prevChar : 0);
      data.add(deltaLine);
      data.add(deltaStart);
      data.add(tokenEntry.length);
      data.add(tokenEntry.type);
      data.add(tokenEntry.modifiers);

      prevLine = tokenEntry.line;
      prevChar = tokenEntry.start;
      first = false;
    }
    return data;
  }

  private record TokenEntry(int line, int start, int length, int type, int modifiers) { }

  private static final Set<Integer> NUMBER_TYPES = Set.of(
    BSLLexer.DECIMAL,
    BSLLexer.FLOAT
  );

  private static final Set<Integer> STRING_TYPES = Set.of(
    BSLLexer.STRING,
    BSLLexer.STRINGPART,
    BSLLexer.STRINGSTART,
    BSLLexer.STRINGTAIL,
    // preprocessor string literal for #Использовать "путь"
    BSLLexer.PREPROC_STRING
  );

  private static final Set<Integer> OPERATOR_TYPES = Set.of(
    BSLLexer.LPAREN,
    BSLLexer.RPAREN,
    BSLLexer.LBRACK,
    BSLLexer.RBRACK,
    BSLLexer.COMMA,
    BSLLexer.SEMICOLON,
    BSLLexer.COLON,
    BSLLexer.DOT,
    BSLLexer.PLUS,
    BSLLexer.MINUS,
    BSLLexer.MUL,
    BSLLexer.QUOTIENT,
    BSLLexer.MODULO,
    BSLLexer.ASSIGN,
    BSLLexer.NOT_EQUAL,
    BSLLexer.LESS,
    BSLLexer.LESS_OR_EQUAL,
    BSLLexer.GREATER,
    BSLLexer.GREATER_OR_EQUAL,
    BSLLexer.QUESTION,
    BSLLexer.TILDA
  );

  private static final Set<Integer> ANNOTATION_TOKENS = Set.of(
    BSLLexer.ANNOTATION_ATSERVERNOCONTEXT_SYMBOL,
    BSLLexer.ANNOTATION_ATCLIENTATSERVERNOCONTEXT_SYMBOL,
    BSLLexer.ANNOTATION_ATCLIENTATSERVER_SYMBOL,
    BSLLexer.ANNOTATION_ATCLIENT_SYMBOL,
    BSLLexer.ANNOTATION_ATSERVER_SYMBOL,
    BSLLexer.ANNOTATION_BEFORE_SYMBOL,
    BSLLexer.ANNOTATION_AFTER_SYMBOL,
    BSLLexer.ANNOTATION_AROUND_SYMBOL,
    BSLLexer.ANNOTATION_CHANGEANDVALIDATE_SYMBOL,
    BSLLexer.ANNOTATION_CUSTOM_SYMBOL
  );
}
