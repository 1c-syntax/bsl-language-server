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
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.description.MethodDescription;
import com.github._1c_syntax.bsl.languageserver.context.symbol.description.SourceDefinedSymbolDescription;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableDescription;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParser.AnnotationContext;
import com.github._1c_syntax.bsl.parser.BSLParser.CompilerDirectiveContext;
import com.github._1c_syntax.bsl.parser.BSLParser.Preproc_nativeContext;
import com.github._1c_syntax.bsl.parser.BSLParser.PreprocessorContext;
import com.github._1c_syntax.bsl.parser.BSLParser.RegionEndContext;
import com.github._1c_syntax.bsl.parser.BSLParser.RegionStartContext;
import com.github._1c_syntax.bsl.parser.BSLParser.UseContext;
import com.github._1c_syntax.bsl.parser.SDBLLexer;
import com.github._1c_syntax.bsl.parser.SDBLParser;
import com.github._1c_syntax.bsl.parser.SDBLParserBaseVisitor;
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
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
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

  private static final Set<Integer> SPEC_LITERALS = Set.of(
    BSLLexer.UNDEFINED,
    BSLLexer.TRUE,
    BSLLexer.FALSE,
    BSLLexer.NULL
  );

  // SDBL (Query Language) token types
  private static final Set<Integer> SDBL_KEYWORDS = createSdblKeywords();
  private static final Set<Integer> SDBL_FUNCTIONS = createSdblFunctions();
  private static final Set<Integer> SDBL_METADATA_TYPES = createSdblMetadataTypes();
  private static final Set<Integer> SDBL_VIRTUAL_TABLES = createSdblVirtualTables();
  private static final Set<Integer> SDBL_LITERALS = createSdblLiterals();
  private static final Set<Integer> SDBL_OPERATORS = createSdblOperators();
  private static final Set<Integer> SDBL_STRINGS = Set.of(SDBLLexer.STR);
  private static final Set<Integer> SDBL_COMMENTS = Set.of(SDBLLexer.LINE_COMMENT);
  private static final Set<Integer> SDBL_PARAMETERS = Set.of(SDBLLexer.AMPERSAND, SDBLLexer.PARAMETER_IDENTIFIER);
  private static final Set<Integer> SDBL_EDS = Set.of(
    SDBLLexer.EDS_CUBE,
    SDBLLexer.EDS_TABLE,
    SDBLLexer.EDS_CUBE_DIMTABLE
  );
  private static final Set<Integer> SDBL_NUMBERS = Set.of(SDBLLexer.DECIMAL, SDBLLexer.FLOAT);

  private static final String[] NO_MODIFIERS = new String[0];
  private static final String[] DOC_ONLY = new String[]{SemanticTokenModifiers.Documentation};
  private static final String[] DEFAULT_LIBRARY = new String[]{SemanticTokenModifiers.DefaultLibrary};

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
   * @param params          Параметры запроса
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

    // 4) SDBL (Query Language) tokens - process before lexical tokens to identify strings to skip
    var stringsToSkip = collectStringsWithSdblTokens(documentContext);

    // 5) Lexical tokens on default channel: strings, numbers, macros, operators, keywords
    // Skip strings that contain SDBL tokens (they'll be split and added by addSdblTokens)
    addLexicalTokens(tokensFromDefaultChannel, entries, stringsToSkip);

    // 6) Add SDBL tokens and split string parts
    addSdblTokens(documentContext, entries, stringsToSkip);

    // 6) Build delta-encoded data
    List<Integer> data = toDeltaEncoded(entries);
    return new SemanticTokens(data);
  }

  private void addMultilineDescriptions(
    DocumentContext documentContext, List<Range> descriptionRanges, List<TokenEntry> entries) {
    if (!multilineTokenSupport) {
      return;
    }

    for (Range r : descriptionRanges) {
      // compute multi-line token length using document text
      int length = documentContext.getText(r).length();
      addRange(entries, r, length, SemanticTokenTypes.Comment, DOC_ONLY);
    }
  }

  private void addVariableSymbols(
    DocumentContext documentContext,
    SymbolTree symbolTree,
    List<TokenEntry> entries,
    List<Range> descriptionRanges,
    BitSet documentationLines
  ) {
    for (var variableSymbol : symbolTree.getVariables()) {
      if (variableSymbol.getKind() == VariableKind.PARAMETER) {
        continue;
      }
      
      var nameRange = variableSymbol.getVariableNameRange();
      if (!Ranges.isEmpty(nameRange)) {
        boolean isDefinition = referenceResolver.findReference(documentContext.getUri(), nameRange.getStart())
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
    
    var references = referenceIndex.getReferencesFrom(documentContext.getUri(), SymbolKind.Variable);
    references.stream()
      .filter(Reference::isSourceDefinedSymbolReference)
      .forEach(reference -> reference.getSourceDefinedSymbol()
        .filter(symbol -> symbol instanceof VariableSymbol)
        .map(symbol -> (VariableSymbol) symbol)
        .ifPresent(variableSymbol -> {
          var tokenType = variableSymbol.getKind() == VariableKind.PARAMETER
            ? SemanticTokenTypes.Parameter
            : SemanticTokenTypes.Variable;
          
          if (reference.getOccurrenceType() == OccurrenceType.DEFINITION) {
            addRange(entries, reference.getSelectionRange(), tokenType, SemanticTokenModifiers.Definition);
          } else {
            addRange(entries, reference.getSelectionRange(), tokenType);
          }
        }));
  }

  private void addMethodSymbols(SymbolTree symbolTree, List<TokenEntry> entries, List<Range> descriptionRanges, BitSet documentationLines) {
    for (var method : symbolTree.getMethods()) {
      var semanticTokenType = method.isFunction() ? SemanticTokenTypes.Function : SemanticTokenTypes.Method;
      addRange(entries, method.getSubNameRange(), semanticTokenType);
      for (ParameterDefinition parameter : method.getParameters()) {
        addRange(entries, parameter.getRange(), SemanticTokenTypes.Parameter, SemanticTokenModifiers.Definition);
      }
      method.getDescription().ifPresent((MethodDescription description) ->
        processVariableDescription(descriptionRanges, documentationLines, description)
      );
    }
  }

  private void processVariableDescription(
    List<Range> descriptionRanges,
    BitSet documentationLines,
    SourceDefinedSymbolDescription description
  ) {
    var range = description.getRange();
    if (Ranges.isEmpty(range)) {
      return;
    }

    descriptionRanges.add(range);
    if (!multilineTokenSupport) {
      markLines(documentationLines, range);
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
      addAmpersandRange(entries, compilerDirective.AMPERSAND(), compilerDirective.compilerDirectiveSymbol());
    }

    // annotations: single Decorator from '&' through annotation name; params identifiers as Parameter
    for (var annotation : Trees.<AnnotationContext>findAllRuleNodes(parseTree, BSLParser.RULE_annotation)) {
      addAmpersandRange(entries, annotation.AMPERSAND(), annotation.annotationName());

      var annotationParams = annotation.annotationParams();
      if (annotationParams == null) {
        continue;
      }

      for (var annotationParam : annotationParams.annotationParam()) {
        var annotationParamName = annotationParam.annotationParamName();
        if (annotationParamName != null) {
          addRange(entries, Ranges.create(annotationParamName.IDENTIFIER()), SemanticTokenTypes.Parameter);
        }
      }
    }
  }

  private void addPreprocessorFromAst(List<TokenEntry> entries, ParseTree parseTree) {
    addRegionsNamespaces(entries, parseTree);
    addDirectives(entries, parseTree);
    addOtherPreprocs(entries, parseTree);
  }

  // Regions as Namespace: handle all regionStart and regionEnd nodes explicitly
  private void addRegionsNamespaces(List<TokenEntry> entries, ParseTree parseTree) {
    for (var regionStart : Trees.<RegionStartContext>findAllRuleNodes(parseTree, BSLParser.RULE_regionStart)) {
      // Namespace only for '#'+keyword part to avoid overlap with region name token
      var preprocessor = Trees.<PreprocessorContext>getAncestorByRuleIndex(regionStart, BSLParser.RULE_preprocessor);
      if (preprocessor != null && regionStart.PREPROC_REGION() != null) {
        addRange(entries,
          Ranges.create(preprocessor.getStart(), regionStart.PREPROC_REGION().getSymbol()),
          SemanticTokenTypes.Namespace);
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
  }

  // Use directives as Namespace: #Использовать ...
  // Native directives as Macro: #native
  private void addDirectives(List<TokenEntry> entries, ParseTree parseTree) {
    for (var use : Trees.<UseContext>findAllRuleNodes(parseTree, BSLParser.RULE_use)) {
      addNamespaceForUse(entries, use);
    }

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
  }

  // Other preprocessor directives: Macro for each HASH and PREPROC_* token,
  // excluding region start/end, native, use (handled as Namespace)
  private void addOtherPreprocs(List<TokenEntry> entries, ParseTree parseTree) {
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
    var hashNode = useCtx.HASH();
    var useNode = useCtx.PREPROC_USE_KEYWORD();

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

  // общий для аннотаций и директив компиляции способ добавления
  private void addAmpersandRange(List<TokenEntry> entries, TerminalNode node, @Nullable ParserRuleContext name) {
    var ampersand = node.getSymbol(); // '&'
    if (name != null) {
      var symbolToken = name.getStart();
      addRange(entries, Ranges.create(ampersand, symbolToken), SemanticTokenTypes.Decorator);
    } else {
      addRange(entries, Ranges.create(ampersand), SemanticTokenTypes.Decorator);
    }
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

  private void addLexicalTokens(List<Token> tokens, List<TokenEntry> entries, Set<Token> stringsToSkip) {
    for (Token token : tokens) {
      var tokenType = token.getType();
      var tokenText = Objects.toString(token.getText(), "");
      if (!tokenText.isEmpty()) {
        // Skip string tokens that contain SDBL tokens - they'll be handled by addSdblTokens
        if (STRING_TYPES.contains(tokenType) && stringsToSkip.contains(token)) {
          continue;
        }
        selectAndAddSemanticToken(entries, token, tokenType);
      }
    }
  }

  private void selectAndAddSemanticToken(List<TokenEntry> entries, Token token, int tokenType) {
    if (STRING_TYPES.contains(tokenType)) { // strings
      addRange(entries, Ranges.create(token), SemanticTokenTypes.String);
    } else if (tokenType == BSLLexer.DATETIME) { // date literals in single quotes
      addRange(entries, Ranges.create(token), SemanticTokenTypes.String);
    } else if (NUMBER_TYPES.contains(tokenType)) { // numbers
      addRange(entries, Ranges.create(token), SemanticTokenTypes.Number);
    } else if (OPERATOR_TYPES.contains(tokenType)) { // operators and punctuators
      addRange(entries, Ranges.create(token), SemanticTokenTypes.Operator);
    } else if (tokenType == BSLLexer.AMPERSAND || ANNOTATION_TOKENS.contains(tokenType)) {
      // Skip '&' and all ANNOTATION_* symbol tokens here to avoid duplicate Decorator emission (handled via AST)
    } else if (SPEC_LITERALS.contains(tokenType)) { // specific literals as keywords: undefined/boolean/null
      addRange(entries, Ranges.create(token), SemanticTokenTypes.Keyword);
    } else {      // keywords (by symbolic name suffix), skip PREPROC_* (handled via AST)
      String symbolicName = BSLLexer.VOCABULARY.getSymbolicName(tokenType);
      if (symbolicName != null && symbolicName.endsWith("_KEYWORD") && !symbolicName.startsWith("PREPROC_")) {
        addRange(entries, Ranges.create(token), SemanticTokenTypes.Keyword);
      }
    }
  }

  private Set<Token> collectStringsWithSdblTokens(DocumentContext documentContext) {
    var queries = documentContext.getQueries();
    if (queries.isEmpty()) {
      return Set.of();
    }

    // Collect all SDBL tokens grouped by line
    // Note: ANTLR tokens use 1-indexed line numbers, convert to 0-indexed for LSP Range
    var sdblTokensByLine = new HashMap<Integer, List<Token>>();
    for (var query : queries) {
      for (Token token : query.getTokens()) {
        if (token.getChannel() != Token.DEFAULT_CHANNEL) {
          continue;
        }
        int zeroIndexedLine = token.getLine() - 1;  // ANTLR uses 1-indexed, convert to 0-indexed for Range
        sdblTokensByLine.computeIfAbsent(zeroIndexedLine, k -> new ArrayList<>()).add(token);
      }
    }

    if (sdblTokensByLine.isEmpty()) {
      return Set.of();
    }

    // Collect BSL string tokens that contain SDBL tokens
    var bslStringTokens = documentContext.getTokensFromDefaultChannel().stream()
      .filter(token -> STRING_TYPES.contains(token.getType()))
      .toList();

    var stringsToSkip = new HashSet<Token>();

    for (Token bslString : bslStringTokens) {
      var stringRange = Ranges.create(bslString);
      int stringLine = stringRange.getStart().getLine();

      var sdblTokensOnLine = sdblTokensByLine.get(stringLine);
      if (sdblTokensOnLine == null || sdblTokensOnLine.isEmpty()) {
        continue;
      }

      // Check if any SDBL tokens overlap with this string token
      var hasOverlappingTokens = sdblTokensOnLine.stream()
        .anyMatch(sdblToken -> {
          var sdblRange = Ranges.create(sdblToken);
          return Ranges.containsRange(stringRange, sdblRange);
        });

      if (hasOverlappingTokens) {
        stringsToSkip.add(bslString);
      }
    }

    return stringsToSkip;
  }

  private void addSdblTokens(DocumentContext documentContext, List<TokenEntry> entries, Set<Token> stringsToSkip) {
    var queries = documentContext.getQueries();
    if (queries.isEmpty()) {
      return;
    }

    // Collect all SDBL tokens grouped by line
    // Note: ANTLR tokens use 1-indexed line numbers, convert to 0-indexed for LSP Range
    var sdblTokensByLine = new HashMap<Integer, List<Token>>();
    for (var query : queries) {
      for (Token token : query.getTokens()) {
        if (token.getChannel() != Token.DEFAULT_CHANNEL) {
          continue;
        }
        int zeroIndexedLine = token.getLine() - 1;  // ANTLR uses 1-indexed, convert to 0-indexed for Range
        sdblTokensByLine.computeIfAbsent(zeroIndexedLine, k -> new ArrayList<>()).add(token);
      }
    }

    if (sdblTokensByLine.isEmpty()) {
      return;
    }

    // For each BSL string token that was skipped, split it around SDBL tokens
    int stringTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.String);
    
    for (Token stringToken : stringsToSkip) {
      var stringRange = Ranges.create(stringToken);
      int stringLine = stringRange.getStart().getLine();

      var sdblTokensOnLine = sdblTokensByLine.get(stringLine);
      if (sdblTokensOnLine == null || sdblTokensOnLine.isEmpty()) {
        continue;
      }

      // Check if any SDBL tokens overlap with this string token
      int stringStart = stringRange.getStart().getCharacter();
      int stringEnd = stringRange.getEnd().getCharacter();
      
      var overlappingTokens = sdblTokensOnLine.stream()
        .filter(sdblToken -> {
          int sdblStart = sdblToken.getCharPositionInLine();
          int sdblEnd = sdblStart + (int) sdblToken.getText().codePoints().count();
          // Token overlaps if it's within the string range
          return sdblStart >= stringStart && sdblEnd <= stringEnd;
        })
        .sorted(Comparator.comparingInt(Token::getCharPositionInLine))
        .toList();

      if (overlappingTokens.isEmpty()) {
        continue;
      }

      // Split the STRING token around SDBL tokens
      int currentPos = stringStart;

      for (Token sdblToken : overlappingTokens) {
        int sdblStart = sdblToken.getCharPositionInLine();
        int sdblEnd = sdblStart + (int) sdblToken.getText().codePoints().count();

        // Add string part before SDBL token
        if (currentPos < sdblStart && stringTypeIdx >= 0) {
          entries.add(new TokenEntry(
            stringLine,
            currentPos,
            sdblStart - currentPos,
            stringTypeIdx,
            0
          ));
        }

        currentPos = sdblEnd;
      }

      // Add final string part after last SDBL token
      if (currentPos < stringEnd && stringTypeIdx >= 0) {
        entries.add(new TokenEntry(
          stringLine,
          currentPos,
          stringEnd - currentPos,
          stringTypeIdx,
          0
        ));
      }
    }

    // Add all SDBL tokens (with adjusted line numbers)
    for (var query : queries) {
      for (Token token : query.getTokens()) {
        if (token.getChannel() != Token.DEFAULT_CHANNEL) {
          continue;
        }
        addSdblToken(entries, token);
      }
    }
    
    // Add AST-based semantic tokens (aliases, field names, metadata names, etc.)
    for (var query : queries) {
      var visitor = new SdblSemanticTokensVisitor(entries, legend);
      visitor.visit(query.getAst());
    }
  }

  private void addSdblToken(List<TokenEntry> entries, Token token) {
    var tokenType = token.getType();
    var semanticTypeAndModifiers = getSdblTokenTypeAndModifiers(tokenType);
    if (semanticTypeAndModifiers != null) {
      // ANTLR uses 1-indexed line numbers, convert to 0-indexed for LSP Range
      int zeroIndexedLine = token.getLine() - 1;
      // Create range with corrected line number
      var range = new Range(
        new Position(zeroIndexedLine, token.getCharPositionInLine()),
        new Position(zeroIndexedLine, token.getCharPositionInLine() + (int) token.getText().codePoints().count())
      );
      addRange(entries, range, semanticTypeAndModifiers.type, semanticTypeAndModifiers.modifiers);
    }
  }

  @Nullable
  private SdblTokenTypeAndModifiers getSdblTokenTypeAndModifiers(int tokenType) {
    if (SDBL_KEYWORDS.contains(tokenType)) {
      return new SdblTokenTypeAndModifiers(SemanticTokenTypes.Keyword, NO_MODIFIERS);
    } else if (SDBL_FUNCTIONS.contains(tokenType)) {
      // Functions as Function type with defaultLibrary modifier (built-in SDBL functions)
      return new SdblTokenTypeAndModifiers(SemanticTokenTypes.Function, DEFAULT_LIBRARY);
    } else if (SDBL_METADATA_TYPES.contains(tokenType) || SDBL_VIRTUAL_TABLES.contains(tokenType) || SDBL_EDS.contains(tokenType)) {
      // Metadata types (Справочник, РегистрСведений, etc.) as Namespace with no modifiers (per JSON spec)
      return new SdblTokenTypeAndModifiers(SemanticTokenTypes.Namespace, NO_MODIFIERS);
    } else if (SDBL_LITERALS.contains(tokenType)) {
      // Literals as Keyword (matching YAML: constant.language.sdbl, no Constant type in LSP)
      return new SdblTokenTypeAndModifiers(SemanticTokenTypes.Keyword, NO_MODIFIERS);
    } else if (SDBL_OPERATORS.contains(tokenType)) {
      return new SdblTokenTypeAndModifiers(SemanticTokenTypes.Operator, NO_MODIFIERS);
    } else if (SDBL_STRINGS.contains(tokenType)) {
      return new SdblTokenTypeAndModifiers(SemanticTokenTypes.String, NO_MODIFIERS);
    } else if (SDBL_COMMENTS.contains(tokenType)) {
      return new SdblTokenTypeAndModifiers(SemanticTokenTypes.Comment, NO_MODIFIERS);
    } else if (SDBL_PARAMETERS.contains(tokenType)) {
      // Parameters as Parameter (matching YAML: variable.parameter.sdbl)
      return new SdblTokenTypeAndModifiers(SemanticTokenTypes.Parameter, NO_MODIFIERS);
    } else if (SDBL_NUMBERS.contains(tokenType)) {
      // Numbers as Number (matching YAML: constant.numeric.sdbl)
      return new SdblTokenTypeAndModifiers(SemanticTokenTypes.Number, NO_MODIFIERS);
    }
    return null;
  }

  private record SdblTokenTypeAndModifiers(String type, String[] modifiers) {
  }

  // SDBL token type factory methods
  private static Set<Integer> createSdblKeywords() {
    return Set.of(
      SDBLLexer.ALL,
      SDBLLexer.ALLOWED,
      SDBLLexer.AND,
      SDBLLexer.AS,
      SDBLLexer.ASC,
      SDBLLexer.AUTOORDER,
      SDBLLexer.BETWEEN,
      SDBLLexer.BY_EN,
      SDBLLexer.CASE,
      SDBLLexer.CAST,
      SDBLLexer.DESC,
      SDBLLexer.DISTINCT,
      SDBLLexer.DROP,
      SDBLLexer.ELSE,
      SDBLLexer.END,
      SDBLLexer.ESCAPE,
      SDBLLexer.FOR,
      SDBLLexer.FROM,
      SDBLLexer.FULL,
      SDBLLexer.GROUP,
      SDBLLexer.HAVING,
      SDBLLexer.HIERARCHY,
      SDBLLexer.HIERARCHY_FOR_IN,
      SDBLLexer.IN,
      SDBLLexer.INDEX,
      SDBLLexer.INNER,
      SDBLLexer.INTO,
      SDBLLexer.IS,
      SDBLLexer.ISNULL,
      SDBLLexer.JOIN,
      SDBLLexer.LEFT,
      SDBLLexer.LIKE,
      SDBLLexer.NOT,
      SDBLLexer.OF,
      SDBLLexer.ONLY,
      SDBLLexer.ON_EN,
      SDBLLexer.OR,
      SDBLLexer.ORDER,
      SDBLLexer.OVERALL,
      SDBLLexer.OUTER,
      SDBLLexer.PERIODS,
      SDBLLexer.PO_RU,
      SDBLLexer.REFS,
      SDBLLexer.RIGHT,
      SDBLLexer.SELECT,
      SDBLLexer.SET,
      SDBLLexer.THEN,
      SDBLLexer.TOP,
      SDBLLexer.TOTALS,
      SDBLLexer.UNION,
      SDBLLexer.UPDATE,
      SDBLLexer.WHEN,
      SDBLLexer.WHERE,
      SDBLLexer.EMPTYREF,
      SDBLLexer.GROUPEDBY,
      SDBLLexer.GROUPING
    );
  }

  private static Set<Integer> createSdblFunctions() {
    return Set.of(
      SDBLLexer.AVG,
      SDBLLexer.BEGINOFPERIOD,
      SDBLLexer.BOOLEAN,
      SDBLLexer.COUNT,
      SDBLLexer.DATE,
      SDBLLexer.DATEADD,
      SDBLLexer.DATEDIFF,
      SDBLLexer.DATETIME,
      SDBLLexer.DAY,
      SDBLLexer.DAYOFYEAR,
      SDBLLexer.EMPTYTABLE,
      SDBLLexer.ENDOFPERIOD,
      SDBLLexer.HALFYEAR,
      SDBLLexer.HOUR,
      SDBLLexer.MAX,
      SDBLLexer.MIN,
      SDBLLexer.MINUTE,
      SDBLLexer.MONTH,
      SDBLLexer.NUMBER,
      SDBLLexer.QUARTER,
      SDBLLexer.PRESENTATION,
      SDBLLexer.RECORDAUTONUMBER,
      SDBLLexer.REFPRESENTATION,
      SDBLLexer.SECOND,
      SDBLLexer.STRING,
      SDBLLexer.SUBSTRING,
      SDBLLexer.SUM,
      SDBLLexer.TENDAYS,
      SDBLLexer.TYPE,
      SDBLLexer.VALUE,
      SDBLLexer.VALUETYPE,
      SDBLLexer.WEEK,
      SDBLLexer.WEEKDAY,
      SDBLLexer.YEAR,
      SDBLLexer.INT,
      SDBLLexer.ACOS,
      SDBLLexer.ASIN,
      SDBLLexer.ATAN,
      SDBLLexer.COS,
      SDBLLexer.SIN,
      SDBLLexer.TAN,
      SDBLLexer.LOG,
      SDBLLexer.LOG10,
      SDBLLexer.EXP,
      SDBLLexer.POW,
      SDBLLexer.SQRT,
      SDBLLexer.LOWER,
      SDBLLexer.STRINGLENGTH,
      SDBLLexer.TRIMALL,
      SDBLLexer.TRIML,
      SDBLLexer.TRIMR,
      SDBLLexer.UPPER,
      SDBLLexer.ROUND,
      SDBLLexer.STOREDDATASIZE,
      SDBLLexer.UUID,
      SDBLLexer.STRFIND,
      SDBLLexer.STRREPLACE
    );
  }

  private static Set<Integer> createSdblMetadataTypes() {
    return Set.of(
      SDBLLexer.ACCOUNTING_REGISTER_TYPE,
      SDBLLexer.ACCUMULATION_REGISTER_TYPE,
      SDBLLexer.BUSINESS_PROCESS_TYPE,
      SDBLLexer.CALCULATION_REGISTER_TYPE,
      SDBLLexer.CATALOG_TYPE,
      SDBLLexer.CHART_OF_ACCOUNTS_TYPE,
      SDBLLexer.CHART_OF_CALCULATION_TYPES_TYPE,
      SDBLLexer.CHART_OF_CHARACTERISTIC_TYPES_TYPE,
      SDBLLexer.CONSTANT_TYPE,
      SDBLLexer.DOCUMENT_TYPE,
      SDBLLexer.DOCUMENT_JOURNAL_TYPE,
      SDBLLexer.ENUM_TYPE,
      SDBLLexer.EXCHANGE_PLAN_TYPE,
      SDBLLexer.EXTERNAL_DATA_SOURCE_TYPE,
      SDBLLexer.FILTER_CRITERION_TYPE,
      SDBLLexer.INFORMATION_REGISTER_TYPE,
      SDBLLexer.SEQUENCE_TYPE,
      SDBLLexer.TASK_TYPE
    );
  }

  private static Set<Integer> createSdblVirtualTables() {
    return Set.of(
      SDBLLexer.ACTUAL_ACTION_PERIOD_VT,
      SDBLLexer.BALANCE_VT,
      SDBLLexer.BALANCE_AND_TURNOVERS_VT,
      SDBLLexer.BOUNDARIES_VT,
      SDBLLexer.DR_CR_TURNOVERS_VT,
      SDBLLexer.EXT_DIMENSIONS_VT,
      SDBLLexer.RECORDS_WITH_EXT_DIMENSIONS_VT,
      SDBLLexer.SCHEDULE_DATA_VT,
      SDBLLexer.SLICEFIRST_VT,
      SDBLLexer.SLICELAST_VT,
      SDBLLexer.TASK_BY_PERFORMER_VT,
      SDBLLexer.TURNOVERS_VT
    );
  }

  private static Set<Integer> createSdblLiterals() {
    return Set.of(
      SDBLLexer.TRUE,
      SDBLLexer.FALSE,
      SDBLLexer.UNDEFINED,
      SDBLLexer.NULL
    );
  }

  private static Set<Integer> createSdblOperators() {
    return Set.of(
      SDBLLexer.SEMICOLON,
      SDBLLexer.DOT,  // Added for field access operator
      SDBLLexer.PLUS,
      SDBLLexer.MINUS,
      SDBLLexer.MUL,
      SDBLLexer.QUOTIENT,
      SDBLLexer.ASSIGN,
      SDBLLexer.LESS_OR_EQUAL,
      SDBLLexer.LESS,
      SDBLLexer.NOT_EQUAL,
      SDBLLexer.GREATER_OR_EQUAL,
      SDBLLexer.GREATER,
      SDBLLexer.COMMA,
      SDBLLexer.BRACE,
      SDBLLexer.BRACE_START,
      SDBLLexer.NUMBER_SIGH
    );
  }

  private record TokenEntry(int line, int start, int length, int type, int modifiers) {
  }

  /**
   * Visitor for SDBL AST to add semantic tokens based on context.
   * Handles:
   * - Table aliases → Variable
   * - Field names (after dots) → Property
   * - Metadata type names → Namespace  
   * - Alias declarations (after AS/КАК) → Variable + Declaration
   * - Operators (dots, commas) → Operator
   */
  private static class SdblSemanticTokensVisitor extends SDBLParserBaseVisitor<Void> {
    private final List<TokenEntry> entries;
    private final SemanticTokensLegend legend;
    private final int variableIdx;
    private final int propertyIdx;
    private final int namespaceIdx;
    private final int declarationModifierBit;
    
    public SdblSemanticTokensVisitor(List<TokenEntry> entries, SemanticTokensLegend legend) {
      this.entries = entries;
      this.legend = legend;
      this.variableIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Variable);
      this.propertyIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Property);
      this.namespaceIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Namespace);
      this.declarationModifierBit = 1 << legend.getTokenModifiers().indexOf(SemanticTokenModifiers.Declaration);
    }
    
    @Override
    public Void visitDataSource(SDBLParser.DataSourceContext ctx) {
      // Handle table sources and their aliases
      var alias = ctx.alias();
      if (alias != null && alias.identifier() != null) {
        // Alias after AS/КАК → Variable + Declaration
        var token = alias.identifier().getStart();
        addToken(token, variableIdx, declarationModifierBit);
      }
      
      return super.visitDataSource(ctx);
    }
    
    @Override
    public Void visitSelectedField(SDBLParser.SelectedFieldContext ctx) {
      // Handle field selections and their aliases
      var alias = ctx.alias();
      if (alias != null && alias.identifier() != null) {
        // Alias after AS/КАК → Variable + Declaration
        var token = alias.identifier().getStart();
        addToken(token, variableIdx, declarationModifierBit);
      }
      
      return super.visitSelectedField(ctx);
    }
    
    @Override
    public Void visitMdo(SDBLParser.MdoContext ctx) {
      // Metadata type names (Справочник, РегистрСведений, etc.) are already handled
      // by lexical token processing as Namespace with defaultLibrary modifier
      
      // Handle MDO structure: MetadataType.ObjectName.VirtualTableMethod
      // Example: РегистрСведений.КурсыВалют.СрезПоследних
      var identifiers = Trees.getDescendants(ctx).stream()
        .filter(SDBLParser.IdentifierContext.class::isInstance)
        .map(SDBLParser.IdentifierContext.class::cast)
        .toList();
      
      if (identifiers.size() == 1) {
        // Single identifier → Class (metadata object name)
        int classIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Class);
        addToken(identifiers.get(0).getStart(), classIdx, 0);
      } else if (identifiers.size() == 2) {
        // Two identifiers → first is Class (object name), second is Method (virtual table method)
        int classIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Class);
        int methodIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Method);
        addToken(identifiers.get(0).getStart(), classIdx, 0);
        addToken(identifiers.get(1).getStart(), methodIdx, 0);
      } else if (identifiers.size() > 2) {
        // More than two → last one could be a method, second-to-last is the object name
        int classIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Class);
        int methodIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Method);
        // Second-to-last → Class (object name)
        addToken(identifiers.get(identifiers.size() - 2).getStart(), classIdx, 0);
        // Last → Method (virtual table method like СрезПоследних)
        addToken(identifiers.get(identifiers.size() - 1).getStart(), methodIdx, 0);
      }
      
      return super.visitMdo(ctx);
    }
    
    @Override
    public Void visitColumn(SDBLParser.ColumnContext ctx) {
      // Handle field references: TableAlias.FieldName
      var identifiers = ctx.identifier();
      if (identifiers != null && !identifiers.isEmpty()) {
        if (identifiers.size() == 1) {
          // Single identifier - could be alias or field
          // Context-dependent, treat as variable for now
          addToken(identifiers.get(0).getStart(), variableIdx, 0);
        } else if (identifiers.size() >= 2) {
          // First identifier → Variable (table alias)
          addToken(identifiers.get(0).getStart(), variableIdx, 0);
          
          // Dots are handled by lexical token processing
          
          // Last identifier → Property (field name)
          addToken(identifiers.get(identifiers.size() - 1).getStart(), propertyIdx, 0);
        }
      }
      
      return super.visitColumn(ctx);
    }
    
    private void addToken(Token token, int typeIdx, int modifiers) {
      if (token == null || typeIdx < 0) {
        return;
      }
      
      // ANTLR uses 1-indexed line numbers, convert to 0-indexed for LSP Range
      int zeroIndexedLine = token.getLine() - 1;
      int start = token.getCharPositionInLine();
      int length = (int) token.getText().codePoints().count();
      
      entries.add(new TokenEntry(zeroIndexedLine, start, length, typeIdx, modifiers));
    }
  }
}
