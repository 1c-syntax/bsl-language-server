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
package com.github._1c_syntax.bsl.languageserver.configuration.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import io.leangen.geantyref.AnnotationFormatException;
import io.leangen.geantyref.TypeFactory;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Частичное переопределение метаданных диагностики, заданное в конфигурации.
 * <p>
 * Хранит только явно указанные атрибуты {@link DiagnosticMetadata}. При наложении на метаданные
 * диагностики ({@link #applyTo(DiagnosticMetadata)}) переопределяются только эти атрибуты,
 * остальные сохраняют значения исходных метаданных.
 */
@EqualsAndHashCode
@ToString
public final class DiagnosticMetadataOverride {

  private static final Method[] ATTRIBUTES = DiagnosticMetadata.class.getDeclaredMethods();
  private static final Set<String> ATTRIBUTE_NAMES = Arrays.stream(ATTRIBUTES)
    .map(Method::getName)
    .collect(Collectors.toUnmodifiableSet());

  /**
   * Имена атрибутов {@link DiagnosticMetadata}, явно заданных в переопределении.
   */
  @Getter
  private final Set<String> overriddenAttributes;

  /**
   * Значения переопределения. Атрибуты, не входящие в {@link #overriddenAttributes},
   * содержат значения по умолчанию аннотации и при наложении не используются.
   */
  private final DiagnosticMetadata values;

  /**
   * Создать переопределение из значений атрибутов.
   *
   * @param attributes Значения атрибутов {@link DiagnosticMetadata} в виде «имя атрибута → значение».
   *                   Значения должны иметь типы атрибутов аннотации (перечисления, массивы перечислений,
   *                   примитивы, строки). Ключи, не являющиеся атрибутами аннотации, игнорируются.
   * @throws IllegalArgumentException если значение атрибута не соответствует его типу
   */
  public DiagnosticMetadataOverride(Map<String, Object> attributes) {
    var knownAttributes = new HashMap<String, Object>();
    attributes.forEach((name, value) -> {
      if (ATTRIBUTE_NAMES.contains(name)) {
        knownAttributes.put(name, value);
      }
    });

    this.values = createMetadata(knownAttributes);
    this.overriddenAttributes = Set.copyOf(knownAttributes.keySet());
  }

  /**
   * Наложить переопределение на метаданные.
   *
   * @param base Исходные метаданные диагностики.
   * @return Метаданные, в которых явно заданные в переопределении атрибуты взяты из него,
   * а остальные — из {@code base}.
   */
  public DiagnosticMetadata applyTo(DiagnosticMetadata base) {
    if (overriddenAttributes.isEmpty()) {
      return base;
    }

    var merged = new HashMap<String, Object>();
    for (var attribute : ATTRIBUTES) {
      var source = overriddenAttributes.contains(attribute.getName()) ? values : base;
      merged.put(attribute.getName(), readAttribute(attribute, source));
    }
    return createMetadata(merged);
  }

  private static DiagnosticMetadata createMetadata(Map<String, Object> attributes) {
    try {
      return TypeFactory.annotation(DiagnosticMetadata.class, attributes);
    } catch (AnnotationFormatException e) {
      throw new IllegalArgumentException("Invalid diagnostic metadata attributes: " + attributes, e);
    }
  }

  private static Object readAttribute(Method attribute, DiagnosticMetadata metadata) {
    try {
      return attribute.invoke(metadata);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException("Can't read diagnostic metadata attribute " + attribute.getName(), e);
    }
  }
}
