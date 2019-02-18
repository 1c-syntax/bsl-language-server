/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com>
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
package org.github._1c_syntax.bsl.languageserver.context;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.github._1c_syntax.bsl.parser.BSLLexer;
import org.github._1c_syntax.bsl.parser.BSLParser;

import java.util.List;

public class DocumentContext {

  private BSLParser.FileContext ast;
  private List<Token> tokens;
  private String uri;

  public DocumentContext(String uri, String content) {
    this.uri = uri;
    build(content);
  }

  public BSLParser.FileContext getAst() {
    return ast;
  }

  public List<Token> getTokens() {
    return tokens;
  }

  public String getUri() {
    return uri;
  }

  public void rebuild(String content) {
    build(content);
  }

  private void build(String content) {
    CharStream input = CharStreams.fromString(content);

    BSLLexer lexer = new BSLLexer(input);

    lexer.setInputStream(input);

    CommonTokenStream tokenStream = new CommonTokenStream(lexer);
    tokenStream.fill();

    tokens = tokenStream.getTokens();

    BSLParser parser = new BSLParser(tokenStream);
    ast = parser.file();
  }
}
