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

import com.github._1c_syntax.bsl.context.PlatformContextGrabber;
import com.github._1c_syntax.bsl.context.PlatformFinder;
import com.github._1c_syntax.bsl.context.api.ContextQueryTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Сверка встроенного пака таблиц языка запросов с живой справкой.
 * <p>
 * Пак подменяет синтакс-помощник, когда 1С не установлена, — и разойтись с ним
 * не должен. Тест требует установленной платформы: он и есть то место, где
 * расхождение обнаружится, а остальные тесты идут по паку и платформы не
 * требуют. Разошёлся — перегенерировать {@code GenerateBuiltinQueryTables}.
 */
@EnabledIfEnvironmentVariable(named = "BSL_LANGUAGE_SERVER_RUN_HBK_TESTS",
  matches = "true",
  disabledReason = "Сверка пака с живой справкой требует установленной 1С")
class BuiltinQueryTablesHbkTest {

  @Test
  void packMatchesTheInstalledPlatform() throws Exception {
    // given
    var install = PlatformFinder.findLatest().orElseThrow();
    var grabber = PlatformContextGrabber.fromPlatformBin(install.binDir(),
      Files.createTempDirectory("builtin-query-tables-check-"));
    grabber.parse();

    // when
    var live = grabber.getQueryProvider().getTables();
    var pack = BuiltinQueryTables.load();

    // then
    assertThat(names(pack))
      .as("состав таблиц в паке — тот же, что у платформы")
      .containsExactlyInAnyOrderElementsOf(names(live));
    assertThat(fields(pack))
      .as("имена полей и типы значений — те же")
      .containsExactlyInAnyOrderElementsOf(fields(live));
  }

  private static List<String> names(List<? extends ContextQueryTable> tables) {
    return tables.stream()
      .map(table -> table.name().getName() + " | " + table.name().getAlias()
        + " | correspondence=" + table.correspondence().map(String::valueOf).orElse("-"))
      .toList();
  }

  /** Поля всех таблиц одной строкой на поле: имя таблицы, оба написания имени и типы. */
  private static List<String> fields(List<? extends ContextQueryTable> tables) {
    return tables.stream()
      .flatMap(table -> table.fields().stream()
        .map(field -> table.name().getName() + " :: " + field.name().getName()
          + " | " + field.name().getAlias()
          + " | " + field.types().stream()
          .map(type -> type.name().getName())
          .collect(Collectors.joining(", "))))
      .toList();
  }
}
