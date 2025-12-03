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
package com.github._1c_syntax.bsl.languageserver.utils.expressiontree;

import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Терминальный узел дерева выражений.
 * <p>
 * Представляет литералы (константы) и идентификаторы (имена переменных, методов):
 * числа, строки, булевы значения, Неопределено, имена переменных.
 */
public class TerminalSymbolNode extends BslExpression {
  private TerminalSymbolNode(ExpressionNodeType type, ParseTree representingAst) {
    super(type, representingAst, null);
  }

  /**
   * @param constant константа
   * @return терминал константы
   */
  public static TerminalSymbolNode literal(BSLParser.ConstValueContext constant) {
    return new TerminalSymbolNode(ExpressionNodeType.LITERAL, constant);
  }

  /**
   * @param constant константа
   * @return терминал константы
   */
  public static TerminalSymbolNode literal(TerminalNode constant) {
    return new TerminalSymbolNode(ExpressionNodeType.LITERAL, constant);
  }

  /**
   * @param identifier идентификатор
   * @return терминал идентификатора
   */
  public static TerminalSymbolNode identifier(TerminalNode identifier) {
    return new TerminalSymbolNode(ExpressionNodeType.IDENTIFIER, identifier);
  }
}
