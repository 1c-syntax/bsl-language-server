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
package com.github._1c_syntax.bsl.languageserver.cfg;

import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import lombok.EqualsAndHashCode;

import java.util.Optional;

@EqualsAndHashCode(callSuper = true)
public class ConditionalVertex extends BranchingVertex {
  private final BSLParserRuleContext ast;

  public ConditionalVertex(BSLParser.IfBranchContext ctx) {
    ast = ctx;
  }

  public ConditionalVertex(BSLParser.ElsifBranchContext ctx) {
    ast = ctx;
  }

  public BSLParser.ExpressionContext getExpression() {
    if (ast instanceof BSLParser.IfBranchContext) {
      return ((BSLParser.IfBranchContext) ast).expression();
    } else if (ast instanceof BSLParser.ElsifBranchContext) {
      return ((BSLParser.ElsifBranchContext) ast).expression();
    }

    throw new IllegalStateException();
  }

  @Override
  public Optional<BSLParserRuleContext> getAst() {
    return Optional.of(ast);
  }

  @Override
  protected void onConnectOutgoing(ControlFlowGraph graph, CfgVertex target, CfgEdge edge) {
    super.onConnectOutgoing(graph, target, edge);

    if (edge.getType() != CfgEdgeType.TRUE_BRANCH && edge.getType() != CfgEdgeType.FALSE_BRANCH) {
      throw new FlowGraphLinkException("Can't add edge " + this + "-> " + target + "\n"
        +"Edge type " + edge.getType() + " is forbidden here.");
    }
  }
}
