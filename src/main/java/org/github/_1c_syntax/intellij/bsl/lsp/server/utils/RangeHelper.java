package org.github._1c_syntax.intellij.bsl.lsp.server.utils;

import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

public class RangeHelper {
  public static Range newRange(int startLine, int startChar, int endLine, int endChar) {
    return new Range(new Position(startLine, startChar), new Position(endLine, endChar));
  }

  public static Range newRange(Token startToken, Token endToken) {
    int startLine = startToken.getLine() - 1;
    int startChar = startToken.getCharPositionInLine();
    int endLine = endToken.getLine() - 1;
    int endChar = endToken.getCharPositionInLine();

    if (startToken.equals(endToken)) {
      endChar += endToken.getText().length();
    }

    return newRange(startLine, startChar, endLine, endChar);
  }
}
