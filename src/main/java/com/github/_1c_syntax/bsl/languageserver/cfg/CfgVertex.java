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
package com.github._1c_syntax.bsl.languageserver.cfg;

import org.antlr.v4.runtime.ParserRuleContext;

import java.util.Optional;

public abstract class CfgVertex {

  private boolean isConnected;

  public Optional<ParserRuleContext> getAst() {
    return Optional.empty();
  }

  @Override
  public String toString() {
    return getAst().map(
        ast -> getClass().getSimpleName() +
          "{" + ast.getStart().getLine() + ":" + ast.getStop().getLine() + "}")
      .orElseGet(() -> getClass().getSimpleName());
  }

  protected void onConnectOutgoing(ControlFlowGraph graph, CfgVertex target, CfgEdge edge) {
    if (!isConnected) {
      // Шорткат, чтобы не ходить для новых нод в граф
      isConnected = true;
      return;
    }

    graph.outgoingEdgesOf(this).stream()
      .filter(existing -> existing.getType() == edge.getType())
      .findAny()
      .ifPresent((CfgEdge existing) -> {
        throw duplicateLinkError(graph, target, existing);
      });
  }

  private FlowGraphLinkException duplicateLinkError(ControlFlowGraph graph, CfgVertex target, CfgEdge edge) {
    throw new FlowGraphLinkException("Can't add edge " + this + "->" + target + "\n"
      + "Source vertex " + this + " already has " + edge.getType() + " edge " + graph.edgePresentation(edge));
  }
}
