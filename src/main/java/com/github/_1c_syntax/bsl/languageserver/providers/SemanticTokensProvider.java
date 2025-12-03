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
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.context.symbol.description.MethodDescription;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableDescription;
import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParser.AnnotationContext;
import com.github._1c_syntax.bsl.parser.BSLParser.AnnotationParamNameContext;
import com.github._1c_syntax.bsl.parser.BSLParser.CompilerDirectiveContext;
import com.github._1c_syntax.bsl.parser.BSLParser.Preproc_nativeContext;
import com.github._1c_syntax.bsl.parser.BSLParser.PreprocessorContext;
import com.github._1c_syntax.bsl.parser.BSLParser.RegionEndContext;
import com.github._1c_syntax.bsl.parser.BSLParser.RegionStartContext;
import com.github._1c_syntax.bsl.parser.BSLParser.UseContext;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensCapabilities;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Провайдер для предоставления семантических токенов.
 * <p>
 * Обрабатывает запросы {@code textDocument/semanticTokens/full}.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_semanticTokens">Semantic Tokens specification</a>
 */
@Component
@RequiredArgsConstructor
public class SemanticTokensProvider {

  private static final Set<Integer> NUMBER_TYPES = Set.of(
    BSLLexer.DECIMAL,
    BSLLexer.FLOAT
  );

  private static final Set<Integer> STRING_TYPES = Set.of(
    BSLLexer.STRING,
    BSLLexer.STRINGPART,
    BSLLexer.STRINGSTART,
    BSLLexer.STRINGTAIL,
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

  private static final String[] NO_MODIFIERS = new String[0];
  private static final String[] DOC_ONLY = new String[]{SemanticTokenModifiers.Documentation};

  private final SemanticTokensLegend legend;
  private final ReferenceResolver referenceResolver;
  private final ReferenceIndex referenceIndex;

  @Setter(AccessLevel.PROTECTED)
  private boolean multilineTokenSupport;

  /**
   * Обработчик события инициализации языкового сервера.
   * <p>
   * Проверяет возможности клиента и определяет, поддерживаются ли многострочные токены.
   *
   * @param event Событие инициализации сервера
   */
  @EventListener
  public void onClientCapabilitiesChanged(LanguageServerInitializeRequestReceivedEvent event) {
    multilineTokenSupport = Optional.of(event)
      .map(LanguageServerInitializeRequestReceivedEvent::getParams)
      .map(InitializeParams::getCapabilities)
      .map(ClientCapabilities::getTextDocument)
      .map(TextDocumentClientCapabilities::getSemanticTokens)
      .map(SemanticTokensCapabilities::getMultilineTokenSupport)
      .orElse(false);
  }

  /**
   * Получить семантические токены для всего документа.
   *
   * @param documentContext Контекст документа
   * @param params Параметры запроса
   * @return Семантические токены в дельта-кодированном формате
   */
  public SemanticTokens getSemanticTokensFull(DocumentContext documentContext, @SuppressWarnings("unused") SemanticTokensParams params) {
    List<TokenEntry> entries = new ArrayList<>();

    // collect description ranges for describable symbols
    List<Range> descriptionRanges = new ArrayList<>();
    var documentationLines = new BitSet();

    var symbolTree = documentContext.getSymbolTree();
    var ast = documentContext.getAst();
    var uri = documentContext.getUri();
    var comments = documentContext.getComments();
    var tokensFromDefaultChannel = documentContext.getTokensFromDefaultChannel();

    // 1) Symbols: methods/functions, variables, parameters

    addMethodSymbols(symbolTree, entries, descriptionRanges, documentationLines);
    addVariableSymbols(documentContext, symbolTree, entries, descriptionRanges, documentationLines);

    addMultilineDescriptions(documentContext, descriptionRanges, entries);

    // 2) Comments (lexer type LINE_COMMENT)
    addComments(comments, descriptionRanges, entries, documentationLines);

    // 3) AST-driven annotations and compiler directives
    addAnnotationsFromAst(entries, ast);
    addPreprocessorFromAst(entries, ast);

    // 3.1) Method call occurrences as Method tokens
    addMethodCallTokens(entries, uri);

    // 4) Lexical tokens on default channel: strings, numbers, macros, operators, keywords
    addLexicalTokens(tokensFromDefaultChannel, entries);

    // 5) Build delta-encoded data
    List<Integer> data = toDeltaEncoded(entries);
    return new SemanticTokens(data);
  }

  private void addMultilineDescriptions(DocumentContext documentContext, List<Range> descriptionRanges, List<TokenEntry> entries) {
    if (!multilineTokenSupport) {
      return;
    }

    for (Range r : descriptionRanges) {
      // compute multi-line token length using document text
      int length = documentContext.getText(r).length();
      addRange(entries, r, length, SemanticTokenTypes.Comment, DOC_ONLY);
    }
  }

  private void addVariableSymbols(DocumentContext documentContext, SymbolTree symbolTree, List<TokenEntry> entries, List<Range> descriptionRanges, BitSet documentationLines) {
    for (var variableSymbol : symbolTree.getVariables()) {
      var nameRange = variableSymbol.getVariableNameRange();
      if (!Ranges.isEmpty(nameRange)) {
        Position pos = nameRange.getStart();
        boolean isDefinition = referenceResolver.findReference(documentContext.getUri(), pos)
          .map(ref -> ref.getOccurrenceType() == OccurrenceType.DEFINITION)
          .orElse(false);
        if (isDefinition) {
          addRange(entries, nameRange, SemanticTokenTypes.Variable, SemanticTokenModifiers.Definition);
        } else {
          addRange(entries, nameRange, SemanticTokenTypes.Variable);
        }
      }
      variableSymbol.getDescription().ifPresent((VariableDescription description) -> {
        processVariableDescription(descriptionRanges, documentationLines, description);

        description.getTrailingDescription().ifPresent((VariableDescription trailingDescription) ->
          processVariableDescription(descriptionRanges, documentationLines, trailingDescription)
        );
      });
    }
  }

  private void processVariableDescription(List<Range> descriptionRanges, BitSet documentationLines, VariableDescription description) {
    var range = description.getRange();
    if (Ranges.isEmpty(range)) {
      return;
    }

    descriptionRanges.add(range);
    if (!multilineTokenSupport) {
      markLines(documentationLines, range);
    }
  }

  private void addMethodSymbols(SymbolTree symbolTree, List<TokenEntry> entries, List<Range> descriptionRanges, BitSet documentationLines) {
    for (var method : symbolTree.getMethods()) {
      var semanticTokenType = method.isFunction() ? SemanticTokenTypes.Function : SemanticTokenTypes.Method;
      addRange(entries, method.getSubNameRange(), semanticTokenType);
      for (ParameterDefinition parameter : method.getParameters()) {
        addRange(entries, parameter.getRange(), SemanticTokenTypes.Parameter);
      }
      method.getDescription()
        .map(MethodDescription::getRange)
        .filter(r -> !Ranges.isEmpty(r))
        .ifPresent(r -> {
          descriptionRanges.add(r);
          if (!multilineTokenSupport) {
            markLines(documentationLines, r);
          }
        });
    }
  }

  private void addComments(List<Token> comments, List<Range> descriptionRanges, List<TokenEntry> entries, BitSet documentationLines) {
    for (var commentToken : comments) {
      var commentRange = Ranges.create(commentToken);
      if (multilineTokenSupport) {
        boolean insideDescription = descriptionRanges.stream().anyMatch(r -> Ranges.containsRange(r, commentRange));
        if (insideDescription) {
          continue;
        }
        addRange(entries, commentRange, SemanticTokenTypes.Comment);
      } else {
        int commentLine = commentToken.getLine() - 1;
        boolean isDocumentation = documentationLines.get(commentLine);
        if (isDocumentation) {
          addRange(entries, commentRange, SemanticTokenTypes.Comment, DOC_ONLY);
        } else {
          addRange(entries, commentRange, SemanticTokenTypes.Comment);
        }
      }
    }
  }

  private static void markLines(BitSet lines, Range range) {
    int startLine = range.getStart().getLine();
    int endLine = range.getEnd().getLine();
    lines.set(startLine, endLine + 1); // inclusive end
  }

  private void addAnnotationsFromAst(List<TokenEntry> entries, ParseTree parseTree) {
    // compiler directives: single Decorator from '&' through directive symbol
    for (var compilerDirective : Trees.<CompilerDirectiveContext>findAllRuleNodes(parseTree, BSLParser.RULE_compilerDirective)) {
      var ampersand = compilerDirective.AMPERSAND().getSymbol(); // '&'
      if (compilerDirective.compilerDirectiveSymbol() != null) {
        var symbolToken = compilerDirective.compilerDirectiveSymbol().getStart();
        addRange(entries, Ranges.create(ampersand, symbolToken), SemanticTokenTypes.Decorator);
      } else {
        addRange(entries, Ranges.create(ampersand), SemanticTokenTypes.Decorator);
      }
    }

    // annotations: single Decorator from '&' through annotation name; params identifiers as Parameter
    for (var annotation : Trees.<AnnotationContext>findAllRuleNodes(parseTree, BSLParser.RULE_annotation)) {
      var ampersand = annotation.AMPERSAND().getSymbol(); // '&'
      if (annotation.annotationName() != null) {
        var annotationNameToken = annotation.annotationName().getStart();
        addRange(entries, Ranges.create(ampersand, annotationNameToken), SemanticTokenTypes.Decorator);
      } else {
        addRange(entries, Ranges.create(ampersand), SemanticTokenTypes.Decorator);
      }

      var annotationParams = annotation.annotationParams();
      if (annotationParams != null) {
        for (var annotationParamName : Trees.<AnnotationParamNameContext>findAllRuleNodes(annotationParams, BSLParser.RULE_annotationParamName)) {
          addRange(entries, Ranges.create(annotationParamName.IDENTIFIER()), SemanticTokenTypes.Parameter);
        }
      }
    }
  }

  private void addPreprocessorFromAst(List<TokenEntry> entries, ParseTree parseTree) {
    // 1) Regions as Namespace: handle all regionStart and regionEnd nodes explicitly
    for (var regionStart : Trees.<RegionStartContext>findAllRuleNodes(parseTree, BSLParser.RULE_regionStart)) {
      // Namespace only for '#'+keyword part to avoid overlap with region name token
      var preprocessor = Trees.<PreprocessorContext>getAncestorByRuleIndex(regionStart, BSLParser.RULE_preprocessor);
      if (preprocessor != null && regionStart.PREPROC_REGION() != null) {
        addRange(entries, Ranges.create(preprocessor.getStart(), regionStart.PREPROC_REGION().getSymbol()), SemanticTokenTypes.Namespace);
      } else {
        addNamespaceForPreprocessorNode(entries, regionStart);
      }
      // region name highlighted as Variable (consistent with #Использовать <libName>)
      if (regionStart.regionName() != null) {
        addRange(entries, Ranges.create(regionStart.regionName()), SemanticTokenTypes.Variable);
      }
    }
    for (var regionEnd : Trees.<RegionEndContext>findAllRuleNodes(parseTree, BSLParser.RULE_regionEnd)) {
      addNamespaceForPreprocessorNode(entries, regionEnd);
    }

    // 1.1) Use directives as Namespace: #Использовать ... (moduleAnnotations scope)
    for (var use : Trees.<UseContext>findAllRuleNodes(parseTree, BSLParser.RULE_use)) {
      addNamespaceForUse(entries, use);
    }

    // 1.2) Native directives as Macro: #NATIVE (moduleAnnotations scope)
    for (var nativeCtx : Trees.<Preproc_nativeContext>findAllRuleNodes(parseTree, BSLParser.RULE_preproc_native)) {
      var hash = nativeCtx.HASH();
      var nativeKw = nativeCtx.PREPROC_NATIVE();
      if (hash != null) {
        addRange(entries, Ranges.create(hash), SemanticTokenTypes.Macro);
      }
      if (nativeKw != null) {
        addRange(entries, Ranges.create(nativeKw), SemanticTokenTypes.Macro);
      }
    }

    // 2) Other preprocessor directives: Macro for each HASH and PREPROC_* token,
    // excluding region start/end (handled as Namespace)
    for (var preprocessor : Trees.<PreprocessorContext>findAllRuleNodes(parseTree, BSLParser.RULE_preprocessor)) {
      boolean containsRegion = (preprocessor.regionStart() != null) || (preprocessor.regionEnd() != null);
      if (containsRegion) {
        continue; // region handled as Namespace above
      }

      for (Token token : Trees.getTokens(preprocessor)) {
        if (token.getChannel() != Token.DEFAULT_CHANNEL) {
          continue;
        }
        String symbolicName = BSLLexer.VOCABULARY.getSymbolicName(token.getType());
        if (token.getType() == BSLLexer.HASH || (symbolicName != null && symbolicName.startsWith("PREPROC_"))) {
          addRange(entries, Ranges.create(token), SemanticTokenTypes.Macro);
        }
      }
    }
  }

  private void addNamespaceForPreprocessorNode(List<TokenEntry> entries, ParserRuleContext preprocessorChildNode) {
    var preprocessor = Trees.<PreprocessorContext>getAncestorByRuleIndex(preprocessorChildNode, BSLParser.RULE_preprocessor);
    if (preprocessor == null) {
      return;
    }
    var hashToken = preprocessor.getStart();
    if (hashToken == null) {
      return;
    }
    var endToken = preprocessorChildNode.getStop();
    addRange(entries, Ranges.create(hashToken, endToken), SemanticTokenTypes.Namespace);
  }

  private void addNamespaceForUse(List<TokenEntry> entries, UseContext useCtx) {
    TerminalNode hashNode = useCtx.HASH();
    TerminalNode useNode = useCtx.PREPROC_USE_KEYWORD();

    if (hashNode != null && useNode != null) {
      addRange(entries, Ranges.create(hashNode, useNode), SemanticTokenTypes.Namespace);
    } else if (hashNode != null) {
      addRange(entries, Ranges.create(hashNode), SemanticTokenTypes.Namespace);
    } else {
      // no-op
    }

    Optional.ofNullable(useCtx.usedLib())
      .map(BSLParser.UsedLibContext::PREPROC_IDENTIFIER)
      .ifPresent(id -> addRange(entries, Ranges.create(id), SemanticTokenTypes.Variable));
  }

  private void addRange(List<TokenEntry> entries, Range range, String type) {
    addRange(entries, range, type, NO_MODIFIERS);
  }

  private void addRange(List<TokenEntry> entries, Range range, String type, String... modifiers) {
    int explicitLength = Math.max(0, range.getEnd().getCharacter() - range.getStart().getCharacter());
    addRange(entries, range, explicitLength, type, modifiers);
  }

  // overload to add token with explicit precomputed length (used for multi-line tokens)
  private void addRange(List<TokenEntry> entries, Range range, int explicitLength, String type, String[] modifiers) {
    if (Ranges.isEmpty(range)) {
      return;
    }
    int typeIdx = legend.getTokenTypes().indexOf(type);
    if (typeIdx < 0) {
      return;
    }
    int line = range.getStart().getLine();
    int start = range.getStart().getCharacter();
    int length = Math.max(0, explicitLength);
    if (length > 0) {
      var modifierMask = 0;
      for (String mod : modifiers) {
        int idx = legend.getTokenModifiers().indexOf(mod);
        if (idx >= 0) {
          modifierMask |= (1 << idx);
        }
      }
      entries.add(new TokenEntry(line, start, length, typeIdx, modifierMask));
    }
  }

  private static List<Integer> toDeltaEncoded(List<TokenEntry> entries) {
    // de-dup and sort
    Set<TokenEntry> uniq = new HashSet<>(entries);
    List<TokenEntry> sorted = new ArrayList<>(uniq);
    sorted.sort(Comparator
      .comparingInt(TokenEntry::line)
      .thenComparingInt(TokenEntry::start));

    List<Integer> data = new ArrayList<>(sorted.size() * 5);
    var prevLine = 0;
    var prevChar = 0;
    var first = true;

    for (TokenEntry tokenEntry : sorted) {
      int deltaLine = first ? tokenEntry.line : (tokenEntry.line - prevLine);
      int prevCharOrZero = (deltaLine == 0) ? prevChar : 0;
      int deltaStart = first ? tokenEntry.start : (tokenEntry.start - prevCharOrZero);

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

  private void addMethodCallTokens(List<TokenEntry> entries, URI uri) {
    for (var reference : referenceIndex.getReferencesFrom(uri, SymbolKind.Method)) {
      if (!reference.isSourceDefinedSymbolReference()) {
        continue;
      }

      reference.getSourceDefinedSymbol()
        .ifPresent(symbol -> addRange(entries, reference.getSelectionRange(), SemanticTokenTypes.Method));
    }
  }

  private void addLexicalTokens(List<Token> tokens, List<TokenEntry> entries) {
    for (Token token : tokens) {
      var tokenType = token.getType();
      var tokenText = Objects.toString(token.getText(), "");
      if (tokenText.isEmpty()) {
        continue;
      }

      // strings
      if (STRING_TYPES.contains(tokenType)) {
        addRange(entries, Ranges.create(token), SemanticTokenTypes.String);
        continue;
      }

      // date literals in single quotes
      if (tokenType == BSLLexer.DATETIME) {
        addRange(entries, Ranges.create(token), SemanticTokenTypes.String);
        continue;
      }

      // numbers
      if (NUMBER_TYPES.contains(tokenType)) {
        addRange(entries, Ranges.create(token), SemanticTokenTypes.Number);
        continue;
      }

      // operators and punctuators
      if (OPERATOR_TYPES.contains(tokenType)) {
        addRange(entries, Ranges.create(token), SemanticTokenTypes.Operator);
        continue;
      }

      // Skip '&' and all ANNOTATION_* symbol tokens here to avoid duplicate Decorator emission (handled via AST)
      if (tokenType == BSLLexer.AMPERSAND || ANNOTATION_TOKENS.contains(tokenType)) {
        continue;
      }

      // specific literals as keywords: undefined/boolean/null
      if (tokenType == BSLLexer.UNDEFINED
        || tokenType == BSLLexer.TRUE
        || tokenType == BSLLexer.FALSE
        || tokenType == BSLLexer.NULL) {
        addRange(entries, Ranges.create(token), SemanticTokenTypes.Keyword);
        continue;
      }

      // keywords (by symbolic name suffix), skip PREPROC_* (handled via AST)
      String symbolicName = BSLLexer.VOCABULARY.getSymbolicName(tokenType);
      if (symbolicName != null && symbolicName.endsWith("_KEYWORD") && !symbolicName.startsWith("PREPROC_")) {
        addRange(entries, Ranges.create(token), SemanticTokenTypes.Keyword);
      }
    }
  }

  private record TokenEntry(int line, int start, int length, int type, int modifiers) {}
}
