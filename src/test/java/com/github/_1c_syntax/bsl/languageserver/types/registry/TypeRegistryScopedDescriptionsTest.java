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
 * Выбор языкового варианта двуязычного описания типа, существующего и в BSL,
 * и в OneScript ({@code ТаблицаЗначений}): описание фильтруется по типу файла,
 * а не отдаётся первым зарегистрированным (issue #4054).
 * <p>
 * В JSON-fallback (bsl-context недоступен) у пересекающихся типов описание
 * есть только в OneScript-наборе — оно не должно показываться в BSL-файлах.
 */
@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class TypeRegistryScopedDescriptionsTest {

  @Autowired
  private TypeRegistry typeRegistry;

  @Test
  void bilingualDescriptionIsSelectedByFileType() {
    // given
    var ref = typeRegistry.resolve("ТаблицаЗначений").orElseThrow();

    // when / then — OneScript-описание видно только в OS-файлах
    assertThat(typeRegistry.getDescription(ref, Language.RU, FileType.OS))
      .contains("табличном виде");
    assertThat(typeRegistry.getDescription(ref, Language.RU, FileType.BSL))
      .doesNotContain("табличном виде");
  }

}
