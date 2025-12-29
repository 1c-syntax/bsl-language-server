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
package com.github._1c_syntax.bsl.languageserver.utils;

import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import lombok.experimental.UtilityClass;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.util.Preconditions;
import org.eclipse.lsp4j.util.Positions;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Набор методов для удобства работы с областями текста (ренджами)
 */
@UtilityClass
public final class Ranges {

  /**
   * Проверяет переданную область на пустоту
   *
   * @param range Проверяемая область
   * @return Признак пустоты
   */
  public boolean isEmpty(Range range) {
    return create().equals(range);
  }

  /**
   * Создать пустой диапазон (0,0,0,0).
   *
   * @return Пустой диапазон
   */
  public Range create() {
    return create(0, 0, 0, 0);
  }

  /**
   * Создать диапазон с указанными координатами.
   *
   * @param startLine Начальная строка
   * @param startChar Начальный символ
   * @param endLine Конечная строка
   * @param endChar Конечный символ
   * @return Созданный диапазон
   */
  public Range create(int startLine, int startChar, int endLine, int endChar) {
    return new Range(new Position(startLine, startChar), new Position(endLine, endChar));
  }

  /**
   * Создание Range для линии
   *
   * @param lineNo    - номер строки
   * @param startChar - номер первого символа
   * @param endChar   - номер последнего символа
   * @return - полученный Range
   */
  public Range create(int lineNo, int startChar, int endChar) {
    return new Range(new Position(lineNo, startChar), new Position(lineNo, endChar));
  }

  /**
   * Создать диапазон из контекста правила парсера.
   *
   * @param ruleContext Контекст правила
   * @return Диапазон, покрывающий весь контекст
   */
  public Range create(ParserRuleContext ruleContext) {
    return create(ruleContext.getStart(), ruleContext.getStop());
  }

  /**
   * Создать диапазон от начала одного контекста до конца другого.
   *
   * @param startCtx Начальный контекст
   * @param endCtx Конечный контекст
   * @return Диапазон между контекстами
   */
  public Range create(ParserRuleContext startCtx, ParserRuleContext endCtx) {
    return create(startCtx.getStart(), endCtx.getStop());
  }

  /**
   * Создать диапазон из токенов.
   *
   * @param startToken Начальный токен
   * @param endToken Конечный токен
   * @return Диапазон между токенами
   */
  public Range create(Token startToken, @Nullable Token endToken) {
    int startLine = startToken.getLine() - 1;
    int startChar = startToken.getCharPositionInLine();
    var tokenToCalculateEnd = endToken == null ? startToken : endToken;
    int endLine = tokenToCalculateEnd.getLine() - 1;
    int endChar;
    if (tokenToCalculateEnd.getType() == Token.EOF) {
      endChar = tokenToCalculateEnd.getCharPositionInLine();
    } else {
      endChar = tokenToCalculateEnd.getCharPositionInLine() + tokenToCalculateEnd.getText().length();
    }

    return create(startLine, startChar, endLine, endChar);
  }

  /**
   * Создать диапазон из списка токенов.
   *
   * @param tokens Список токенов
   * @return Диапазон от первого до последнего токена
   */
  public Range create(List<Token> tokens) {
    if (tokens.isEmpty()) {
      return Ranges.create();
    }
    var firstElement = tokens.get(0);
    var lastElement = tokens.get(tokens.size() - 1);

    return Ranges.create(firstElement, lastElement);
  }

  /**
   * Создать диапазон из терминального узла.
   *
   * @param terminalNode Терминальный узел
   * @return Диапазон узла
   */
  public Range create(TerminalNode terminalNode) {
    return create(terminalNode.getSymbol());
  }

  /**
   * Создать диапазон между двумя терминальными узлами.
   *
   * @param startTerminalNode Начальный узел
   * @param stopTerminalNode Конечный узел
   * @return Диапазон между узлами
   */
  public Range create(TerminalNode startTerminalNode, TerminalNode stopTerminalNode) {
    return create(startTerminalNode.getSymbol(), stopTerminalNode.getSymbol());
  }

  /**
   * Создать диапазон из токена.
   *
   * @param token Токен
   * @return Диапазон токена
   */
  public Range create(Token token) {
    int startLine = token.getLine() - 1;
    int startChar = token.getCharPositionInLine();
    int endLine = token.getLine() - 1;
    int endChar = token.getCharPositionInLine() + token.getText().length();

    return create(startLine, startChar, endLine, endChar);
  }

  /**
   * Создание Range для узла дерева разбора.
   *
   * @param tree - дерево разбора.
   * @return - полученный Range.
   */
  public Range create(ParseTree tree) {
    if (tree instanceof TerminalNode) {
      return Ranges.create((TerminalNode) tree);
    } else if (tree instanceof Token) {
      return Ranges.create((Token) tree);
    } else if (tree instanceof ParserRuleContext) {
      return Ranges.create((ParserRuleContext) tree);
    } else {
      throw new IllegalArgumentException();
    }
  }

  public boolean containsRange(Range bigger, Range smaller) {
    return org.eclipse.lsp4j.util.Ranges.containsRange(bigger, smaller);
  }

  public boolean containsPosition(Range range, Position position) {
    Preconditions.checkNotNull(range, "range");
    Preconditions.checkNotNull(position, "position");
    return range.getStart().equals(position)
      || (Positions.isBefore(range.getStart(), position)
        && Positions.isBefore(position, range.getEnd()));
  }

  /**
   * Проверяет, содержит ли диапазон указанную позицию.
   *
   * @param startLine      - начальная строка диапазона
   * @param startCharacter - начальный символ диапазона
   * @param endLine        - конечная строка диапазона
   * @param endCharacter   - конечный символ диапазона
   * @param position       - позиция для проверки
   * @return true, если позиция находится внутри диапазона (включая начало, исключая конец)
   */
  public boolean containsPosition(
    int startLine, int startCharacter, int endLine, int endCharacter,
    Position position
  ) {
    return containsPosition(startLine, startCharacter, endLine, endCharacter,
      position.getLine(), position.getCharacter());
  }

  /**
   * Проверяет, содержит ли диапазон указанную позицию.
   *
   * @param startLine      - начальная строка диапазона
   * @param startCharacter - начальный символ диапазона
   * @param endLine        - конечная строка диапазона
   * @param endCharacter   - конечный символ диапазона
   * @param line           - строка позиции
   * @param character      - символ позиции
   * @return true, если позиция находится внутри диапазона (включая начало, исключая конец)
   */
  public boolean containsPosition(
    int startLine, int startCharacter, int endLine, int endCharacter,
    int line, int character
  ) {
    // Позиция равна началу диапазона
    if (line == startLine && character == startCharacter) {
      return true;
    }
    // Позиция после начала и до конца
    return isBefore(startLine, startCharacter, line, character)
      && isBefore(line, character, endLine, endCharacter);
  }

  /**
   * Проверяет, что первая позиция строго раньше второй.
   *
   * @param line1      - строка первой позиции
   * @param character1 - символ первой позиции
   * @param line2      - строка второй позиции
   * @param character2 - символ второй позиции
   * @return true, если первая позиция строго раньше второй
   */
  private boolean isBefore(int line1, int character1, int line2, int character2) {
    if (line1 < line2) {
      return true;
    }
    if (line1 > line2) {
      return false;
    }
    return character1 < character2;
  }



  /**
   * Натуральный порядок сравнения Range
   *
   * @param o1 - левый\меньший операнд
   * @param o2 - правый\больший операнд
   * @return 0 - равно, 1 - больше, -1 - меньше
   */
  public int compare(Range o1, Range o2) {
    if (o1.equals(o2)){
      return 0;
    }
    final var startCompare = compare(o1.getStart(), o2.getStart());
    if (startCompare != 0){
      return startCompare;
    }
    return compare(o1.getEnd(), o2.getEnd());
  }

  /**
   * Натуральный порядок сравнения Position
   *
   * @param pos1 - левый\меньший операнд
   * @param pos2 - правый\больший операнд
   * @return 0 - равно, 1 - больше, -1 - меньше
   */
  public int compare(Position pos1, Position pos2) {
    if (pos1.equals(pos2)){
      return 0;
    }

    // 1,1 10,10
    if (pos1.getLine() < pos2.getLine()) {
      return -1;
    }
    // 10,10 1,1
    if (pos1.getLine() > pos2.getLine()) {
      return 1;
    }
    // 1,4 1,9
    return Integer.compare(pos1.getCharacter(), pos2.getCharacter());
    // 1,9 1,4
  }

  /**
   * Натуральный порядок сравнения двух диапазонов, заданных деконструированными координатами.
   *
   * @param startLine1 - начальная строка первого диапазона
   * @param startChar1 - начальный символ первого диапазона
   * @param endLine1   - конечная строка первого диапазона
   * @param endChar1   - конечный символ первого диапазона
   * @param startLine2 - начальная строка второго диапазона
   * @param startChar2 - начальный символ второго диапазона
   * @param endLine2   - конечная строка второго диапазона
   * @param endChar2   - конечный символ второго диапазона
   * @return 0 - равно, 1 - больше, -1 - меньше
   */
  public int compare(
    int startLine1, int startChar1, int endLine1, int endChar1,
    int startLine2, int startChar2, int endLine2, int endChar2
  ) {
    // Сравнение начальных позиций
    if (startLine1 != startLine2) {
      return Integer.compare(startLine1, startLine2);
    }
    if (startChar1 != startChar2) {
      return Integer.compare(startChar1, startChar2);
    }
    // Сравнение конечных позиций
    if (endLine1 != endLine2) {
      return Integer.compare(endLine1, endLine2);
    }
    return Integer.compare(endChar1, endChar2);
  }

}
