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
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Платформенные события типов приходят из bsl-context в виде членов
 * {@link MemberKind#EVENT}. Нужен реальный HBK (СП) — события у типов
 * не покрываются JSON-fallback'ом.
 */
@SpringBootTest
@CleanupContextBeforeClassAndAfterClass
@TestPropertySource(properties = "app.platform-context.enabled=true")
@EnabledIfEnvironmentVariable(named = "BSL_LANGUAGE_SERVER_RUN_HBK_TESTS",
  matches = "true",
  disabledReason = "Требует HBK 1С (события у типов берутся из bsl-context)")
class PlatformEventsHbkTest {

  @Autowired
  private BslContextPlatformTypesProvider provider;

  @Test
  void formTypeHasOnOpenEvent() {
    // Тип «Форма» имеет ~13 событий в HBK (probe-замер); проверяем что они
    // публикуются провайдером как члены MemberKind.EVENT и среди них есть
    // ключевой ПриОткрытии.
    var formDecl = provider.getTypes().stream()
      .filter(t -> "Форма".equals(t.qualifiedName()))
      .findFirst()
      .orElseThrow(() -> new AssertionError("Тип 'Форма' не найден в bsl-context"));

    var events = formDecl.members().stream()
      .filter(m -> m.kind() == MemberKind.EVENT)
      .toList();

    assertThat(events)
      .as("Форма должна иметь события из HBK")
      .isNotEmpty();
    assertThat(events).extracting(m -> m.name())
      .contains("ПриОткрытии");
  }

  @Test
  void multipleTypesHaveEvents() {
    // Sanity-check: HBK 8.5.4 содержит ~138 типов с событиями, ~659 событий
    // (см. probe в bsl-context). Проверяем, что провайдер их публикует как
    // EVENT-членов в значимом объёме.
    var types = provider.getTypes();
    long typesWithEvents = types.stream()
      .filter(t -> t.members().stream().anyMatch(m -> m.kind() == MemberKind.EVENT))
      .count();
    long totalEvents = types.stream()
      .flatMap(t -> t.members().stream())
      .filter(m -> m.kind() == MemberKind.EVENT)
      .count();

    assertThat(typesWithEvents)
      .as("ожидается > 50 типов с событиями (HBK 8.5.4: 138)")
      .isGreaterThan(50);
    assertThat(totalEvents)
      .as("ожидается > 200 событий всего (HBK 8.5.4: 659)")
      .isGreaterThan(200);
  }
}
