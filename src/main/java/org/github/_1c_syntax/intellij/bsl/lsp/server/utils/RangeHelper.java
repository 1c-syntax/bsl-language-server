package org.github._1c_syntax.intellij.bsl.lsp.server.utils;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

public class RangeHelper {
  public static Range newRange(int startLine, int startChar, int endLine, int endChar) {
    return new Range(new Position(startLine, startChar), new Position(endLine, endChar));
  }
}
