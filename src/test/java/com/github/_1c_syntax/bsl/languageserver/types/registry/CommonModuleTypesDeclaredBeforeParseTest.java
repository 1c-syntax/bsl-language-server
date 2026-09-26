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

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Имя общего модуля известно из метаданных, а разбор его файла нужен только членам. Типы
 * общих модулей объявляются сразу по добавлению рабочей области — иначе обращение
 * {@code ОбщийМодуль.Метод()} из документа, разобранного раньше модуля, не находит даже
 * получателя, и результат выходит пустым, ничем не отличимым от честной пустоты.
 * <p>
 * {@code populate=false} здесь намеренно: доказывается, что объявление не зависит от того,
 * разобран ли хоть один документ.
 */
class CommonModuleTypesDeclaredBeforeParseTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeRegistry typeRegistry;

  @Test
  void commonModuleTypeIsDeclaredWithoutParsingItsFile() {
    // given: рабочая область добавлена, но ни один документ не разобран.
    initServerContext(Path.of(PATH_TO_METADATA), false);
    assertThat(context.getDocuments()).isEmpty();

    // then: тип общего модуля уже объявлен и виден как глобальное имя.
    assertThat(typeRegistry.resolve("ОбщегоНазначения"))
      .as("тип общего модуля объявлен по метаданным, без разбора его файла")
      .isPresent();
    assertThat(typeRegistry.getMembers(TypeRegistry.GLOBAL_CONTEXT, FileType.BSL))
      .as("имя общего модуля видно без префикса — оно член глобальной области")
      .anyMatch(member -> member.matches("ОбщегоНазначения"));
  }
}
