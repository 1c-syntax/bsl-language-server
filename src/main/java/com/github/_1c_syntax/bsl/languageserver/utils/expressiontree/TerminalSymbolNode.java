/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
import org.antlr.v4.runtime.tree.TerminalNode;

public class TerminalSymbolNode extends BslExpression{
  private TerminalSymbolNode(ExpressionNodeType type) {
    super(type);
  }

  public static TerminalSymbolNode literal(BSLParser.ConstValueContext constant){
    var node = new TerminalSymbolNode(ExpressionNodeType.LITERAL);
    node.setRepresentingAst(constant);
    return node;
  }

  public static TerminalSymbolNode literal(TerminalNode constant){
    var node = new TerminalSymbolNode(ExpressionNodeType.LITERAL);
    node.setRepresentingAst(constant);
    return node;
  }

  public static TerminalSymbolNode identifier(TerminalNode identifier){
    var node = new TerminalSymbolNode(ExpressionNodeType.IDENTIFIER);
    node.setRepresentingAst(identifier);
    return node;
  }
}
