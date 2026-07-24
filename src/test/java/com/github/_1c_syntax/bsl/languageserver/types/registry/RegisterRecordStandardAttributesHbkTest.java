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
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.Locale;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Регресс: {@code getAttributes()} регистра (bsl-mdo) возвращает стандартные
 * реквизиты записи (Период/Регистратор/Активность/…) вперемешку с
 * собственными, но platform-типа записи ({@code РегистрХХХЗапись.<Имя>}) уже
 * несёт их из bsl-context как обычные bilingual-члены. Без фильтра по
 * {@code Имя реквизита} материализуются ОДНОЯЗЫЧНЫЕ дубликаты (под английским
 * написанием) — нужен реальный HBK, дубликаты не воспроизводятся на
 * JSON-фолбэке.
 */
@CleanupContextBeforeClassAndAfterClass
@TestPropertySource(properties = "app.platform-context.enabled=true")
@EnabledIfEnvironmentVariable(named = "BSL_LANGUAGE_SERVER_RUN_HBK_TESTS",
  matches = "true",
  disabledReason = "Требует HBK 1С (стандартные реквизиты записи регистра — из bsl-context)")
class RegisterRecordStandardAttributesHbkTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeRegistry typeRegistry;

  @Test
  void informationRegisterRecordHasNoDuplicateStandardAttributeMembers() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();

    var ref = typeRegistry.resolve("РегистрСведенийЗапись.РегистрСведений1", FileType.BSL).orElse(null);
    assertThat(ref).as("специализированный тип записи регистра сведений должен резолвиться").isNotNull();

    var members = typeRegistry.getMembers(ref, FileType.BSL);
    var lowerNames = members.stream().map(m -> m.name().toLowerCase(Locale.ROOT)).toList();

    assertThat(lowerNames)
      .as("Активность/Период/Регистратор/НомерСтроки должны прийти РОВНО по одному разу, "
        + "как единственный bilingual-член (из bsl-context), а не второй раз под "
        + "английским написанием (материализованным по ошибке из getAttributes())")
      .doesNotContain("active", "period", "linenumber", "recorder")
      .contains("активность", "период", "регистратор", "номерстроки");
  }
}
