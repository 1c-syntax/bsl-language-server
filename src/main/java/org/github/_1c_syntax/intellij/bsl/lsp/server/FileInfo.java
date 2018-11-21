package org.github._1c_syntax.intellij.bsl.lsp.server;

import org.antlr.v4.runtime.Token;
import org.github._1c_syntax.parser.BSLParser;

import java.util.List;

public class FileInfo {
  private final BSLParser.FileContext tree;
  private final List<Token> tokens;

  public FileInfo(BSLParser.FileContext tree, List<Token> tokens) {
    this.tree = tree;
    this.tokens = tokens;
  }

  public List<Token> getTokens() {
    return tokens;
  }

  public BSLParser.FileContext getTree() {
    return tree;
  }
}
