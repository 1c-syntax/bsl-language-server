/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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
package org.github._1c_syntax.bsl.languageserver.utils;

import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.github._1c_syntax.bsl.parser.BSLParserRuleContext;

public final class RangeHelper {

  private RangeHelper() {
    // Utility class
  }

  public static Range newRange(int startLine, int startChar, int endLine, int endChar) {
    return new Range(new Position(startLine, startChar), new Position(endLine, endChar));
  }

  public static Range newRange(BSLParserRuleContext ruleContext) {
    return newRange(ruleContext.getStart(), ruleContext.getStop());
  }

  public static Range newRange(Token startToken, Token endToken) {
    int startLine = startToken.getLine() - 1;
    int startChar = startToken.getCharPositionInLine();
    int endLine = endToken.getLine() - 1;
    int endChar;
    if (endToken.getType() == Token.EOF) {
      endChar = 0;
    } else {
      endChar = endToken.getCharPositionInLine() + endToken.getText().length();
    }

    return newRange(startLine, startChar, endLine, endChar);
  }

  public static Range newRange(Token token) {
    int startLine = token.getLine() - 1;
    int startChar = token.getCharPositionInLine();
    int endLine = token.getLine() - 1;
    int endChar = token.getCharPositionInLine() + token.getText().length();

    return newRange(startLine, startChar, endLine, endChar);
  }
}
