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
package com.github._1c_syntax.bsl.languageserver.context.symbol;

import com.github._1c_syntax.bsl.languageserver.context.symbol.description.DescriptionReader;
import com.github._1c_syntax.bsl.languageserver.context.symbol.description.ParameterDescription;
import com.github._1c_syntax.bsl.languageserver.context.symbol.description.TypeDescription;
import com.github._1c_syntax.bsl.parser.BSLMethodDescriptionParser;
import com.github._1c_syntax.bsl.parser.BSLMethodDescriptionTokenizer;
import lombok.Getter;
import org.antlr.v4.runtime.Token;

import java.util.List;
import java.util.stream.Collectors;

public class MethodDescription {

  private final int startLine;
  private final int endLine;
  /**
   * Содержит полное описание метода (весь текст)
   */
  @Getter
  private final String description;

  /**
   * Содержит часть строки после ключевого слова, в которой должно быть
   * описание причины устаревания метода либо альтернативы
   */
  @Getter
  private final String deprecationInfo;
  /**
   * Признак устарения метода
   */
  @Getter
  private final boolean deprecated;

  /**
   * Описание назначения метода
   */
  @Getter
  private final String purposeDescription;
  /**
   * Примеры использования метода
   */
  @Getter
  private final List<String> examples;
  /**
   * Варианты вызова метода
   */
  @Getter
  private final List<String> callOptions;
  /**
   * Параметры метода с типами и описанием
   */
  @Getter
  private final List<ParameterDescription> parameters;
  /**
   * Возвращаемые значения (типы)
   */
  @Getter
  private final List<TypeDescription> returnedValue;

  public MethodDescription(List<Token> comments) {
    description = comments.stream()
      .map(Token::getText)
      .collect(Collectors.joining("\n"));

    var tokenizer = new BSLMethodDescriptionTokenizer(description);
    var ast = tokenizer.getAst();

    purposeDescription = DescriptionReader.readPurposeDescription(ast);
    deprecated = ast != null && ast.deprecate() != null;
    deprecationInfo = DescriptionReader.readDeprecationInfo(ast);
    callOptions = DescriptionReader.readExamples(ast, BSLMethodDescriptionParser.RULE_callOptionsString);
    examples = DescriptionReader.readExamples(ast, BSLMethodDescriptionParser.RULE_examplesString);
    parameters = DescriptionReader.readParameters(ast);
    returnedValue = DescriptionReader.readReturnedValue(ast);

    if (comments.isEmpty()) {
      startLine = 0;
      endLine = 0;
      return;
    }

    this.startLine = comments.get(0).getLine();
    this.endLine = comments.get(comments.size() - 1).getLine();
  }

  public boolean isEmpty() {
    return description.isEmpty();
  }

  public boolean contains(Token first, Token last) {
    int firstLine = first.getLine();
    int lastLine = last.getLine();
    return (firstLine >= startLine && lastLine <= endLine);
  }

}
