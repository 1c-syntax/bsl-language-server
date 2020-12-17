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

import lombok.Builder;
import lombok.Value;
import org.eclipse.lsp4j.Range;

/**
 * Класс хранит информацию о параметре метода.
 * См. {@link MethodSymbol}
 */
@Value
@Builder
public class ParameterDefinition {
  String name;
  boolean byValue;
  DefaultValue defaultValue;
  Range range;

  public boolean isOptional() {
    return !DefaultValue.EMPTY.equals(defaultValue);
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

  @Value
  public static class DefaultValue {
    public static final DefaultValue EMPTY = new DefaultValue(ParameterType.EMPTY, "");

    ParameterType type;
    String value;

    public static DefaultValue datetime(String value) {
      return new DefaultValue(ParameterType.DATETIME, value);
    }


  }
}
