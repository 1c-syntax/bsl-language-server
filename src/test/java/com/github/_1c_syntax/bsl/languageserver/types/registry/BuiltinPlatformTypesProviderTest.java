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

import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.languageserver.types.model.LanguageScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Тесты для {@link BuiltinPlatformTypesProvider}. JSON-фолбэк должен
 * отдавать типы только когда bsl-context недоступен. Парсинг JSON покрыт
 * отдельно в {@link BuiltinTypesJsonLoaderTest}.
 */
@ExtendWith(MockitoExtension.class)
class BuiltinPlatformTypesProviderTest {

  @Mock
  private BslContextHolder holder;

  @Mock
  private ContextProvider contextProvider;

  @Test
  void getTypesReturnsBuiltinsWhenBslContextUnavailable() {
    // given
    when(holder.get()).thenReturn(Optional.empty());
    var provider = new BuiltinPlatformTypesProvider(holder);

    // when
    var types = provider.getTypes();

    // then — JSON-fallback всегда содержит хотя бы примитивы и базовые платформенные типы
    assertThat(types).isNotEmpty();
  }

  @Test
  void getTypesReturnsEmptyWhenBslContextAvailable() {
    // given — bsl-context доступен → JSON-fallback не нужен
    when(holder.get()).thenReturn(Optional.of(contextProvider));
    var provider = new BuiltinPlatformTypesProvider(holder);

    // when
    var types = provider.getTypes();

    // then
    assertThat(types).isEmpty();
  }

  @Test
  void languageScopeIsBsl() {
    // given
    var provider = new BuiltinPlatformTypesProvider(holder);

    // when / then
    assertThat(provider.getLanguageScope()).isEqualTo(LanguageScope.BSL);
  }

  @Test
  void typesIncludePrimitiveStringAndNumber() {
    // given
    when(holder.get()).thenReturn(Optional.empty());
    var provider = new BuiltinPlatformTypesProvider(holder);

    // when
    var typeNames = provider.getTypes().stream()
      .map(td -> td.name().primary())
      .toList();

    // then — встроенный JSON-pack обязан включать основные примитивы.
    assertThat(typeNames).contains("Строка", "Число", "Булево");
  }

  @Test
  void typesIncludeBasicCollectionTypes() {
    // given
    when(holder.get()).thenReturn(Optional.empty());
    var provider = new BuiltinPlatformTypesProvider(holder);

    // when
    var typeNames = provider.getTypes().stream()
      .map(td -> td.name().primary())
      .toList();

    // then
    assertThat(typeNames).contains("Массив", "Структура", "Соответствие");
  }

  @Test
  void platformTypeMassivHasPlatformMembers() {
    // given
    when(holder.get()).thenReturn(Optional.empty());
    var provider = new BuiltinPlatformTypesProvider(holder);

    // when
    var arrayDecl = provider.getTypes().stream()
      .filter(td -> "Массив".equals(td.name().primary()))
      .findFirst()
      .orElseThrow();

    // then
    assertThat(arrayDecl.members())
      .extracting(m -> m.name())
      .containsAnyOf("Добавить", "Количество", "ВерхняяГраница", "Очистить");
  }

  @Test
  void primitiveStringHasNoMembers() {
    // given — у примитива Строка нет методов ни в BSL, ни в OneScript:
    // Длина/ВРег/НРег — это глобальные функции (СтрДлина/ВРег/НРег), а не члены типа.
    when(holder.get()).thenReturn(Optional.empty());
    var provider = new BuiltinPlatformTypesProvider(holder);

    // when
    var stringDecl = provider.getTypes().stream()
      .filter(td -> "Строка".equals(td.name().primary()))
      .findFirst()
      .orElseThrow();

    // then
    assertThat(stringDecl.members())
      .as("у примитива Строка не должно быть методов")
      .isEmpty();
  }

  @Test
  void chartFamilyHasDeprecatedPaletteMembers() {
    // Запасной JSON-fallback должен содержать устаревшие свойства/методы
    // Диаграммы (и однотипно ДиаграммаГанта/СводнаяДиаграмма) — они раньше
    // ловились хардкодом DeprecatedAttributes8312Diagnostic, теперь подаются
    // через провайдер.
    when(holder.get()).thenReturn(Optional.empty());
    var provider = new BuiltinPlatformTypesProvider(holder);
    var types = provider.getTypes();

    for (var typeName : java.util.List.of("Диаграмма", "ДиаграммаГанта", "СводнаяДиаграмма")) {
      var decl = types.stream()
        .filter(td -> typeName.equals(td.name().primary()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("type " + typeName + " not in JSON fallback"));
      assertThat(decl.members())
        .as("deprecated members on %s", typeName)
        .extracting(m -> m.name())
        .containsExactlyInAnyOrder(
          "ПалитраЦветов",
          "ЦветНачалаГрадиентнойПалитры",
          "ЦветКонцаГрадиентнойПалитры",
          "ПолучитьПалитру",
          "УстановитьПалитру"
        );
      for (var member : decl.members()) {
        assertThat(member.metadata().deprecatedSinceVersion())
          .as("%s.%s deprecatedSinceVersion", typeName, member.name())
          .isEqualTo("8.3.12");
      }
    }
  }

  @Test
  void primitiveTypesHaveNoConstructors() {
    // given
    when(holder.get()).thenReturn(Optional.empty());
    var provider = new BuiltinPlatformTypesProvider(holder);

    // when — Число — это примитив, конструкторов у него быть не должно.
    var numberDecl = provider.getTypes().stream()
      .filter(td -> "Число".equals(td.name().primary()))
      .findFirst()
      .orElseThrow();

    // then
    assertThat(numberDecl.constructors()).isEmpty();
  }
}
