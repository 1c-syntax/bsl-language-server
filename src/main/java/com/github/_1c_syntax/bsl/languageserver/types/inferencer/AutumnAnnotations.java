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

import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Имена аннотаций фреймворка «ОСень» (Autumn) и помощники чтения их параметров.
 * <p>
 * Базовый набор имён; алиасы (killjoy) и пользовательские аннотации через
 * мета-аннотации разрешаются отдельно.
 */
@UtilityClass
public class AutumnAnnotations {

  /** Объявление компонента-желудя над конструктором. */
  public static final String COMPONENT = "Желудь";
  /** Фабричный метод, создающий желудь. */
  public static final String FACTORY = "Завязь";
  /** Точка внедрения зависимости. */
  public static final String INJECTION = "Пластилин";
  /** Приоритетный желудь при конфликте имён/прозвищ. */
  public static final String PRIMARY = "Верховный";
  /** Дополнительное имя (алиас) желудя; повторяемая. */
  public static final String QUALIFIER = "Прозвище";

  /** Первый параметр большинства аннотаций — имя желудя/значение. */
  public static final String VALUE_PARAMETER = "Значение";
  /** Параметр {@code Тип} аннотаций {@code &Пластилин}/{@code &Завязь}. */
  public static final String TYPE_PARAMETER = "Тип";
  /** Значение {@code Тип}, означающее «желудь как таковой». */
  public static final String BEAN_TYPE = "Желудь";

  /**
   * @return первая аннотация с указанным именем или {@code null}.
   */
  public static @Nullable Annotation find(Iterable<Annotation> annotations, String name) {
    for (var annotation : annotations) {
      if (name.equals(annotation.getName())) {
        return annotation;
      }
    }
    return null;
  }

  /**
   * Значение строкового параметра аннотации: приоритет у параметра с заданным
   * именем; если такого нет — берётся безымянный (позиционный) параметр под
   * указанным индексом.
   */
  public static @Nullable String stringParameter(Annotation annotation, String name, int positionalIndex) {
    String positionalValue = null;
    var position = 0;
    for (var parameter : annotation.getParameters()) {
      if (name.equalsIgnoreCase(parameter.name()) && parameter.value().isLeft()) {
        return parameter.value().getLeft();
      }
      if (parameter.name().isEmpty()) {
        if (position == positionalIndex && parameter.value().isLeft()) {
          positionalValue = parameter.value().getLeft();
        }
        position++;
      }
    }
    return positionalValue;
  }
}
