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

import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.description.ParameterDescription;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import org.eclipse.lsp4j.Range;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Класс хранит информацию о параметре метода.
 * См. {@link MethodSymbol}
 */
@Value
@Builder
public class ParameterDefinition {
  /**
   * Имя параметра.
   */
  String name;

  /**
   * Описание параметра.
   */
  Optional<ParameterDescription> description;

  /**
   * Значение по умолчанию.
   */
  DefaultValue defaultValue;

  @Builder.Default
  List<Annotation> annotations = Collections.emptyList();

  /**
   * Строка места объявления, а в знаке — способ передачи: у параметра «Знач» строка хранится
   * как {@code ~line}. Место — один токен, поэтому строка конца совпадает со строкой начала.
   */
  @Getter(AccessLevel.NONE)
  int signedLine;
  @Getter(AccessLevel.NONE)
  int startCharacter;
  @Getter(AccessLevel.NONE)
  int endCharacter;

  /**
   * Передача параметра по значению.
   */
  public boolean isByValue() {
    return signedLine < 0;
  }

  /**
   * Место объявления параметра.
   */
  public Range getRange() {
    var line = signedLine < 0 ? ~signedLine : signedLine;
    return Ranges.create(line, startCharacter, line, endCharacter);
  }

  public boolean isOptional() {
    return !DefaultValue.EMPTY.equals(defaultValue);
  }

  public static ParameterDefinitionBuilder builder() {
    return new ParameterDefinitionBuilder();
  }

  public enum ParameterType {
    DATETIME,
    BOOLEAN,
    UNDEFINED,
    NULL,
    STRING,
    NUMERIC,
    EMPTY
  }

  public record DefaultValue(ParameterType type, String value) {
      public static final DefaultValue EMPTY = new DefaultValue(ParameterType.EMPTY, "");
  }

  public static class ParameterDefinitionBuilder {

    private boolean byValue;
    private int line;

    public ParameterDefinitionBuilder byValue(boolean byValue) {
      this.byValue = byValue;
      signedLine = byValue ? ~line : line;
      return this;
    }

    /**
     * @param range место объявления — один токен, в пределах одной строки.
     * @return этот же построитель.
     */
    public ParameterDefinitionBuilder range(Range range) {
      var start = range.getStart();
      line = start.getLine();
      signedLine = byValue ? ~line : line;
      startCharacter = start.getCharacter();
      endCharacter = range.getEnd().getCharacter();

      return this;
    }
  }
}
