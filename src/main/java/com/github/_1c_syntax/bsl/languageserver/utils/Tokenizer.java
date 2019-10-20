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
package com.github._1c_syntax.bsl.languageserver.utils;

import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.UnicodeBOMInputStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;
import org.antlr.v4.runtime.Token;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.antlr.v4.runtime.Token.EOF;

public class Tokenizer {

  private String content;
  private Lazy<CommonTokenStream> tokenStream = new Lazy<>(this::computeTokenStream);
  private Lazy<List<Token>> tokens = new Lazy<>(this::computeTokens);

  public Tokenizer(String content) {
    this.content = content;
  }

  public List<Token> getTokens() {
    final List<Token> tokensUnboxed = tokens.getOrCompute();
    return new ArrayList<>(tokensUnboxed);
  }

  private List<Token> computeTokens() {
    List<Token> tokensTemp = new ArrayList<>(getTokenStream().getTokens());

    Token lastToken = tokensTemp.get(tokensTemp.size() - 1);
    if (lastToken.getType() == EOF) {
      tokensTemp.remove(tokensTemp.size() - 1);
    }

    return tokensTemp;
  }

  public CommonTokenStream getTokenStream() {
    final CommonTokenStream tokenStreamUnboxed = tokenStream.getOrCompute();
    tokenStreamUnboxed.seek(0);
    return tokenStreamUnboxed;
  }

  private CommonTokenStream computeTokenStream() {
    requireNonNull(content);

    CharStream input;

    try (InputStream inputStream = IOUtils.toInputStream(content, StandardCharsets.UTF_8);
         UnicodeBOMInputStream ubis = new UnicodeBOMInputStream(inputStream)
    ) {

      ubis.skipBOM();

      input = CharStreams.fromStream(ubis, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    BSLLexer lexer = new BSLLexer(input);

    lexer.setInputStream(input);
    lexer.removeErrorListener(ConsoleErrorListener.INSTANCE);

    CommonTokenStream tempTokenStream = new CommonTokenStream(lexer);
    tempTokenStream.fill();

    return tempTokenStream;
  }

  public void clear(){
    tokenStream.clear();
    tokens.clear();
  }

}
