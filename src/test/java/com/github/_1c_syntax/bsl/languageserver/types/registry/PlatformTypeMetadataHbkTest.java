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

import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypePackProvider.TypeDecl;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * «Страничные» метаданные типов, конструкторов и событий приходят из
 * bsl-context и не покрываются JSON-fallback'ом, поэтому нужен реальный HBK
 * (синтакс-помощник установленной платформы).
 */
@SpringBootTest
@CleanupContextBeforeClassAndAfterClass
@TestPropertySource(properties = "app.platform-context.enabled=true")
@EnabledIfEnvironmentVariable(named = "BSL_LANGUAGE_SERVER_RUN_HBK_TESTS",
  matches = "true",
  disabledReason = "Требует HBK 1С (страничные метаданные берутся из bsl-context)")
class PlatformTypeMetadataHbkTest {

  @Autowired
  private BslContextPlatformTypesProvider provider;

  @Test
  void typePageMetadataIsFilledFromHbk() {
    var array = typeByName("Массив");

    assertThat(array.metadata().availabilities())
      .as("«Доступность:» с главной страницы типа")
      .isNotEmpty();
    assertThat(array.metadata().sinceVersion())
      .as("«Начиная с версии» с главной страницы типа")
      .isNotBlank();
  }

  @Test
  void deprecatedTypeCarriesVersionAndReplacements() {
    // ЗащищенноеСоединениеNSS помечен в СП устаревшим с 8.3.8 в пользу OpenSSL-варианта.
    var deprecated = typeByName("ЗащищенноеСоединениеNSS");

    assertThat(deprecated.metadata().deprecatedSinceVersion()).isEqualTo("8.3.8");
    assertThat(deprecated.metadata().recommendedReplacements())
      .contains("ЗащищенноеСоединениеOpenSSL");
  }

  @Test
  void typeExamplesAndSeeAlsoArePublished() {
    var types = provider.getTypes();

    assertThat(types).filteredOn(t -> !t.metadata().examples().isEmpty())
      .as("блоки «Пример:» страниц типов")
      .isNotEmpty();
    assertThat(types).filteredOn(t -> !t.metadata().seeAlso().isEmpty())
      .as("блоки «См. также:» страниц типов")
      .isNotEmpty();
  }

  @Test
  void constructorMetadataIsPublished() {
    assertThat(provider.getTypes())
      .flatMap(TypeDecl::constructors)
      .filteredOn(constructor -> !constructor.metadata().isEmpty())
      .as("версии/примеры/«См. также» со страниц конструкторов")
      .isNotEmpty();
  }

  @Test
  void eventAndPropertyMetadataIsPublished() {
    var members = provider.getTypes().stream()
      .flatMap(type -> type.members().stream())
      .toList();

    assertThat(members)
      .filteredOn(member -> member.kind() == MemberKind.EVENT && !member.metadata().isEmpty())
      .as("метаданные событий типов")
      .isNotEmpty();
    assertThat(members)
      .filteredOn(member -> !member.metadata().notes().isEmpty())
      .as("«Замечание:» у свойств и методов")
      .isNotEmpty();
  }

  private TypeDecl typeByName(String qualifiedName) {
    return provider.getTypes().stream()
      .filter(type -> qualifiedName.equals(type.qualifiedName()))
      .findFirst()
      .orElseThrow(() -> new AssertionError("Тип '" + qualifiedName + "' не найден в bsl-context"));
  }
}
