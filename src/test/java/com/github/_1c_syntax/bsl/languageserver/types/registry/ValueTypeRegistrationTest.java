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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Регистрация типа «Тип» — значения, которое возвращают {@code Тип()} и
 * {@code ТипЗнч()}. Тип объявлен возвращаемым в паках глобального контекста
 * обоих языков, поэтому и в реестре он должен быть виден и в BSL-, и в
 * OneScript-файлах — иначе ссылка на возвращаемое значение никуда не ведёт.
 */
@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class ValueTypeRegistrationTest {

  @Autowired
  private TypeRegistry typeRegistry;

  @Test
  void valueTypeIsVisibleInBslFiles() {
    // given — платформа 1С не подключена, типы берутся из json-фоллбэка

    // when
    var ref = typeRegistry.resolve("Тип", FileType.BSL);

    // then
    assertThat(ref).isPresent();
    assertThat(typeRegistry.getDescription(ref.orElseThrow(), Language.RU, FileType.BSL))
      .isNotEmpty();
  }

  @Test
  void valueTypeIsVisibleInOscriptFiles() {
    // given — платформа 1С не подключена, типы берутся из json-фоллбэка

    // when
    var ref = typeRegistry.resolve("Тип", FileType.OS);

    // then
    assertThat(ref).isPresent();
    assertThat(typeRegistry.getDescription(ref.orElseThrow(), Language.RU, FileType.OS))
      .isNotEmpty();
  }

  @Test
  void valueTypeResolvesByEnglishName() {
    // given
    var ruRef = typeRegistry.resolve("Тип", FileType.BSL).orElseThrow();

    // when
    var enRef = typeRegistry.resolve("Type", FileType.BSL);

    // then — обе стороны двуязычного имени ведут в один тип
    assertThat(enRef).contains(ruRef);
    assertThat(typeRegistry.displayName(ruRef, Language.EN)).isEqualTo("Type");
  }
}
