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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Вывод типа внедряемой зависимости фреймворка «ОСень» (Autumn).
 * <p>
 * Аннотация {@code &Пластилин} помечает поле/параметр/сеттер как точку
 * внедрения желудя (компонента). Тип внедряемого значения определяется именем
 * желудя:
 * <pre>
 *   &amp;Пластилин("ИмяЖелудя")
 *   Перем ВнедренныйЖелудь;   // тип = желудь "ИмяЖелудя"
 * </pre>
 * Имя желудя берётся из параметра {@code Значение} (или первого позиционного),
 * иначе — из имени самой переменной/параметра. Параметр {@code Тип} с
 * коллекцией ({@code Массив}, {@code ТаблицаЗначений} и т.п.) означает внедрение
 * коллекции желудей — тогда типом становится сама коллекция.
 * <p>
 * На этом этапе имя желудя резолвится напрямую через {@link TypeRegistry}, что
 * покрывает случай «имя желудя == имя типа .os-класса» (поведение по умолчанию
 * аннотации {@code &Желудь}). Переименованные желуди и прозвища — отдельная
 * задача (индекс желудей).
 */
@Component
@Scope(value = WorkspaceScope.SCOPE_NAME, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class AutumnComponentInferencer {

  /**
   * Имена аннотаций-точек внедрения. Базовый набор фреймворка; алиасы и
   * пользовательские аннотации через мета-аннотации — отдельная задача.
   */
  private static final Set<String> INJECTION_ANNOTATIONS = Set.of("Пластилин");

  private static final String VALUE_PARAMETER = "Значение";
  private static final String TYPE_PARAMETER = "Тип";

  /**
   * Значение параметра {@code Тип}, означающее «внедрить желудь как таковой» —
   * тип в этом случае определяется по имени желудя, а не по {@code Тип}.
   */
  private static final String BEAN_TYPE = "Желудь";

  private final TypeRegistry typeRegistry;

  /**
   * Вывести тип внедряемой зависимости по аннотациям объявления.
   *
   * @param annotations  аннотации поля/параметра
   * @param fallbackName имя переменной/параметра — используется как имя желудя,
   *                     если оно не задано в аннотации
   * @param fileType     тип файла-владельца (для корректного резолва типа)
   * @return тип внедряемого желудя или {@link TypeSet#EMPTY}, если это не точка
   *         внедрения либо тип не разрешился
   */
  public TypeSet inferInjectedType(List<Annotation> annotations, String fallbackName, FileType fileType) {
    var injection = findInjectionAnnotation(annotations);
    if (injection == null) {
      return TypeSet.EMPTY;
    }

    var typeName = resolveTypeName(injection, fallbackName);
    if (typeName == null || typeName.isBlank()) {
      return TypeSet.EMPTY;
    }

    return typeRegistry.resolve(typeName, fileType)
      .map(TypeSet::of)
      .orElse(TypeSet.EMPTY);
  }

  private static @Nullable Annotation findInjectionAnnotation(List<Annotation> annotations) {
    for (var annotation : annotations) {
      if (INJECTION_ANNOTATIONS.contains(annotation.getName())) {
        return annotation;
      }
    }
    return null;
  }

  private static @Nullable String resolveTypeName(Annotation injection, String fallbackName) {
    var explicitType = stringParameter(injection, TYPE_PARAMETER, 1);
    if (explicitType != null && !explicitType.isBlank() && !BEAN_TYPE.equalsIgnoreCase(explicitType)) {
      // Тип-коллекция: внедряется коллекция желудей — типом становится коллекция.
      return explicitType;
    }

    var beanName = stringParameter(injection, VALUE_PARAMETER, 0);
    if (beanName != null && !beanName.isBlank()) {
      return beanName;
    }
    return fallbackName;
  }

  /**
   * Значение строкового параметра аннотации: сначала по имени, затем по позиции
   * среди безымянных (позиционных) параметров.
   */
  private static @Nullable String stringParameter(Annotation annotation, String name, int positionalIndex) {
    var parameters = annotation.getParameters();
    for (var parameter : parameters) {
      if (name.equalsIgnoreCase(parameter.name()) && parameter.value().isLeft()) {
        return parameter.value().getLeft();
      }
    }

    int position = 0;
    for (var parameter : parameters) {
      if (!parameter.name().isEmpty()) {
        continue;
      }
      if (position == positionalIndex && parameter.value().isLeft()) {
        return parameter.value().getLeft();
      }
      position++;
    }
    return null;
  }
}
