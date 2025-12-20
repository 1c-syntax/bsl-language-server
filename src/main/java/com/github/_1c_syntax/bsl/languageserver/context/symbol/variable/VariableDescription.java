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
package com.github._1c_syntax.bsl.languageserver.context.symbol.variable;

import com.github._1c_syntax.bsl.languageserver.context.symbol.description.DescriptionReader;
import com.github._1c_syntax.bsl.languageserver.context.symbol.description.ParameterDescription;
import com.github._1c_syntax.bsl.languageserver.context.symbol.description.SourceDefinedSymbolDescription;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLMethodDescriptionTokenizer;
import lombok.Value;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Range;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Класс-описание переменной.
 */
@Value
public class VariableDescription implements SourceDefinedSymbolDescription {
  /**
   * Содержит полное описание переменной (весь текст)
   */
  String description;

  /**
   * Содержит часть строки после ключевого слова, в которой должно быть
   * описание причины устаревания переменной либо альтернативы
   */
  String deprecationInfo;

  /**
   * Признак устаревания переменной
   */
  boolean deprecated;

  /**
   * Описание назначения переменной
   */
  String purposeDescription;

  /**
   * Если описание содержит только ссылку, то здесь будет ее значение
   * <p>
   * TODO Временное решение, надо будет продумать в следующем релизе
   */
  String link;

  /**
   * Диапазон, в котором располагается описание.
   */
  Range range;

  Optional<VariableDescription> trailingDescription;

  public VariableDescription(List<Token> comments) {
    this(comments, Optional.empty());
  }

  public VariableDescription(List<Token> comments, Optional<Token> trailingComment) {
    description = comments.stream()
      .map(Token::getText)
      .collect(Collectors.joining("\n"));

    var tokenizer = new BSLMethodDescriptionTokenizer(description);
    var ast = requireNonNull(tokenizer.getAst());

    range = Ranges.create(comments);
    purposeDescription = DescriptionReader.readPurposeDescription(ast);
    link = DescriptionReader.readLink(ast);
    deprecated = ast.deprecate() != null;
    deprecationInfo = DescriptionReader.readDeprecationInfo(ast);
    trailingDescription = trailingComment.map(List::of).map(VariableDescription::new);
  }

  public VariableDescription(ParameterDescription param) {
    description = "";
    deprecationInfo = "";
    deprecated = false;
    purposeDescription = "";
    range = Ranges.create();
    link = param.link();
    trailingDescription = Optional.empty();
  }
}
