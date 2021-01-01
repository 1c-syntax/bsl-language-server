/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
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
import com.github._1c_syntax.bsl.parser.BSLMethodDescriptionTokenizer;
import lombok.Value;
import org.antlr.v4.runtime.Token;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Класс-описание метода (процедуры или функции)
 */
@Value
public class MethodDescription {

  /**
   * Номер первой строки описания
   */
  int startLine;
  /**
   * Номер последней строки описания
   */
  int endLine;
  /**
   * Содержит полное описание метода (весь текст)
   */
  String description;
  /**
   * Содержит часть строки после ключевого слова, в которой должно быть
   * описание причины устаревания метода либо альтернативы
   */
  String deprecationInfo;

  /**
   * Признак устарения метода
   */
  boolean deprecated;
  /**
   * Описание назначения метода
   */
  String purposeDescription;
  /**
   * Примеры использования метода
   */
  List<String> examples;
  /**
   * Варианты вызова метода
   */
  List<String> callOptions;
  /**
   * Параметры метода с типами и описанием
   */
  List<ParameterDescription> parameters;
  /**
   * Возвращаемые значения (типы)
   */
  List<TypeDescription> returnedValue;
  /**
   * Если описание содержит только ссылку, то здесь будет ее значение
   * <p>
   * TODO Временное решение, надо будет продумать в следующем релизе
   */
  String link;

  public MethodDescription(List<Token> comments) {
    description = comments.stream()
      .map(Token::getText)
      .collect(Collectors.joining("\n"));

    var tokenizer = new BSLMethodDescriptionTokenizer(description);
    var ast = requireNonNull(tokenizer.getAst());

    purposeDescription = DescriptionReader.readPurposeDescription(ast);
    link = DescriptionReader.readLink(ast);
    deprecated = ast.deprecate() != null;
    deprecationInfo = DescriptionReader.readDeprecationInfo(ast);
    callOptions = DescriptionReader.readCallOptions(ast);
    examples = DescriptionReader.readExamples(ast);
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
