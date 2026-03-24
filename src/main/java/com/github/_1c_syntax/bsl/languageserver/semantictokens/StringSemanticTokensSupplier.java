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
package com.github._1c_syntax.bsl.languageserver.semantictokens;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.events.LanguageServerConfigurationChangedEvent;
import com.github._1c_syntax.bsl.languageserver.configuration.semantictokens.ParsedStrTemplateMethods;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.semantictokens.strings.AstTokenInfo;
import com.github._1c_syntax.bsl.languageserver.semantictokens.strings.QueryContext;
import com.github._1c_syntax.bsl.languageserver.semantictokens.strings.SdblAstTokenCollector;
import com.github._1c_syntax.bsl.languageserver.semantictokens.strings.SdblTokenTypes;
import com.github._1c_syntax.bsl.languageserver.semantictokens.strings.SpecialContextVisitor;
import com.github._1c_syntax.bsl.languageserver.semantictokens.strings.StringContext;
import com.github._1c_syntax.bsl.languageserver.semantictokens.strings.SubToken;
import com.github._1c_syntax.bsl.languageserver.semantictokens.strings.TokenPosition;
import com.github._1c_syntax.bsl.languageserver.utils.MultilingualStringAnalyser;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLTokenizer;
import com.github._1c_syntax.utils.Absolute;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Сапплаер семантических токенов для строк BSL и запросов SDBL.
 * <p>
 * Централизованно обрабатывает все строковые токены и разбивает их на подтокены
 * в зависимости от контекста:
 * <ul>
 *   <li>Запросы SDBL: разбивает строки на части вокруг токенов запроса и добавляет токены SDBL</li>
 *   <li>НСтр/NStr: подсвечивает языковые ключи (ru=, en=)</li>
 *   <li>СтрШаблон/StrTemplate: подсвечивает плейсхолдеры (%1, %2)</li>
 *   <li>Конфигурируемые функции-шаблонизаторы: подсвечивает плейсхолдеры (%1, %2)</li>
 *   <li>Лямбда-выражения (только для .os файлов): подсвечивает BSL-код в теле лямбды</li>
 *   <li>Обычные строки: выдаёт токен для всей строки</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class StringSemanticTokensSupplier implements SemanticTokensSupplier {

  private static final Set<Integer> STRING_TYPES = Set.of(
    BSLLexer.STRING,
    BSLLexer.STRINGPART,
    BSLLexer.STRINGSTART,
    BSLLexer.STRINGTAIL
  );

  private static final Pattern LAMBDA_ARROW_PATTERN = Pattern.compile("->\\s*");

  private final SemanticTokensHelper helper;
  private final LanguageServerConfiguration configuration;
  private final ServerContext serverContext;
  private final SemanticTokensLegend legend;

  @Autowired
  private org.springframework.context.ApplicationContext applicationContext;

  private volatile List<SemanticTokensSupplier> allSuppliers;

  private List<SemanticTokensSupplier> getAllSuppliers() {
    if (allSuppliers == null) {
      allSuppliers = new ArrayList<>(applicationContext.getBeansOfType(SemanticTokensSupplier.class).values());
    }
    return allSuppliers;
  }

  private volatile ParsedStrTemplateMethods parsedStrTemplateMethods;

  @PostConstruct
  private void init() {
    updateParsedStrTemplateMethods();
  }

  /**
   * Обработчик события {@link LanguageServerConfigurationChangedEvent}.
   * <p>
   * Обновляет кэшированные паттерны функций-шаблонизаторов при изменении конфигурации.
   *
   * @param event Событие
   */
  @EventListener
  public void handleEvent(LanguageServerConfigurationChangedEvent event) {
    updateParsedStrTemplateMethods();
  }

  private void updateParsedStrTemplateMethods() {
    parsedStrTemplateMethods = configuration.getSemanticTokensOptions().getParsedStrTemplateMethods();
  }

  @Override
  public List<SemanticTokenEntry> getSemanticTokens(DocumentContext documentContext) {
    List<SemanticTokenEntry> entries = new ArrayList<>();

    // Собираем информацию о контекстах строк
    var specialStringContexts = collectSpecialStringContexts(documentContext);
    var queryStringContexts = collectQueryStringContexts(documentContext);
    var lambdaStringContexts = collectLambdaStringContexts(documentContext);

    // Обрабатываем все строковые токены
    var stringTokens = documentContext.getTokensFromDefaultChannel().stream()
      .filter(token -> STRING_TYPES.contains(token.getType()))
      .toList();

    for (Token stringToken : stringTokens) {
      processStringToken(entries, stringToken, specialStringContexts, queryStringContexts, lambdaStringContexts);
    }

    return entries;
  }

  private void processStringToken(
    List<SemanticTokenEntry> entries,
    Token stringToken,
    Map<Token, StringContext> specialContexts,
    Map<Token, QueryContext> queryContexts,
    Map<Token, List<SubToken>> lambdaContexts
  ) {
    // Проверяем, является ли строка частью запроса
    var queryContext = queryContexts.get(stringToken);
    if (queryContext != null) {
      processQueryString(entries, stringToken, queryContext);
      return;
    }

    // Проверяем, содержит ли строка лямбда-выражение
    var lambdaSubTokens = lambdaContexts.get(stringToken);
    if (lambdaSubTokens != null) {
      processLambdaString(entries, stringToken, lambdaSubTokens);
      return;
    }

    // Проверяем специальные контексты (НСтр, СтрШаблон)
    var context = specialContexts.get(stringToken);
    if (context != null) {
      processSpecialContext(entries, stringToken, context);
      return;
    }

    // Обычная строка - добавляем токен для всей строки
    var stringRange = Ranges.create(stringToken);
    helper.addRange(entries, stringRange, SemanticTokenTypes.String);
  }

  private void processQueryString(
    List<SemanticTokenEntry> entries,
    Token stringToken,
    QueryContext queryContext
  ) {
    var stringRange = Ranges.create(stringToken);
    int stringLine = stringRange.getStart().getLine();
    int stringStart = stringRange.getStart().getCharacter();
    int stringEnd = stringRange.getEnd().getCharacter();

    // Получаем SDBL токены на этой строке
    var sdblTokensOnLine = queryContext.sdblTokensByLine().get(stringLine);
    if (sdblTokensOnLine == null || sdblTokensOnLine.isEmpty()) {
      // Нет SDBL токенов - добавляем как обычную строку
      helper.addRange(entries, stringRange, SemanticTokenTypes.String);
      return;
    }

    // Фильтруем токены, которые находятся внутри этой строки
    var overlappingTokens = sdblTokensOnLine.stream()
      .filter(sdblToken -> {
        int sdblStart = sdblToken.getCharPositionInLine();
        int sdblEnd = sdblStart + (int) sdblToken.getText().codePoints().count();
        return sdblStart >= stringStart && sdblEnd <= stringEnd;
      })
      .sorted(Comparator.comparingInt(Token::getCharPositionInLine))
      .toList();

    if (overlappingTokens.isEmpty()) {
      helper.addRange(entries, stringRange, SemanticTokenTypes.String);
      return;
    }

    // Разбиваем строку на части и добавляем SDBL токены
    int currentPos = stringStart;
    var skipPositions = queryContext.skipPositions();

    for (Token sdblToken : overlappingTokens) {
      int sdblStart = sdblToken.getCharPositionInLine();
      int sdblEnd = sdblStart + (int) sdblToken.getText().codePoints().count();

      // Проверяем, нужно ли пропустить этот токен (например, он уже был обработан как часть параметра)
      if (skipPositions.contains(new TokenPosition(stringLine, sdblStart, sdblEnd - sdblStart))) {
        continue;
      }

      // Добавляем часть строки до SDBL токена
      if (currentPos < sdblStart) {
        helper.addEntry(entries, stringLine, currentPos, sdblStart - currentPos, SemanticTokenTypes.String);
      }

      // Добавляем SDBL токен с учётом AST-переопределений и получаем фактическую длину
      int actualLength = addSdblToken(entries, sdblToken, queryContext.astTokenOverrides());

      currentPos = sdblStart + actualLength;
    }

    // Добавляем финальную часть строки
    if (currentPos < stringEnd) {
      helper.addEntry(entries, stringLine, currentPos, stringEnd - currentPos, SemanticTokenTypes.String);
    }
  }

  private int addSdblToken(
    List<SemanticTokenEntry> entries,
    Token token,
    Map<TokenPosition, AstTokenInfo> astTokenOverrides
  ) {
    int zeroIndexedLine = token.getLine() - 1;
    int start = token.getCharPositionInLine();
    int length = (int) token.getText().codePoints().count();

    // Сначала проверяем AST-переопределение
    var tokenPosition = new TokenPosition(zeroIndexedLine, start, length);
    var astOverride = astTokenOverrides.get(tokenPosition);
    if (astOverride != null) {
      // Используем переопределённую длину, если она задана
      int effectiveLength = astOverride.overrideLength() > 0 ? astOverride.overrideLength() : length;
      var range = new Range(
        new Position(zeroIndexedLine, start),
        new Position(zeroIndexedLine, start + effectiveLength)
      );
      helper.addRange(entries, range, astOverride.type(), astOverride.modifiers());
      return effectiveLength;
    }

    // Иначе используем тип на основе лексера
    var tokenType = token.getType();
    var semanticTypeAndModifiers = SdblTokenTypes.getTokenTypeAndModifiers(tokenType);
    if (semanticTypeAndModifiers != null) {
      var range = new Range(
        new Position(zeroIndexedLine, start),
        new Position(zeroIndexedLine, start + length)
      );
      helper.addRange(entries, range, semanticTypeAndModifiers.type(), semanticTypeAndModifiers.modifiers());
    }
    return length;
  }

  private void processSpecialContext(
    List<SemanticTokenEntry> entries,
    Token stringToken,
    StringContext context
  ) {
    var stringRange = Ranges.create(stringToken);
    String tokenText = stringToken.getText();
    int tokenLine = stringToken.getLine() - 1; // 0-indexed
    int tokenStart = stringToken.getCharPositionInLine();
    int stringEnd = stringRange.getEnd().getCharacter();

    List<SubToken> subTokens = new ArrayList<>();

    if (context == StringContext.NSTR || context == StringContext.NSTR_AND_STR_TEMPLATE) {
      var positions = MultilingualStringAnalyser.findLanguageKeyPositions(tokenText);
      for (var position : positions) {
        subTokens.add(new SubToken(
          tokenStart + position.start(),
          position.length(),
          SemanticTokenTypes.Property
        ));
      }
    }

    if (context == StringContext.STR_TEMPLATE || context == StringContext.NSTR_AND_STR_TEMPLATE) {
      var positions = MultilingualStringAnalyser.findPlaceholderPositions(tokenText);
      for (var position : positions) {
        subTokens.add(new SubToken(
          tokenStart + position.start(),
          position.length(),
          SemanticTokenTypes.Parameter
        ));
      }
    }

    if (subTokens.isEmpty()) {
      helper.addRange(entries, stringRange, SemanticTokenTypes.String);
      return;
    }

    subTokens.sort(Comparator.comparingInt(SubToken::start));

    // Разбиваем строку на части вокруг подтокенов
    int currentPos = tokenStart;

    for (SubToken subToken : subTokens) {
      if (currentPos < subToken.start()) {
        helper.addEntry(entries, tokenLine, currentPos, subToken.start() - currentPos, SemanticTokenTypes.String);
      }
      helper.addEntry(entries, tokenLine, subToken.start(), subToken.length(), subToken.type());
      currentPos = subToken.start() + subToken.length();
    }

    if (currentPos < stringEnd) {
      helper.addEntry(entries, tokenLine, currentPos, stringEnd - currentPos, SemanticTokenTypes.String);
    }
  }

  private Map<Token, StringContext> collectSpecialStringContexts(DocumentContext documentContext) {
    Map<Token, StringContext> contexts = new HashMap<>();
    var visitor = new SpecialContextVisitor(contexts, parsedStrTemplateMethods);
    visitor.visit(documentContext.getAst());
    return contexts;
  }

  private Map<Token, QueryContext> collectQueryStringContexts(DocumentContext documentContext) {
    Map<Token, QueryContext> contexts = new HashMap<>();
    var queries = documentContext.getQueries();

    if (queries.isEmpty()) {
      return contexts;
    }

    // Собираем SDBL токены по строкам
    var sdblTokensByLine = new HashMap<Integer, List<Token>>();
    for (var query : queries) {
      for (Token token : query.getTokens()) {
        if (token.getChannel() != Token.DEFAULT_CHANNEL) {
          continue;
        }
        int zeroIndexedLine = token.getLine() - 1;
        sdblTokensByLine.computeIfAbsent(zeroIndexedLine, k -> new ArrayList<>()).add(token);
      }
    }

    // Собираем AST-based переопределения типов токенов и позиции для пропуска
    var astTokenOverrides = new HashMap<TokenPosition, AstTokenInfo>();
    var skipPositions = new HashSet<TokenPosition>();
    for (var query : queries) {
      var collector = new SdblAstTokenCollector(astTokenOverrides, skipPositions);
      collector.visit(query.getAst());
    }

    // Определяем, какие строки содержат запросы
    var stringTokens = documentContext.getTokensFromDefaultChannel().stream()
      .filter(token -> STRING_TYPES.contains(token.getType()))
      .toList();

    var queryContext = new QueryContext(sdblTokensByLine, astTokenOverrides, skipPositions);

    for (Token stringToken : stringTokens) {
      var stringRange = Ranges.create(stringToken);
      int stringLine = stringRange.getStart().getLine();

      var sdblTokensOnLine = sdblTokensByLine.get(stringLine);
      if (sdblTokensOnLine == null || sdblTokensOnLine.isEmpty()) {
        continue;
      }

      // Проверяем, есть ли SDBL токены внутри этой строки
      int stringStart = stringRange.getStart().getCharacter();
      int stringEnd = stringRange.getEnd().getCharacter();

      boolean hasOverlapping = sdblTokensOnLine.stream()
        .anyMatch(sdblToken -> {
          int sdblStart = sdblToken.getCharPositionInLine();
          int sdblEnd = sdblStart + (int) sdblToken.getText().codePoints().count();
          return sdblStart >= stringStart && sdblEnd <= stringEnd;
        });

      if (hasOverlapping) {
        contexts.put(stringToken, queryContext);
      }
    }

    return contexts;
  }

  // ==================== Lambda String Processing ====================

  private void processLambdaString(
    List<SemanticTokenEntry> entries,
    Token stringToken,
    List<SubToken> subTokens
  ) {
    var stringRange = Ranges.create(stringToken);
    int tokenLine = stringToken.getLine() - 1;
    int tokenStart = stringToken.getCharPositionInLine();
    int stringEnd = stringRange.getEnd().getCharacter();
    int tokenType = stringToken.getType();

    if (subTokens.isEmpty()) {
      helper.addRange(entries, stringRange, SemanticTokenTypes.String);
      return;
    }

    // Opening delimiter: " for STRING/STRINGSTART, | for STRINGPART/STRINGTAIL
    helper.addEntry(entries, tokenLine, tokenStart, 1, SemanticTokenTypes.String);

    // Fill gaps between sub-tokens with SOURCE to override TextMate string scope
    int currentPos = tokenStart + 1;
    for (SubToken subToken : subTokens) {
      if (currentPos < subToken.start()) {
        helper.addEntry(entries, tokenLine, currentPos, subToken.start() - currentPos, CustomSemanticTokenTypes.SOURCE);
      }
      helper.addEntry(entries, tokenLine, subToken.start(), subToken.length(), subToken.type());
      currentPos = subToken.start() + subToken.length();
    }

    // Closing quote: only for STRING and STRINGTAIL
    if (tokenType == BSLLexer.STRING || tokenType == BSLLexer.STRINGTAIL) {
      // Fill gap before closing quote with SOURCE
      int closingQuotePos = stringEnd - 1;
      if (currentPos < closingQuotePos) {
        helper.addEntry(entries, tokenLine, currentPos, closingQuotePos - currentPos, CustomSemanticTokenTypes.SOURCE);
      }
      helper.addEntry(entries, tokenLine, closingQuotePos, 1, SemanticTokenTypes.String);
    } else if (currentPos < stringEnd) {
      // For STRINGSTART/STRINGPART, fill remaining with SOURCE
      helper.addEntry(entries, tokenLine, currentPos, stringEnd - currentPos, CustomSemanticTokenTypes.SOURCE);
    }
  }

  /**
   * Собирает контексты лямбда-строк: для каждого строкового токена, входящего
   * в строковый литерал с оператором {@code ->}, вычисляет список подтокенов
   * (ключевые слова, операторы, числа) с их позициями в документе.
   * <p>
   * Работает только для файлов OneScript (.os).
   */
  private Map<Token, List<SubToken>> collectLambdaStringContexts(DocumentContext documentContext) {
    if (documentContext.getFileType() != FileType.OS) {
      return Map.of();
    }

    var stringTokens = documentContext.getTokensFromDefaultChannel().stream()
      .filter(token -> STRING_TYPES.contains(token.getType()))
      .toList();

    var groups = groupStringTokens(stringTokens);
    Map<Token, List<SubToken>> result = new HashMap<>();

    for (var group : groups) {
      collectLambdaSubTokensForGroup(group, result);
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
      int type = token.getType();
      if (type == BSLLexer.STRING) {
        groups.add(List.of(token));
      } else if (type == BSLLexer.STRINGSTART) {
        currentGroup = new ArrayList<>();
        currentGroup.add(token);
      } else if (currentGroup != null && (type == BSLLexer.STRINGPART || type == BSLLexer.STRINGTAIL)) {
        currentGroup.add(token);
        if (type == BSLLexer.STRINGTAIL) {
          groups.add(currentGroup);
          currentGroup = null;
        }
      }
    }

    return groups;
  }

  /**
   * Для группы токенов одного строкового литерала проверяет наличие {@code ->},
   * токенизирует тело лямбды через BSLTokenizer и маппит позиции обратно в документ.
   */
  private void collectLambdaSubTokensForGroup(List<Token> group, Map<Token, List<SubToken>> result) {
    String fullContent = extractLambdaFullContent(group);

    var arrowMatcher = LAMBDA_ARROW_PATTERN.matcher(fullContent);
    if (!arrowMatcher.find()) {
      return;
    }

    var segments = buildContentSegments(group);
    int arrowStart = arrowMatcher.start();
    int arrowEnd = arrowMatcher.end();

    // Extract parameter names for function wrapper
    var paramNames = extractLambdaParamNames(fullContent.substring(0, arrowStart));

    // Escaped double quotes "" — both before and after ->
    collectEscapedQuoteTokens(fullContent, segments, result);

    // Parameters (before ->)
    collectLambdaParamTokens(fullContent.substring(0, arrowStart), 0, segments, result);

    // Arrow (->) itself
    addSubTokenAtOffset(arrowStart, 2, SemanticTokenTypes.Operator, segments, result);

    // Body (after ->) — delegate to all semantic token suppliers via virtual DocumentContext
    var bodyEntries = collectLambdaBodyTokensViaSuppliers(group, arrowEnd, paramNames);
    for (var entry : bodyEntries) {
      for (var segment : segments) {
        if (segment.docLine() == entry.line()
          && entry.start() >= segment.docCharStart()
          && entry.start() < segment.docCharStart() + segment.length()) {
          String typeName = legend.getTokenTypes().get(entry.type());
          result.computeIfAbsent(segment.token(), k -> new ArrayList<>())
            .add(new SubToken(entry.start(), entry.length(), typeName));
          break;
        }
      }
    }

    for (Token groupToken : group) {
      var list = result.get(groupToken);
      if (list != null) {
        list.sort(Comparator.comparingInt(SubToken::start));
        removeOverlappingTokens(list);
      }
    }
  }

  /**
   * Удаляет токены, перекрывающиеся с предыдущими. Первый токен в позиции
   * (escape-кавычки добавляются раньше body-токенов) имеет приоритет.
   */
  private static void removeOverlappingTokens(List<SubToken> tokens) {
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

      return getAllSuppliers().stream()
        .map(s -> s.getSemanticTokens(virtualDoc))
        .flatMap(Collection::stream)
        .filter(entry -> entry.line() >= firstBodyLine && entry.line() <= lastBodyLine)
        .toList();
    } finally {
      serverContext.removeDocument(virtualUri);
    }
  }

  private static int getFirstBodyLine(List<Token> group, int arrowEnd) {
    var segments = buildContentSegments(group);
    for (var segment : segments) {
      int segEnd = segment.contentOffset() + segment.length();
      if (segEnd > arrowEnd) {
        return segment.docLine();
      }
    }
    return 0;
  }

  private static int getLastBodyLine(List<Token> group, int arrowEnd) {
    var segments = buildContentSegments(group);
    int lastLine = 0;
    for (var segment : segments) {
      int segEnd = segment.contentOffset() + segment.length();
      if (segEnd > arrowEnd) {
        lastLine = segment.docLine();
      }
    }
    return lastLine;
  }

  private static final String RETURN_KEYWORD = "Возврат";

  private static String buildPaddedLambdaBody(List<Token> group, int arrowEnd, List<String> paramNames) {
    var segments = buildContentSegments(group);
    String fullContent = extractLambdaFullContent(group);

    var lineContents = new TreeMap<Integer, StringBuilder>();

    for (var segment : segments) {
      int segStart = segment.contentOffset();
      int segEnd = segStart + segment.length();

      if (segEnd <= arrowEnd) {
        continue;
      }

      int bodyStartInSeg = Math.max(0, arrowEnd - segStart);
      int docCol = segment.docCharStart() + bodyStartInSeg;
      String content = fullContent.substring(segStart + bodyStartInSeg, segEnd);

      var lineSb = lineContents.computeIfAbsent(segment.docLine(), k -> new StringBuilder());
      while (lineSb.length() < docCol) {
        lineSb.append(' ');
      }
      lineSb.append(content);
    }

    if (lineContents.isEmpty()) {
      return "";
    }

    int firstBodyLine = lineContents.firstKey();
    int maxLine = lineContents.lastKey();
    boolean canWrapInFunction = firstBodyLine > 0 && !paramNames.isEmpty();

    var sb = new StringBuilder();

    // Insert fake function header at line 0
    if (canWrapInFunction) {
      String bodyText = fullContent.substring(arrowEnd);
      boolean bodyHasReturn = bodyText.toLowerCase(java.util.Locale.ENGLISH).contains("возврат")
        || bodyText.toLowerCase(java.util.Locale.ENGLISH).contains("return");

      sb.append("Функция _Лямбда(");
      sb.append(String.join(", ", paramNames));
      sb.append(')');
      if (!bodyHasReturn) {
        sb.append(' ').append(RETURN_KEYWORD);
      }
    } else {
      var line0 = lineContents.get(0);
      if (line0 != null) {
        sb.append(line0);
      }
    }

    for (int line = 1; line <= maxLine; line++) {
      sb.append('\n');
      var lineSb = lineContents.get(line);
      if (lineSb != null) {
        sb.append(lineSb);
      }
    }

    // Append КонецФункции on a new line after body
    if (canWrapInFunction) {
      sb.append("\nКонецФункции");
    }

    return removeDoubleQuotesPreservingPositions(sb.toString());
  }

  private static final Pattern QUOTE_PAIR_PATTERN = Pattern.compile(
    "(?:\"{12}|\"{10}|\"{8}|\"{6}|\"{4}|\"{2})"
  );

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
    var strings = new java.util.StringJoiner("");
    while (matcher.find()) {
      var quotesLineLength = matcher.group(0).length();
      var emptyString = " ".repeat(quotesLineLength / 2);
      strings.add(newText.substring(0, matcher.start()) + (leftQuoteFound ? "" : emptyString)
        + matcher.group(0).substring(0, quotesLineLength / 2) + (leftQuoteFound ? emptyString : ""));

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
    String cleanedParams = paramsPart.replace("\"\"", "\"");
    if (cleanedParams.isBlank()) {
      return;
    }

    var tokenizer = new BSLTokenizer(cleanedParams);
    for (Token paramToken : tokenizer.getTokens()) {
      if (paramToken.getChannel() != Token.DEFAULT_CHANNEL || paramToken.getType() == Token.EOF) {
        continue;
      }

      String semanticType = mapLambdaParamTokenToSemanticType(paramToken.getType());
      if (semanticType == null) {
        continue;
      }

      int cleanedOffset = paramToken.getStartIndex();
      int originalOffset = mapCleanedOffsetToOriginal(paramsPart, cleanedParams, cleanedOffset);
      int tokenLength = (int) paramToken.getText().codePoints().count();
      addSubTokenAtOffset(baseOffset + originalOffset, tokenLength, semanticType, segments, result);
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
    int idx = 0;
    while (idx < fullContent.length() - 1) {
      if (fullContent.charAt(idx) == '"' && fullContent.charAt(idx + 1) == '"') {
        addSubTokenAtOffset(idx, 2, CustomSemanticTokenTypes.STRING_ESCAPE, segments, result);
        idx += 2;
      } else {
        idx++;
      }
    }
  }

  private static String extractLambdaFullContent(List<Token> group) {
    var sb = new StringBuilder();
    for (Token token : group) {
      String text = token.getText();
      int type = token.getType();
      if (type == BSLLexer.STRING) {
        sb.append(text, 1, text.length() - 1);
      } else if (type == BSLLexer.STRINGSTART) {
        sb.append(text.substring(1));
      } else if (type == BSLLexer.STRINGPART) {
        sb.append('\n');
        sb.append(text.substring(1));
      } else if (type == BSLLexer.STRINGTAIL) {
        sb.append('\n');
        sb.append(text, 1, text.length() - 1);
      }
    }
    return sb.toString();
  }

  private record ContentSegment(int contentOffset, int length, int docLine, int docCharStart, Token token) {
  }

  private static List<ContentSegment> buildContentSegments(List<Token> group) {
    List<ContentSegment> segments = new ArrayList<>();
    int contentOffset = 0;

    for (Token token : group) {
      String text = token.getText();
      int type = token.getType();
      int tokenLine = token.getLine() - 1;
      int tokenCharStart = token.getCharPositionInLine();

      int segmentStartInDoc;
      int segmentLength;

      if (type == BSLLexer.STRING) {
        segmentStartInDoc = tokenCharStart + 1;
        segmentLength = text.length() - 2;
      } else if (type == BSLLexer.STRINGSTART) {
        segmentStartInDoc = tokenCharStart + 1;
        segmentLength = text.length() - 1;
      } else if (type == BSLLexer.STRINGPART) {
        contentOffset++;
        segmentStartInDoc = tokenCharStart + 1;
        segmentLength = text.length() - 1;
      } else if (type == BSLLexer.STRINGTAIL) {
        contentOffset++;
        segmentStartInDoc = tokenCharStart + 1;
        segmentLength = text.length() - 2;
      } else {
        continue;
      }

      segments.add(new ContentSegment(contentOffset, segmentLength, tokenLine, segmentStartInDoc, token));
      contentOffset += segmentLength;
    }

    return segments;
  }

  private static final Set<Integer> LAMBDA_PARAM_OPERATOR_TYPES = Set.of(
    BSLLexer.LPAREN,
    BSLLexer.RPAREN,
    BSLLexer.COMMA,
    BSLLexer.ASSIGN
  );

  private static final Set<Integer> ANNOTATION_SYMBOL_TYPES = Set.of(
    BSLLexer.ANNOTATION_CUSTOM_SYMBOL,
    BSLLexer.ANNOTATION_BEFORE_SYMBOL,
    BSLLexer.ANNOTATION_AFTER_SYMBOL,
    BSLLexer.ANNOTATION_AROUND_SYMBOL,
    BSLLexer.ANNOTATION_CHANGEANDVALIDATE_SYMBOL,
    BSLLexer.ANNOTATION_ATSERVER_SYMBOL,
    BSLLexer.ANNOTATION_ATCLIENT_SYMBOL,
    BSLLexer.ANNOTATION_ATSERVERNOCONTEXT_SYMBOL,
    BSLLexer.ANNOTATION_ATCLIENTATSERVER_SYMBOL,
    BSLLexer.ANNOTATION_ATCLIENTATSERVERNOCONTEXT_SYMBOL
  );

  private static String mapLambdaParamTokenToSemanticType(int bslTokenType) {
    if (bslTokenType == BSLLexer.IDENTIFIER) {
      return SemanticTokenTypes.Parameter;
    }
    if (LAMBDA_PARAM_OPERATOR_TYPES.contains(bslTokenType)) {
      return SemanticTokenTypes.Operator;
    }
    if (bslTokenType == BSLLexer.AMPERSAND || ANNOTATION_SYMBOL_TYPES.contains(bslTokenType)) {
      return SemanticTokenTypes.Decorator;
    }
    if (bslTokenType == BSLLexer.STRING) {
      return SemanticTokenTypes.String;
    }
    if (bslTokenType == BSLLexer.FLOAT || bslTokenType == BSLLexer.DECIMAL) {
      return SemanticTokenTypes.Number;
    }
    if (bslTokenType == BSLLexer.DATETIME) {
      return SemanticTokenTypes.Number;
    }
    if (bslTokenType == BSLLexer.TRUE
      || bslTokenType == BSLLexer.FALSE
      || bslTokenType == BSLLexer.UNDEFINED
      || bslTokenType == BSLLexer.NULL) {
      return SemanticTokenTypes.Keyword;
    }
    if (bslTokenType == BSLLexer.PLUS || bslTokenType == BSLLexer.MINUS) {
      return SemanticTokenTypes.Operator;
    }
    return null;
  }

  private static int mapCleanedOffsetToOriginal(String original, String cleaned, int cleanedOffset) {
    int origIdx = 0;
    int cleanIdx = 0;
    while (cleanIdx < cleanedOffset && origIdx < original.length()) {
      if (origIdx + 1 < original.length()
        && original.charAt(origIdx) == '"'
        && original.charAt(origIdx + 1) == '"') {
        origIdx += 2;
        cleanIdx++;
      } else {
        origIdx++;
        cleanIdx++;
      }
    }
    return origIdx;
  }
}
