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
package com.github._1c_syntax.bsl.languageserver.types.registry;

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Встроенные JSON-ресурсы должны читаться загрузчиком классов самого BSL LS,
 * а не context class loader'ом потока.
 * <p>
 * Когда BSL LS встроен в чужой хост с изолированными загрузчиками (плагин
 * SonarQube), context class loader принадлежит хосту и jar-а BSL LS не видит.
 * Классы при этом грузятся нормально — своим загрузчиком, — а вот ресурсы,
 * найденные через context class loader, не находятся, и глобальная область
 * молча оказывается пустой.
 * <p>
 * Тесты подменяют context class loader на platform class loader — он заведомо
 * не видит ресурсов приложения, то есть воспроизводит хостовую изоляцию.
 */
class BuiltinResourcesClassLoaderTest {

  private static final String BUILTIN_GLOBALS =
    "com/github/_1c_syntax/bsl/languageserver/types/registry/builtin-globals.json";
  private static final String FALLBACK_TYPES =
    "com/github/_1c_syntax/bsl/languageserver/types/registry/classloader-fallback-types.json";

  @Test
  void globalScopeIsLoadedWithForeignContextClassLoader() {
    // given: платформа 1С недоступна, значит BSL-часть читается из JSON-ресурса
    var bslContextHolder = mock(BslContextHolder.class);
    when(bslContextHolder.get()).thenReturn(Optional.empty());

    // when: провайдер построен под чужим context class loader'ом
    var provider = withForeignContextClassLoader(
      () -> new GlobalScopeProvider(bslContextHolder, mock(TypeRegistry.class)));

    // then: обе языковые части наполнены из своих builtin-*-globals.json
    assertThat(provider.getClasses(FileType.OS))
      .as("классы OneScript — из builtin-oscript-globals.json")
      .isNotEmpty();
    assertThat(provider.getClasses(FileType.BSL))
      .as("классы BSL — из builtin-globals.json")
      .isNotEmpty();
    // ключевых слов нет в builtin-globals.json — они догружаются
    // KeywordMetadataLoader'ом из builtin-keywords.json
    assertThat(provider.getKeywords(FileType.BSL))
      .as("ключевые слова BSL — из builtin-keywords.json")
      .isNotEmpty();
  }

  @Test
  void globalContextMembersAreLoadedWithForeignContextClassLoader() {
    var members = withForeignContextClassLoader(
      () -> GlobalScopeProvider.globalContextMembers(BUILTIN_GLOBALS));

    assertThat(members).isNotEmpty();
  }

  @Test
  void builtinTypesAreLoadedWithForeignContextClassLoader() {
    var types = withForeignContextClassLoader(
      () -> BuiltinTypesJsonLoader.load(FALLBACK_TYPES));

    assertThat(types).isNotEmpty();
  }

  /**
   * Выполняет действие с context class loader'ом, не видящим ресурсов
   * приложения, и возвращает исходный загрузчик на место.
   */
  private static <T> T withForeignContextClassLoader(Supplier<T> action) {
    var thread = Thread.currentThread();
    var previous = thread.getContextClassLoader();
    thread.setContextClassLoader(ClassLoader.getPlatformClassLoader());
    try {
      return action.get();
    } finally {
      thread.setContextClassLoader(previous);
    }
  }
}
