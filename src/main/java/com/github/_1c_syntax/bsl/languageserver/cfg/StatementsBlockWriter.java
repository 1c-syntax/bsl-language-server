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

import lombok.Getter;
import org.antlr.v4.runtime.ParserRuleContext;
import org.jspecify.annotations.NullUnmarked;

import java.util.ArrayDeque;
import java.util.Deque;

@NullUnmarked
class StatementsBlockWriter {

  static class JumpInformationRecord {
    CfgVertex methodReturn;
    CfgVertex loopContinue;
    CfgVertex loopBreak;
    CfgVertex exceptionHandler;
  }

  static class StatementsBlockRecord {

    private CfgVertex subgraphBegin;
    private CfgVertex subgraphEnd;

    private BasicBlockVertex statements = new BasicBlockVertex();
    private final Deque<CfgVertex> buildStack = new ArrayDeque<>();

    @Getter
    private final JumpInformationRecord jumpContext;

    public StatementsBlockRecord() {
      this(new JumpInformationRecord());
    }

    public StatementsBlockRecord(JumpInformationRecord jumpInfo) {
      jumpContext = jumpInfo;
      subgraphBegin = statements;
      subgraphEnd = statements;
    }

    public void add(ParserRuleContext statement) {
      statements.addStatement(statement);
    }

    public Deque<CfgVertex> getBuildParts() {
      return buildStack;
    }

    public void split() {
      if (subgraphBegin instanceof BasicBlockVertex && subgraphBegin == subgraphEnd) {
        subgraphBegin = statements;
      }

      statements = new BasicBlockVertex();
      subgraphEnd = statements;
    }

    public CfgVertex begin() {
      return subgraphBegin;
    }

    public CfgVertex end() {
      return subgraphEnd;
    }

    public void replaceEnd(CfgVertex vertex) {
      if ((subgraphBegin == subgraphEnd)) {
        subgraphBegin = vertex;
      }

      subgraphEnd = vertex;
    }
  }

  private final Deque<StatementsBlockRecord> blocks = new ArrayDeque<>();

  public int size() {
    return blocks.size();
  }

  public StatementsBlockRecord enterBlock() {
    return enterBlock(new JumpInformationRecord());
  }

  public StatementsBlockRecord enterBlock(JumpInformationRecord newJumpStates) {
    var current = getCurrentBlock();

    if (current != null) {

      if (newJumpStates.methodReturn == null) {
        newJumpStates.methodReturn = current.jumpContext.methodReturn;
      }

      if (newJumpStates.loopBreak == null) {
        newJumpStates.loopBreak = current.jumpContext.loopBreak;
      }

      if (newJumpStates.loopContinue == null) {
        newJumpStates.loopContinue = current.jumpContext.loopContinue;
      }

      if (newJumpStates.exceptionHandler == null) {
        newJumpStates.exceptionHandler = current.jumpContext.exceptionHandler;
      }
    }

    var block = new StatementsBlockRecord(newJumpStates);
    blocks.push(block);
    return block;
  }

  public StatementsBlockRecord leaveBlock() {
    return blocks.pop();
  }

  public StatementsBlockRecord getCurrentBlock() {
    return blocks.peek();
  }

  public void addStatement(ParserRuleContext statement) {
    getCurrentBlock().add(statement);
  }

}
