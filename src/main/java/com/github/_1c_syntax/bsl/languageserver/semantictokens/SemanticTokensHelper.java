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

import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Вспомогательные методы для создания семантических токенов.
 * <p>
 * Предоставляет методы для добавления токенов в список с учетом
 * легенды семантических токенов.
 */
@Component
@RequiredArgsConstructor
public class SemanticTokensHelper {

  private static final String[] NO_MODIFIERS = new String[0];

  private final SemanticTokensLegend legend;

  /**
   * Добавить токен с заданным типом без модификаторов.
   *
   * @param entries Список токенов для наполнения
   * @param range   Диапазон токена
   * @param type    Тип токена (из SemanticTokenTypes)
   */
  public void addRange(List<SemanticTokenEntry> entries, Range range, String type) {
    addRange(entries, range, type, NO_MODIFIERS);
  }

  /**
   * Добавить токен с заданным типом и модификаторами.
   *
   * @param entries   Список токенов для наполнения
   * @param range     Диапазон токена
   * @param type      Тип токена (из SemanticTokenTypes)
   * @param modifiers Модификаторы токена (из SemanticTokenModifiers)
   */
  public void addRange(List<SemanticTokenEntry> entries, Range range, String type, String... modifiers) {
    int explicitLength = Math.max(0, range.getEnd().getCharacter() - range.getStart().getCharacter());
    addEntry(entries, range.getStart().getLine(), range.getStart().getCharacter(), explicitLength, type, modifiers);
  }

  /**
   * Добавить токен с явно указанной длиной (для многострочных токенов).
   *
   * @param entries        Список токенов для наполнения
   * @param range          Диапазон токена
   * @param explicitLength Явная длина токена
   * @param type           Тип токена (из SemanticTokenTypes)
   * @param modifiers      Модификаторы токена (из SemanticTokenModifiers)
   */
  public void addRange(List<SemanticTokenEntry> entries, Range range, int explicitLength, String type, String[] modifiers) {
    addEntry(entries, range.getStart().getLine(), range.getStart().getCharacter(), explicitLength, type, modifiers);
  }

  /**
   * Добавить токен по позиции и длине без модификаторов.
   *
   * @param entries Список токенов для наполнения
   * @param line    Номер строки (0-индексированный)
   * @param start   Начальная позиция в строке
   * @param length  Длина токена
   * @param type    Тип токена (из SemanticTokenTypes)
   */
  public void addEntry(List<SemanticTokenEntry> entries, int line, int start, int length, String type) {
    addEntry(entries, line, start, length, type, NO_MODIFIERS);
  }

  /**
   * Добавить токен по позиции и длине с модификаторами.
   *
   * @param entries   Список токенов для наполнения
   * @param line      Номер строки (0-индексированный)
   * @param start     Начальная позиция в строке
   * @param length    Длина токена
   * @param type      Тип токена (из SemanticTokenTypes)
   * @param modifiers Модификаторы токена (из SemanticTokenModifiers)
   */
  public void addEntry(List<SemanticTokenEntry> entries, int line, int start, int length, String type, String... modifiers) {
    if (length <= 0) {
      return;
    }
    int typeIdx = legend.getTokenTypes().indexOf(type);
    if (typeIdx < 0) {
      return;
    }
    var modifierMask = computeModifierMask(modifiers);
    entries.add(new SemanticTokenEntry(line, start, length, typeIdx, modifierMask));
  }

  /**
   * Добавить токен из ANTLR токена с явно указанным типом.
   *
   * @param entries Список токенов для наполнения
   * @param token   ANTLR токен
   * @param type    Тип семантического токена (из SemanticTokenTypes)
   */
  public void addTokenRange(List<SemanticTokenEntry> entries, Token token, String type) {
    addTokenRange(entries, token, type, NO_MODIFIERS);
  }

  /**
   * Добавить токен из ANTLR токена с явно указанным типом и модификаторами.
   *
   * @param entries   Список токенов для наполнения
   * @param token     ANTLR токен
   * @param type      Тип семантического токена (из SemanticTokenTypes)
   * @param modifiers Модификаторы токена (из SemanticTokenModifiers)
   */
  public void addTokenRange(List<SemanticTokenEntry> entries, @Nullable Token token, String type, String... modifiers) {
    if (token == null) {
      return;
    }

    // ANTLR uses 1-indexed line numbers, convert to 0-indexed for LSP Range
    int zeroIndexedLine = token.getLine() - 1;
    int start = token.getCharPositionInLine();
    int length = (int) token.getText().codePoints().count();

    var range = new Range(
      new Position(zeroIndexedLine, start),
      new Position(zeroIndexedLine, start + length)
    );

    addRange(entries, range, type, modifiers);
  }

  /**
   * Добавить токен из TerminalNode с явно указанным типом и модификаторами.
   *
   * @param entries   Список токенов для наполнения
   * @param node      Терминальный узел AST
   * @param type      Тип семантического токена (из SemanticTokenTypes)
   * @param modifiers Модификаторы токена (из SemanticTokenModifiers)
   */
  public void addTerminalNodeRange(List<SemanticTokenEntry> entries, @Nullable TerminalNode node, String type, String... modifiers) {
    if (node != null) {
      addTokenRange(entries, node.getSymbol(), type, modifiers);
    }
  }

  /**
   * Добавить токен из ParserRuleContext (от начала до конца контекста).
   *
   * @param entries   Список токенов для наполнения
   * @param ctx       Контекст парсера
   * @param type      Тип семантического токена (из SemanticTokenTypes)
   * @param modifiers Модификаторы токена (из SemanticTokenModifiers)
   */
  public void addContextRange(List<SemanticTokenEntry> entries, @Nullable ParserRuleContext ctx, String type, String... modifiers) {
    if (ctx == null || ctx.getStart() == null || ctx.getStop() == null) {
      return;
    }

    var startToken = ctx.getStart();
    var stopToken = ctx.getStop();

    // ANTLR uses 1-indexed line numbers, convert to 0-indexed for LSP Range
    int zeroIndexedLine = startToken.getLine() - 1;
    int start = startToken.getCharPositionInLine();

    // Calculate length from start of first token to end of last token
    int stopEndPosition = stopToken.getCharPositionInLine() + (int) stopToken.getText().codePoints().count();
    int length = stopEndPosition - start;

    var range = new Range(
      new Position(zeroIndexedLine, start),
      new Position(zeroIndexedLine, start + length)
    );

    addRange(entries, range, type, modifiers);
  }

  /**
   * Получить индекс типа токена в легенде.
   *
   * @param type Тип токена (из SemanticTokenTypes)
   * @return Индекс типа в легенде или -1, если тип не найден
   */
  public int getTypeIndex(String type) {
    return legend.getTokenTypes().indexOf(type);
  }

  /**
   * Вычислить битовую маску модификаторов.
   *
   * @param modifiers Модификаторы токена (из SemanticTokenModifiers)
   * @return Битовая маска модификаторов
   */
  public int computeModifierMask(String... modifiers) {
    var modifierMask = 0;
    for (String mod : modifiers) {
      int idx = legend.getTokenModifiers().indexOf(mod);
      if (idx >= 0) {
        modifierMask |= (1 << idx);
      }
    }
    return modifierMask;
  }
}

