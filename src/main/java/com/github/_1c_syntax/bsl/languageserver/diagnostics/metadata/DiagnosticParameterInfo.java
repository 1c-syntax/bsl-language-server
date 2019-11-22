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

import com.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic;
import org.reflections.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.stream.Collectors;

public final class DiagnosticParameterInfo {

  private final Object defaultValue;
  private final Class type;
  private final String description;
  private final String name;

  private DiagnosticParameterInfo(Field field, DiagnosticInfo diagnosticInfo) {

    DiagnosticParameter diagnosticParameter = field.getAnnotation(DiagnosticParameter.class);
    this.type = diagnosticParameter.type();
    this.name = field.getName();
    this.description = diagnosticInfo.getResourceString(name);
    this.defaultValue = castDiagnosticParameterValue(diagnosticParameter.defaultValue());

  }

  public Object getDefaultValue() {
    return this.defaultValue;
  }

  public String getDescription() {
    return this.description;
  }

  public String getName() {
    return name;
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
  static Map<String, DiagnosticParameterInfo> createDiagnosticParameters(
    Class<? extends BSLDiagnostic> diagnosticClass,
    DiagnosticInfo diagnosticInfo
  ) {
    return ReflectionUtils.getAllFields(
      diagnosticClass,
      ReflectionUtils.withAnnotation(DiagnosticParameter.class)
    ).stream()
      .collect(Collectors.toMap(
        Field::getName,
        (Field field) -> new DiagnosticParameterInfo(field, diagnosticInfo)
      ));
  }
}
