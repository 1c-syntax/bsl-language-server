/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
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
package com.github._1c_syntax.bsl.languageserver.context.symbol;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.parser.description.VariableDescription;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.util.List;
import java.util.Optional;

/**
 * Реализация символа переменной, дополнительно хранящая аннотации объявления.
 * <p>
 * Создаётся {@link AbstractVariableSymbol.Builder} только когда у переменной
 * реально есть аннотации (переменные уровня модуля во фреймворке ОСень). Для
 * остальных переменных используется {@link ShortBasedVariableSymbol} /
 * {@link IntBasedVariableSymbol} без лишней ссылки в layout объекта.
 */
@Value
@NonFinal
@ToString(callSuper = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
public class AnnotatedVariableSymbol extends IntBasedVariableSymbol {

  @Getter
  List<Annotation> annotations;

  @SuppressWarnings("java:S107")
  public AnnotatedVariableSymbol(
    String name,
    SourceDefinedSymbol scope,
    DocumentContext owner,
    Optional<SourceDefinedSymbol> parent,
    byte kind,
    boolean export,
    Optional<VariableDescription> description,
    int startLine,
    int startCharacter,
    int endLine,
    int endCharacter,
    int variableNameLine,
    int variableNameStartCharacter,
    int variableNameEndCharacter,
    List<Annotation> annotations
  ) {
    super(
      name,
      scope,
      owner,
      parent,
      kind,
      export,
      description,
      startLine,
      startCharacter,
      endLine,
      endCharacter,
      variableNameLine,
      variableNameStartCharacter,
      variableNameEndCharacter
    );
    this.annotations = annotations;
  }
}
