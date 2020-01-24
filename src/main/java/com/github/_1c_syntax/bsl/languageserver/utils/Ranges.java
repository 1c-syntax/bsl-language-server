/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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
package com.github._1c_syntax.bsl.languageserver.utils;

import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

public final class Ranges {

  private Ranges() {
    // Utility class
  }

  public static Range create(int startLine, int startChar, int endLine, int endChar) {
    return new Range(new Position(startLine, startChar), new Position(endLine, endChar));
  }

  public static Range create(ParserRuleContext ruleContext) {
    return create(ruleContext.getStart(), ruleContext.getStop());
  }

  public static Range create(RegionSymbol regionSymbol) {
    return create(regionSymbol.getStartNode().getStart(), regionSymbol.getEndNode().getStop());
  }

  public static Range create(Token startToken, Token endToken) {
    int startLine = startToken.getLine() - 1;
    int startChar = startToken.getCharPositionInLine();
    int endLine = endToken.getLine() - 1;
    int endChar;
    if (endToken.getType() == Token.EOF) {
      endChar = endToken.getCharPositionInLine();
    } else {
      endChar = endToken.getCharPositionInLine() + endToken.getText().length();
    }

    return create(startLine, startChar, endLine, endChar);
  }

  public static Range create(TerminalNode terminalNode) {
    return create(terminalNode.getSymbol());
  }

  public static Range create(TerminalNode startTerminalNode, TerminalNode stopTerminalNode) {
    return create(startTerminalNode.getSymbol(), stopTerminalNode.getSymbol());
  }

  public static Range create(Token token) {
    int startLine = token.getLine() - 1;
    int startChar = token.getCharPositionInLine();
    int endLine = token.getLine() - 1;
    int endChar = token.getCharPositionInLine() + token.getText().length();

    return create(startLine, startChar, endLine, endChar);
  }

  public static boolean containsRange(Range bigger, Range smaller) {
    return org.eclipse.lsp4j.util.Ranges.containsRange(bigger, smaller);
  }

  public static boolean containsPosition(Range range, Position position) {
    return org.eclipse.lsp4j.util.Ranges.containsPosition(range, position);
  }
}
