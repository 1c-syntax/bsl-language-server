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
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет, что описания платформенных типов (классов и system enums)
 * из builtin-oscript-platform-types.json пробрасываются в {@link TypeRegistry}
 * и доступны через {@link TypeRegistry#getDescription(com.github._1c_syntax.bsl.languageserver.types.model.TypeRef)}.
 */
@CleanupContextBeforeClassAndAfterClass
class PlatformTypeDescriptionTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeRegistry typeRegistry;

  @Test
  void exposesOscriptClassDescription() {
    initServerContext();
    var ref = typeRegistry.resolve("ИнформацияОбОшибке").orElseThrow();
    assertThat(typeRegistry.getDescription(ref, FileType.OS)).isNotBlank();
  }

  @Test
  void exposesSystemEnumDescription() {
    initServerContext();
    // КодировкаТекста — system enum; описание в JSON-fallback есть только в OneScript-наборе.
    var ref = typeRegistry.resolve("КодировкаТекста").orElseThrow();
    assertThat(typeRegistry.getDescription(ref, FileType.OS)).isNotBlank();
  }

  @Test
  void exposesConstructorsForArray() {
    initServerContext();
    var ref = typeRegistry.resolve("Массив").orElseThrow();
    var ctors = typeRegistry.getConstructors(ref, FileType.OS);
    assertThat(ctors).isNotEmpty();
    // 3 варианта: По умолчанию (0 параметров), По количеству элементов (1), На основании фиксированного (1).
    assertThat(ctors).hasSizeGreaterThanOrEqualTo(2);
    // BSL-набор имеет собственные конструкторы Массива.
    assertThat(typeRegistry.getConstructors(ref, FileType.BSL)).isNotEmpty();
  }
}
