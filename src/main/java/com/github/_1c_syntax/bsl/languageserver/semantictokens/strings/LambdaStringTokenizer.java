/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
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
package com.github._1c_syntax.bsl.languageserver.semantictokens.strings;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.semantictokens.CustomSemanticTokenTypes;
import com.github._1c_syntax.bsl.languageserver.semantictokens.SemanticTokenEntry;
import com.github._1c_syntax.bsl.languageserver.semantictokens.SemanticTokensSupplier;
import com.github._1c_syntax.bsl.languageserver.utils.MultilingualStringAnalyser;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLTokenizer;
import com.github._1c_syntax.utils.Absolute;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Собирает семантические подтокены для лямбда-выражений внутри строковых литералов.
 * <p>
 * Экземпляр создаётся на каждый вызов и не является Spring-компонентом.
 * Работает только для файлов OneScript (.os).
 */
@Slf4j
public class LambdaStringTokenizer {

  private static final Set<Integer> STRING_TYPES = Set.of(
    BSLLexer.STRING, BSLLexer.STRINGPART, BSLLexer.STRINGSTART, BSLLexer.STRINGTAIL
  );

  private static final Pattern LAMBDA_ARROW_PATTERN = Pattern.compile("->\\s*");

  private static final String RETURN_KEYWORD = "Возврат";

  private static final Pattern QUOTE_PAIR_PATTERN =
    Pattern.compile("(?:\"{12}|\"{10}|\"{8}|\"{6}|\"{4}|\"{2})");

  private static final Set<Integer> LAMBDA_PARAM_OPERATOR_TYPES =
    Set.of(BSLLexer.LPAREN, BSLLexer.RPAREN, BSLLexer.COMMA, BSLLexer.ASSIGN);

  private static final Set<Integer> ANNOTATION_SYMBOL_TYPES = Set.of(
    BSLLexer.ANNOTATION_CUSTOM_SYMBOL, BSLLexer.ANNOTATION_BEFORE_SYMBOL,
    BSLLexer.ANNOTATION_AFTER_SYMBOL, BSLLexer.ANNOTATION_AROUND_SYMBOL,
    BSLLexer.ANNOTATION_CHANGEANDVALIDATE_SYMBOL, BSLLexer.ANNOTATION_ATSERVER_SYMBOL,
    BSLLexer.ANNOTATION_ATCLIENT_SYMBOL, BSLLexer.ANNOTATION_ATSERVERNOCONTEXT_SYMBOL,
    BSLLexer.ANNOTATION_ATCLIENTATSERVER_SYMBOL, BSLLexer.ANNOTATION_ATCLIENTATSERVERNOCONTEXT_SYMBOL
  );

  private static final int ESCAPE_QUOTE_LENGTH = 2;
  private static final int ARROW_LENGTH = 2;

  private static final Map<Integer, String> PARAM_TOKEN_TYPE_MAP = createParamTokenTypeMap();

  private final ServerContext serverContext;
  private final SemanticTokensLegend legend;
  private final List<SemanticTokensSupplier> allSuppliers;

  public LambdaStringTokenizer(
    ServerContext serverContext,
    SemanticTokensLegend legend,
    List<SemanticTokensSupplier> allSuppliers
  ) {
    this.serverContext = serverContext;
    this.legend = legend;
    this.allSuppliers = List.copyOf(allSuppliers);
  }

  private static Map<Integer, String> createParamTokenTypeMap() {
    var map = new HashMap<Integer, String>();
    map.put(BSLLexer.IDENTIFIER, SemanticTokenTypes.Parameter);
    map.put(BSLLexer.AMPERSAND, SemanticTokenTypes.Decorator);
    map.put(BSLLexer.STRING, SemanticTokenTypes.String);
    LAMBDA_PARAM_OPERATOR_TYPES.forEach(t -> map.put(t, SemanticTokenTypes.Operator));
    ANNOTATION_SYMBOL_TYPES.forEach(t -> map.put(t, SemanticTokenTypes.Decorator));
    List.of(BSLLexer.FLOAT, BSLLexer.DECIMAL, BSLLexer.DATETIME)
      .forEach(t -> map.put(t, SemanticTokenTypes.Number));
    List.of(BSLLexer.TRUE, BSLLexer.FALSE, BSLLexer.UNDEFINED, BSLLexer.NULL)
      .forEach(t -> map.put(t, SemanticTokenTypes.Keyword));
    List.of(BSLLexer.PLUS, BSLLexer.MINUS)
      .forEach(t -> map.put(t, SemanticTokenTypes.Operator));
    return Map.copyOf(map);
  }

  /**
   * Собирает контексты лямбда-строк: для каждого строкового токена, входящего
   * в строковый литерал с оператором {@code ->}, вычисляет список подтокенов
   * (ключевые слова, операторы, числа) с их позициями в документе.
   * <p>
   * Работает только для файлов OneScript (.os).
   */
  public Map<Token, List<SubToken>> collect(
    DocumentContext documentContext, Map<Token, StringContext> specialStringContexts
  ) {
    if (documentContext.getFileType() != FileType.OS) {
      return Map.of();
    }

    var stringTokens = documentContext.getTokensFromDefaultChannel().stream()
      .filter(token -> STRING_TYPES.contains(token.getType()))
      .toList();

    var groups = groupStringTokens(stringTokens);
    Map<Token, List<SubToken>> result = new HashMap<>();

    for (var group : groups) {
      collectLambdaSubTokensForGroup(group, result, specialStringContexts);
    }

    return result;
  }

  /**
   * Группирует строковые токены в последовательности, составляющие один строковый литерал.
   * STRING — однострочный литерал (сам себе группа).
   * STRINGSTART, STRINGPART*, STRINGTAIL — многострочный литерал (одна группа).
   */
  private static List<List<Token>> groupStringTokens(List<Token> stringTokens) {
    List<List<Token>> groups = new ArrayList<>();
    List<Token> currentGroup = null;

    for (Token token : stringTokens) {
      switch (token.getType()) {
        case BSLLexer.STRING -> groups.add(List.of(token));
        case BSLLexer.STRINGSTART -> {
          currentGroup = new ArrayList<>();
          currentGroup.add(token);
        }
        case BSLLexer.STRINGPART, BSLLexer.STRINGTAIL ->
          currentGroup = addToMultilineGroup(token, currentGroup, groups);
        default -> { /* skip unexpected token types */ }
      }
    }

    return groups;
  }

  @Nullable
  private static List<Token> addToMultilineGroup(
    Token token, @Nullable List<Token> currentGroup, List<List<Token>> groups
  ) {
    if (currentGroup == null) {
      return null;
    }
    currentGroup.add(token);
    if (token.getType() == BSLLexer.STRINGTAIL) {
      groups.add(currentGroup);
      return null;
    }
    return currentGroup;
  }

  /**
   * Для группы токенов одного строкового литерала проверяет наличие {@code ->},
   * токенизирует тело лямбды через BSLTokenizer и маппит позиции обратно в документ.
   */
  private void collectLambdaSubTokensForGroup(
    List<Token> group, Map<Token, List<SubToken>> result,
    Map<Token, StringContext> specialStringContexts
  ) {
    var fullContent = extractLambdaFullContent(group);

    var arrowMatcher = LAMBDA_ARROW_PATTERN.matcher(fullContent);
    if (!arrowMatcher.find()) {
      return;
    }

    var segments = buildContentSegments(group);
    var arrowStart = arrowMatcher.start();
    var arrowEnd = arrowMatcher.end();

    // Extract parameter names for function wrapper
    var paramNames = extractLambdaParamNames(fullContent.substring(0, arrowStart));

    // Escaped double quotes "" — both before and after ->
    collectEscapedQuoteTokens(fullContent, segments, result);

    // StrTemplate/NStr placeholders in params area (left of ->)
    // Must be before collectLambdaParamTokens so NStr language keys take priority
    var groupContext = group.stream()
      .map(specialStringContexts::get)
      .filter(Objects::nonNull)
      .findFirst()
      .orElse(null);
    if (groupContext != null) {
      collectSpecialContextSubTokens(fullContent.substring(0, arrowStart), 0, segments, result, groupContext);
    }

    // Parameters (before ->)
    collectLambdaParamTokens(fullContent.substring(0, arrowStart), 0, segments, result);

    // Arrow (->) itself
    addSubTokenAtOffset(arrowStart, ARROW_LENGTH, SemanticTokenTypes.Operator, segments, result);

    // StrTemplate/NStr placeholders in body area (right of ->)
    if (groupContext != null) {
      collectSpecialContextSubTokens(fullContent.substring(arrowEnd), arrowEnd, segments, result, groupContext);
    }

    // Body (after ->) — delegate to all semantic token suppliers via virtual DocumentContext
    var bodyEntries = collectLambdaBodyTokensViaSuppliers(group, arrowEnd, paramNames);
    mapBodyTokensToResult(bodyEntries, segments, result);

    for (Token groupToken : group) {
      var list = result.get(groupToken);
      if (list != null) {
        list.sort(Comparator.comparingInt(SubToken::start));
        removeOverlappingTokens(list);
      }
    }
  }

  private void mapBodyTokensToResult(
    List<SemanticTokenEntry> bodyEntries, List<ContentSegment> segments, Map<Token, List<SubToken>> result
  ) {
    for (var entry : bodyEntries) {
      for (var segment : segments) {
        if (segment.docLine() == entry.line()
          && entry.start() >= segment.docCharStart()
          && entry.start() < segment.docCharStart() + segment.length()) {
          var typeName = legend.getTokenTypes().get(entry.type());
          result.computeIfAbsent(segment.token(), k -> new ArrayList<>())
            .add(new SubToken(entry.start(), entry.length(), typeName));
          break;
        }
      }
    }
  }

  /**
   * Удаляет перекрывающиеся токены. Escape-кавычки ({@code ""}) имеют абсолютный
   * приоритет: любой другой токен, пересекающийся с escape-кавычкой, удаляется.
   * Затем выполняется стандартный проход слева направо.
   */
  private static void removeOverlappingTokens(List<SubToken> tokens) {
    var escapeIntervals = tokens.stream()
      .filter(t -> CustomSemanticTokenTypes.STRING_ESCAPE.equals(t.type()))
      .map(t -> new int[]{t.start(), t.start() + t.length()})
      .toList();

    if (!escapeIntervals.isEmpty()) {
      tokens.removeIf(t -> !CustomSemanticTokenTypes.STRING_ESCAPE.equals(t.type())
        && escapeIntervals.stream().anyMatch(e ->
          t.start() < e[1] && (t.start() + t.length()) > e[0]));
    }

    var it = tokens.iterator();
    int lastEnd = -1;
    while (it.hasNext()) {
      var token = it.next();
      if (token.start() < lastEnd) {
        it.remove();
      } else {
        lastEnd = token.start() + token.length();
      }
    }
  }

  private List<SemanticTokenEntry> collectLambdaBodyTokensViaSuppliers(
    List<Token> group, int arrowEnd, List<String> paramNames
  ) {
    String paddedContent = buildPaddedLambdaBody(group, arrowEnd, paramNames);
    if (paddedContent.isBlank()) {
      return List.of();
    }

    var virtualUri = Absolute.uri(
      URI.create("file:///virtual-lambda-" + UUID.randomUUID() + ".os")
    );

    // Determine which lines belong to the real body (exclude fake header/footer)
    int firstBodyLine = getFirstBodyLine(group, arrowEnd);
    int lastBodyLine = getLastBodyLine(group, arrowEnd);

    try {
      var virtualDoc = serverContext.addDocument(virtualUri);
      serverContext.rebuildDocument(virtualDoc, paddedContent, 1);

      return allSuppliers.stream()
        .map(s -> s.getSemanticTokens(virtualDoc))
        .flatMap(Collection::stream)
        .filter(entry -> entry.line() >= firstBodyLine && entry.line() <= lastBodyLine)
        .toList();
    } finally {
      try {
        serverContext.removeDocument(virtualUri);
      } catch (IllegalStateException e) {
        LOGGER.warn("Could not remove virtual lambda document {}: {}", virtualUri, e.getMessage());
      }
    }
  }

  private static int getFirstBodyLine(List<Token> group, int arrowEnd) {
    return buildContentSegments(group).stream()
      .filter(s -> s.contentOffset() + s.length() > arrowEnd)
      .findFirst()
      .map(ContentSegment::docLine)
      .orElse(0);
  }

  private static int getLastBodyLine(List<Token> group, int arrowEnd) {
    return buildContentSegments(group).stream()
      .filter(s -> s.contentOffset() + s.length() > arrowEnd)
      .mapToInt(ContentSegment::docLine)
      .max()
      .orElse(0);
  }

  private static String buildPaddedLambdaBody(List<Token> group, int arrowEnd, List<String> paramNames) {
    var segments = buildContentSegments(group);
    var fullContent = extractLambdaFullContent(group);
    var lineContents = fillBodyLineContents(segments, fullContent, arrowEnd);

    if (lineContents.isEmpty()) {
      return "";
    }

    var firstBodyLine = lineContents.firstKey();
    var maxLine = lineContents.lastKey();
    var canWrapInFunction = firstBodyLine > 0 && !paramNames.isEmpty();

    var sb = new StringBuilder();
    if (canWrapInFunction) {
      sb.append(buildFunctionHeader(paramNames, fullContent.substring(arrowEnd)));
    } else {
      var line0 = lineContents.get(0);
      if (line0 != null) {
        sb.append(line0);
      }
    }

    for (var line = 1; line <= maxLine; line++) {
      sb.append('\n');
      var lineSb = lineContents.get(line);
      if (lineSb != null) {
        sb.append(lineSb);
      }
    }

    if (canWrapInFunction) {
      sb.append("\nКонецФункции");
    }

    return removeDoubleQuotesPreservingPositions(sb.toString());
  }

  private static TreeMap<Integer, StringBuilder> fillBodyLineContents(
    List<ContentSegment> segments, String fullContent, int arrowEnd
  ) {
    var lineContents = new TreeMap<Integer, StringBuilder>();
    for (var segment : segments) {
      var segStart = segment.contentOffset();
      var segEnd = segStart + segment.length();
      if (segEnd <= arrowEnd) {
        continue;
      }
      var bodyStartInSeg = Math.max(0, arrowEnd - segStart);
      var docCol = segment.docCharStart() + bodyStartInSeg;
      var content = fullContent.substring(segStart + bodyStartInSeg, segEnd);
      var lineSb = lineContents.computeIfAbsent(segment.docLine(), k -> new StringBuilder());
      while (lineSb.length() < docCol) {
        lineSb.append(' ');
      }
      lineSb.append(content);
    }
    return lineContents;
  }

  private static String buildFunctionHeader(List<String> paramNames, String bodyText) {
    var bodyLower = bodyText.toLowerCase(Locale.ENGLISH);
    var hasReturn = bodyLower.contains("возврат") || bodyLower.contains("return");
    var header = "Функция _Лямбда(" + String.join(", ", paramNames) + ")";
    return hasReturn ? header : (header + " " + RETURN_KEYWORD);
  }

  /**
   * Заменяет экранированные двойные кавычки {@code ""} на одинарную кавычку {@code "}
   * с пробелом для сохранения позиций. Чередует: opening {@code ""} → {@code  "} (пробел + кавычка),
   * closing {@code ""} → {@code " } (кавычка + пробел). Аналог QueryComputer.removeDoubleQuotes.
   */
  private static String removeDoubleQuotesPreservingPositions(String text) {
    var leftQuoteFound = false;
    var matcher = QUOTE_PAIR_PATTERN.matcher(text);
    var newText = text;
    var textLength = text.length();
    var strings = new StringJoiner("");
    while (matcher.find()) {
      var quotesLineLength = matcher.group(0).length();
      var emptyString = " ".repeat(quotesLineLength / ESCAPE_QUOTE_LENGTH);
      strings.add(newText.substring(0, matcher.start()) + (leftQuoteFound ? "" : emptyString)
        + matcher.group(0).substring(0, quotesLineLength / ESCAPE_QUOTE_LENGTH) + (leftQuoteFound ? emptyString : ""));

      if (matcher.end() < textLength) {
        newText = newText.substring(matcher.end());
        textLength = newText.length();
      } else {
        newText = "";
        break;
      }

      matcher = QUOTE_PAIR_PATTERN.matcher(newText);
      leftQuoteFound = !leftQuoteFound;
    }

    if (!newText.isEmpty()) {
      strings.add(newText);
    }

    return strings.toString();
  }

  private static List<String> extractLambdaParamNames(String paramsPart) {
    var names = new ArrayList<String>();
    String cleaned = paramsPart.replace("\"\"", "\"");
    if (cleaned.isBlank()) {
      return names;
    }
    var tokenizer = new BSLTokenizer(cleaned);
    for (Token token : tokenizer.getTokens()) {
      if (token.getChannel() == Token.DEFAULT_CHANNEL && token.getType() == BSLLexer.IDENTIFIER) {
        names.add(token.getText());
      }
    }
    return names;
  }

  private static void collectLambdaParamTokens(
    String paramsPart, int baseOffset, List<ContentSegment> segments, Map<Token, List<SubToken>> result
  ) {
    var cleanedParams = paramsPart.replace("\"\"", "\"");
    if (cleanedParams.isBlank()) {
      return;
    }

    var tokenizer = new BSLTokenizer(cleanedParams);
    for (Token paramToken : tokenizer.getTokens()) {
      if (paramToken.getChannel() == Token.DEFAULT_CHANNEL && paramToken.getType() != Token.EOF) {
        var semanticType = mapLambdaParamTokenToSemanticType(paramToken.getType());
        if (semanticType != null) {
          var cleanedOffset = paramToken.getStartIndex();
          var originalOffset = mapCleanedOffsetToOriginal(paramsPart, cleanedOffset);
          var tokenLength = (int) paramToken.getText().codePoints().count();
          addSubTokenAtOffset(baseOffset + originalOffset, tokenLength, semanticType, segments, result);
        }
      }
    }
  }

  private static void addSubTokenAtOffset(
    int absoluteOffset, int tokenLength, String semanticType,
    List<ContentSegment> segments, Map<Token, List<SubToken>> result
  ) {
    for (var segment : segments) {
      if (absoluteOffset >= segment.contentOffset
        && absoluteOffset < segment.contentOffset + segment.length) {
        int docColumn = segment.docCharStart + (absoluteOffset - segment.contentOffset);
        result.computeIfAbsent(segment.token, k -> new ArrayList<>())
          .add(new SubToken(docColumn, tokenLength, semanticType));
        break;
      }
    }
  }

  /**
   * Сканирует содержимое лямбда-строки на наличие экранированных кавычек {@code ""}
   * и добавляет для них SubToken'ы с типом {@code stringEscape}.
   */
  private static void collectEscapedQuoteTokens(
    String fullContent, List<ContentSegment> segments, Map<Token, List<SubToken>> result
  ) {
    var idx = 0;
    while (idx < fullContent.length() - 1) {
      if (fullContent.charAt(idx) == '"' && fullContent.charAt(idx + 1) == '"') {
        addSubTokenAtOffset(idx, ESCAPE_QUOTE_LENGTH, CustomSemanticTokenTypes.STRING_ESCAPE, segments, result);
        idx += ESCAPE_QUOTE_LENGTH;
      } else {
        idx++;
      }
    }
  }

  /**
   * Сканирует текст на наличие плейсхолдеров СтрШаблон ({@code %1}..{@code %10})
   * и/или ключей языка НСтр и добавляет SubToken'ы.
   */
  private static void collectSpecialContextSubTokens(
    String text, int baseOffset, List<ContentSegment> segments,
    Map<Token, List<SubToken>> result, StringContext context
  ) {
    if (context == StringContext.STR_TEMPLATE || context == StringContext.NSTR_AND_STR_TEMPLATE) {
      var positions = MultilingualStringAnalyser.findPlaceholderPositions(text);
      for (var position : positions) {
        addSubTokenAtOffset(baseOffset + position.start(), position.length(),
          SemanticTokenTypes.Parameter, segments, result);
      }
    }

    if (context == StringContext.NSTR || context == StringContext.NSTR_AND_STR_TEMPLATE) {
      var positions = MultilingualStringAnalyser.findLanguageKeyPositions(text);
      for (var position : positions) {
        addSubTokenAtOffset(baseOffset + position.start(), position.length(),
          SemanticTokenTypes.Property, segments, result);
      }
    }
  }

  private static String extractLambdaFullContent(List<Token> group) {
    var sb = new StringBuilder();
    for (Token token : group) {
      var text = token.getText();
      switch (token.getType()) {
        case BSLLexer.STRING -> sb.append(text, 1, text.length() - 1);
        case BSLLexer.STRINGSTART -> sb.append(text.substring(1));
        case BSLLexer.STRINGPART -> sb.append('\n').append(text.substring(1));
        case BSLLexer.STRINGTAIL -> sb.append('\n').append(text, 1, text.length() - 1);
        default -> { /* non-string token types are skipped */ }
      }
    }
    return sb.toString();
  }

  private record ContentSegment(int contentOffset, int length, int docLine, int docCharStart, Token token) {
  }

  private static List<ContentSegment> buildContentSegments(List<Token> group) {
    List<ContentSegment> segments = new ArrayList<>();
    var contentOffset = 0;

    for (Token token : group) {
      var text = token.getText();
      var type = token.getType();
      var tokenLine = token.getLine() - 1;
      var tokenCharStart = token.getCharPositionInLine();

      int segmentStartInDoc;
      int segmentLength;

      switch (type) {
        case BSLLexer.STRING -> {
          segmentStartInDoc = tokenCharStart + 1;
          segmentLength = text.length() - ESCAPE_QUOTE_LENGTH;
        }
        case BSLLexer.STRINGSTART -> {
          segmentStartInDoc = tokenCharStart + 1;
          segmentLength = text.length() - 1;
        }
        case BSLLexer.STRINGPART -> {
          contentOffset++;
          segmentStartInDoc = tokenCharStart + 1;
          segmentLength = text.length() - 1;
        }
        case BSLLexer.STRINGTAIL -> {
          contentOffset++;
          segmentStartInDoc = tokenCharStart + 1;
          segmentLength = text.length() - ESCAPE_QUOTE_LENGTH;
        }
        default -> {
          continue;
        }
      }

      segments.add(new ContentSegment(contentOffset, segmentLength, tokenLine, segmentStartInDoc, token));
      contentOffset += segmentLength;
    }

    return segments;
  }

  @Nullable
  private static String mapLambdaParamTokenToSemanticType(int bslTokenType) {
    return PARAM_TOKEN_TYPE_MAP.get(bslTokenType);
  }

  private static int mapCleanedOffsetToOriginal(String original, int cleanedOffset) {
    var origIdx = 0;
    var cleanIdx = 0;
    while (cleanIdx < cleanedOffset && origIdx < original.length()) {
      if (origIdx + 1 < original.length()
        && original.charAt(origIdx) == '"'
        && original.charAt(origIdx + 1) == '"') {
        origIdx += ESCAPE_QUOTE_LENGTH;
        cleanIdx++;
      } else {
        origIdx++;
        cleanIdx++;
      }
    }
    return origIdx;
  }
}
