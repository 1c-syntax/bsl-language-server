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
package com.github._1c_syntax.bsl.languageserver.util;

import lombok.experimental.UtilityClass;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.annotation.ReflectiveRuntimeHintsRegistrar;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * Подсказки native-image, которые Spring AOT собирает с класса бина.
 */
@UtilityClass
public class AotHints {

  /**
   * Собрать подсказки, объявленные на классе: {@code @ImportRuntimeHints} и аннотации на основе
   * {@code @Reflective} ({@code @RegisterReflection}, {@code @RegisterReflectionForBinding}).
   *
   * @param type класс бина
   * @return собранные подсказки
   */
  public RuntimeHints hintsOf(Class<?> type) {
    var hints = new RuntimeHints();
    var importRuntimeHints = AnnotatedElementUtils.findMergedAnnotation(type, ImportRuntimeHints.class);
    if (importRuntimeHints != null) {
      for (var registrar : importRuntimeHints.value()) {
        BeanUtils.instantiateClass(registrar).registerHints(hints, type.getClassLoader());
      }
    }
    new ReflectiveRuntimeHintsRegistrar().registerRuntimeHints(hints, type);
    return hints;
  }
}
