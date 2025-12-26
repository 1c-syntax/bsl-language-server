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
package com.github._1c_syntax.bsl.languageserver.semantictokens;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
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
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Сапплаер семантических токенов для строк BSL и запросов SDBL.
 * <p>
 * Централизованно обрабатывает все строковые токены и разбивает их на подтокены
 * в зависимости от контекста:
 * <ul>
 *   <li>Запросы SDBL: разбивает строки на части вокруг токенов запроса и добавляет токены SDBL</li>
 *   <li>НСтр/NStr: подсвечивает языковые ключи (ru=, en=)</li>
 *   <li>СтрШаблон/StrTemplate: подсвечивает плейсхолдеры (%1, %2)</li>
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

  private final SemanticTokensHelper helper;

  @Override
  public List<SemanticTokenEntry> getSemanticTokens(DocumentContext documentContext) {
    List<SemanticTokenEntry> entries = new ArrayList<>();

    // Собираем информацию о контекстах строк
    var specialStringContexts = collectSpecialStringContexts(documentContext);
    var queryStringContexts = collectQueryStringContexts(documentContext);

    // Обрабатываем все строковые токены
    var stringTokens = documentContext.getTokensFromDefaultChannel().stream()
      .filter(token -> STRING_TYPES.contains(token.getType()))
      .toList();

    for (Token stringToken : stringTokens) {
      processStringToken(entries, stringToken, specialStringContexts, queryStringContexts);
    }

    return entries;
  }

  private void processStringToken(
    List<SemanticTokenEntry> entries,
    Token stringToken,
    Map<Token, StringContext> specialContexts,
    Map<Token, QueryContext> queryContexts
  ) {
    // Проверяем, является ли строка частью запроса
    var queryContext = queryContexts.get(stringToken);
    if (queryContext != null) {
      processQueryString(entries, stringToken, queryContext);
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
    var visitor = new SpecialContextVisitor(contexts);
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
}
