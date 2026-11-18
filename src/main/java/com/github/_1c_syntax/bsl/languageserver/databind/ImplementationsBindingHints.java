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
package com.github._1c_syntax.bsl.languageserver.databind;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Type;
import java.util.Objects;

/**
 * Подсказки для native-image к полиморфным типам, которые читаются и пишутся Jackson'ом.
 * <p>
 * Конкретные реализации типа находятся на classpath проекта во время AOT-обработки и
 * регистрируются для биндинга вместе со своими свойствами.
 */
public final class ImplementationsBindingHints {

  private static final String BASE_PACKAGE = "com.github._1c_syntax.bsl.languageserver";

  private ImplementationsBindingHints() {
    // utility class
  }

  /**
   * Зарегистрировать для биндинга все конкретные реализации типа из пакетов проекта.
   *
   * @param hints       подсказки, в которые добавляется регистрация
   * @param classLoader загрузчик классов, по classpath которого ищутся реализации
   * @param baseType    базовый тип (интерфейс или класс)
   */
  public static void register(RuntimeHints hints, @Nullable ClassLoader classLoader, Class<?> baseType) {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.setResourceLoader(new DefaultResourceLoader(classLoader));
    scanner.addIncludeFilter(new AssignableTypeFilter(baseType));

    var implementations = scanner.findCandidateComponents(BASE_PACKAGE).stream()
      .map(BeanDefinition::getBeanClassName)
      .filter(Objects::nonNull)
      .map(className -> ClassUtils.resolveClassName(className, classLoader))
      .toArray(Type[]::new);

    new BindingReflectionHintsRegistrar().registerReflectionHints(hints.reflection(), implementations);
  }
}
