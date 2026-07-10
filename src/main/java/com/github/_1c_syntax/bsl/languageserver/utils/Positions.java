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
package com.github._1c_syntax.bsl.languageserver.utils;

import lombok.experimental.UtilityClass;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Position;
import org.jspecify.annotations.Nullable;

/**
 * Набор методов для удобства работы с позициями (LSP {@link Position}).
 * <p>
 * ANTLR-координаты токена ({@code line} 1-based, {@code charPositionInLine})
 * приводятся к LSP-модели ({@code line} 0-based) в одном месте, без промежуточного
 * {@link org.eclipse.lsp4j.Range}.
 */
@UtilityClass
public final class Positions {

  /**
   * Создать позицию по координатам LSP.
   *
   * @param line      строка (0-based)
   * @param character символ
   * @return созданная позиция
   */
  public Position create(int line, int character) {
    return new Position(line, character);
  }

  /**
   * Начальная позиция токена.
   *
   * @param token токен
   * @return позиция начала токена
   */
  public Position create(Token token) {
    return create(token.getLine() - 1, token.getCharPositionInLine());
  }

  /**
   * Начальная позиция терминального узла.
   *
   * @param terminalNode терминальный узел; {@code null} трактуется как пустая позиция (0,0)
   * @return позиция начала узла
   */
  public Position create(@Nullable TerminalNode terminalNode) {
    return terminalNode == null ? create(0, 0) : create(terminalNode.getSymbol());
  }

  /**
   * Начальная позиция контекста правила парсера (по стартовому токену).
   *
   * @param ruleContext контекст правила
   * @return позиция начала контекста
   */
  public Position create(ParserRuleContext ruleContext) {
    return create(ruleContext.getStart());
  }

  /**
   * Конечная позиция токена. Соответствует концу {@link Ranges#create(Token)}:
   * {@code charPositionInLine + длина текста токена}.
   *
   * @param token токен
   * @return позиция конца токена
   */
  public Position createEnd(Token token) {
    return create(token.getLine() - 1, token.getCharPositionInLine() + token.getText().length());
  }

  /**
   * Конечная позиция терминального узла.
   *
   * @param terminalNode терминальный узел; {@code null} трактуется как пустая позиция (0,0)
   * @return позиция конца узла
   */
  public Position createEnd(@Nullable TerminalNode terminalNode) {
    return terminalNode == null ? create(0, 0) : createEnd(terminalNode.getSymbol());
  }
}
