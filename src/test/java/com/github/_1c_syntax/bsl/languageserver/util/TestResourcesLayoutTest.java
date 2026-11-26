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
package com.github._1c_syntax.bsl.languageserver.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Фикстуры-конфигурации лежат только в {@code src/test/resources/metadata/<имя>}.
 * <p>
 * Тест на одиночном файле получает рабочую область с корнем в каталоге этого файла
 * ({@link TestUtils#getDocumentContextFromFile(String)}), а метаданные ищутся во всех
 * подкаталогах корня. Конфигурация, положенная рядом с одиночными фикстурами, читается
 * заново каждым таким тестом и замедляет его в разы — хотя сам он её не касается.
 */
class TestResourcesLayoutTest {

  private static final Path RESOURCES = Path.of("src", "test", "resources");
  private static final Path METADATA = RESOURCES.resolve("metadata");
  private static final Set<String> CONFIGURATION_ROOTS = Set.of("Configuration.xml", "Configuration.mdo");

  @Test
  void configurationFixturesLiveInMetadata() throws IOException {
    // when
    try (var files = Files.walk(RESOURCES)) {
      var misplaced = files
        .filter(file -> CONFIGURATION_ROOTS.contains(file.getFileName().toString()))
        .filter(file -> !file.startsWith(METADATA))
        .toList();

      // then
      assertThat(misplaced)
        .as("конфигурации вне %s читаются каждым тестом на одиночном файле по соседству", METADATA)
        .isEmpty();
    }
  }
}
