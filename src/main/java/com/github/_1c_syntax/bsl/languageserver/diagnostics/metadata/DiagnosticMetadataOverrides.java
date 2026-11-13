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
package com.github._1c_syntax.bsl.languageserver.diagnostics.metadata;

import io.leangen.geantyref.AnnotationFormatException;
import io.leangen.geantyref.TypeFactory;
import lombok.experimental.UtilityClass;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Переопределения метаданных диагностик, заданные в конфигурации.
 * <p>
 * Переопределение представлено экземпляром {@link DiagnosticMetadata}, который дополнительно
 * помнит, какие атрибуты были заданы явно. При наложении на метаданные диагностики
 * ({@link #applyTo(DiagnosticMetadata, DiagnosticMetadata)}) переопределяются только они,
 * остальные атрибуты сохраняют значения исходных метаданных.
 */
@UtilityClass
public class DiagnosticMetadataOverrides {

  private static final Method[] ATTRIBUTES = DiagnosticMetadata.class.getDeclaredMethods();
  private static final Set<String> ATTRIBUTE_NAMES = Arrays.stream(ATTRIBUTES)
    .map(Method::getName)
    .collect(Collectors.toUnmodifiableSet());

  /**
   * Создать переопределение метаданных из значений атрибутов.
   *
   * @param attributes Значения атрибутов {@link DiagnosticMetadata} в виде «имя атрибута → значение».
   *                   Значения должны иметь типы атрибутов аннотации (перечисления, массивы перечислений,
   *                   примитивы, строки). Ключи, не являющиеся атрибутами аннотации, игнорируются.
   * @return Метаданные, в которых явно заданы только переданные атрибуты.
   * Остальные атрибуты возвращают значения по умолчанию аннотации.
   * @throws IllegalArgumentException если значение атрибута не соответствует его типу
   */
  public static DiagnosticMetadata create(Map<String, Object> attributes) {
    var specifiedAttributes = new HashMap<String, Object>();
    attributes.forEach((name, value) -> {
      if (ATTRIBUTE_NAMES.contains(name)) {
        specifiedAttributes.put(name, value);
      }
    });

    var values = createMetadata(specifiedAttributes);
    var handler = new PartialMetadataHandler(values, Set.copyOf(specifiedAttributes.keySet()));

    return (DiagnosticMetadata) Proxy.newProxyInstance(
      DiagnosticMetadata.class.getClassLoader(),
      new Class<?>[]{DiagnosticMetadata.class, PartialMetadata.class},
      handler
    );
  }

  /**
   * Наложить переопределение на метаданные диагностики.
   *
   * @param base     Исходные метаданные диагностики.
   * @param override Переопределение. Если оно создано через {@link #create(Map)}, накладываются только
   *                 явно заданные в нём атрибуты; иначе переопределение заменяет метаданные целиком.
   * @return Итоговые метаданные диагностики.
   */
  public static DiagnosticMetadata applyTo(DiagnosticMetadata base, DiagnosticMetadata override) {
    if (!(override instanceof PartialMetadata partialMetadata)) {
      return override;
    }

    var specifiedAttributes = partialMetadata.specifiedAttributes();
    if (specifiedAttributes.isEmpty()) {
      return base;
    }

    var merged = new HashMap<String, Object>();
    for (var attribute : ATTRIBUTES) {
      var source = specifiedAttributes.contains(attribute.getName()) ? override : base;
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

  /**
   * Признак метаданных, знающих множество явно заданных атрибутов.
   */
  interface PartialMetadata {
    Set<String> specifiedAttributes();
  }

  /**
   * Делегирует атрибуты и методы {@link java.lang.annotation.Annotation} экземпляру аннотации
   * со значениями переопределения и отвечает на {@link PartialMetadata#specifiedAttributes()}.
   */
  private record PartialMetadataHandler(
    DiagnosticMetadata values,
    Set<String> specifiedAttributes
  ) implements InvocationHandler {

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (method.getDeclaringClass() == PartialMetadata.class) {
        return specifiedAttributes;
      }

      if ("equals".equals(method.getName()) && method.getParameterCount() == 1) {
        var other = args[0];
        if (other instanceof PartialMetadata otherPartial
          && !specifiedAttributes.equals(otherPartial.specifiedAttributes())) {
          return false;
        }
        return values.equals(other);
      }

      try {
        return method.invoke(values, args);
      } catch (InvocationTargetException e) {
        throw e.getCause();
      }
    }
  }
}
