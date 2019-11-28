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
package com.github._1c_syntax.bsl.languageserver.diagnostics.metadata;

import org.reflections.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;

public final class DiagnosticParameterInfo {

  private final Class type;
  private final String name;
  private final String description;
  private final Object defaultValue;

  private DiagnosticParameterInfo(Field field, String description) {

    DiagnosticParameter diagnosticParameter = field.getAnnotation(DiagnosticParameter.class);
    this.type = diagnosticParameter.type();
    this.name = field.getName();
    this.description = description;
    this.defaultValue = castDiagnosticParameterValue(diagnosticParameter.defaultValue());

  }

  public Class getType() {
    return type;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return this.description;
  }

  public Object getDefaultValue() {
    return this.defaultValue;
  }

  private Object castDiagnosticParameterValue(String valueToCast) {
    Object value;
    if (type == Integer.class) {
      value = Integer.parseInt(valueToCast);
    } else if (type == Boolean.class) {
      value = Boolean.parseBoolean(valueToCast);
    } else if (type == Float.class) {
      value = Float.parseFloat(valueToCast);
    } else if (type == String.class) {
      value = valueToCast;
    } else {
      throw new IllegalArgumentException("Unsupported diagnostic parameter type " + type);
    }

    return value;
  }

  @SuppressWarnings("unchecked")
  static List<DiagnosticParameterInfo> createDiagnosticParameters(DiagnosticInfo diagnosticInfo) {
    return ReflectionUtils.getAllFields(
      diagnosticInfo.getDiagnosticClass(),
      ReflectionUtils.withAnnotation(DiagnosticParameter.class)
    ).stream()
      .map((Field field) -> new DiagnosticParameterInfo(field, diagnosticInfo.getResourceString(field.getName())))
      .collect(Collectors.toList());
  }
}
