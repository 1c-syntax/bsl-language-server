/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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

  public Range create() {
    return create(0, 0, 0, 0);
  }

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

  public Range create(ParserRuleContext ruleContext) {
    return create(ruleContext.getStart(), ruleContext.getStop());
  }

  public Range create(ParserRuleContext startCtx, ParserRuleContext endCtx) {
    return create(startCtx.getStart(), endCtx.getStop());
  }

  public Range create(Token startToken, Token endToken) {
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

  public Range create(List<Token> tokens) {
    if (tokens.isEmpty()) {
      return Ranges.create();
    }
    var firstElement = tokens.get(0);
    var lastElement = tokens.get(tokens.size() - 1);

    return Ranges.create(firstElement, lastElement);
  }

  public Range create(TerminalNode terminalNode) {
    return create(terminalNode.getSymbol());
  }

  public Range create(TerminalNode startTerminalNode, TerminalNode stopTerminalNode) {
    return create(startTerminalNode.getSymbol(), stopTerminalNode.getSymbol());
  }

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
    return org.eclipse.lsp4j.util.Ranges.containsPosition(range, position);
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
   * @deprecated Для совместимости метод оставлен, но будет удален в будущих версиях.
   * Вместо него стоит использовать метод {@link ModuleSymbol#getSelectionRange()}
   */
  @Deprecated(since = "0.20")
  public Optional<Range> getFirstSignificantTokenRange(Collection<Token> tokens) {
    return tokens.stream()
      .filter(token -> token.getType() != Token.EOF)
      .filter(token -> token.getType() != BSLLexer.WHITE_SPACE)
      .map(Ranges::create)
      .filter(range -> (!range.getStart().equals(range.getEnd())))
      .findFirst();
  }

}
