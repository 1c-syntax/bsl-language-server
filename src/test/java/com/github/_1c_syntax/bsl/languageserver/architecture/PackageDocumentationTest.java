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
package com.github._1c_syntax.bsl.languageserver.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ответственность каждого пакета записана рядом с ним: пакет с классами описан своим
 * {@code package-info.java} — что в нём лежит и за что он отвечает. Новый пакет без описания
 * валит сборку.
 */
class PackageDocumentationTest {

  private static final Path SOURCES = Path.of("src/main/java/com/github/_1c_syntax/bsl/languageserver");

  @Test
  void everyPackageWithClassesHasPackageInfo() throws IOException {
    try (var directories = Files.walk(SOURCES)) {
      var undocumented = directories
        .filter(Files::isDirectory)
        .filter(PackageDocumentationTest::containsClasses)
        .filter(directory -> !Files.exists(directory.resolve("package-info.java")))
        .map(directory -> SOURCES.relativize(directory).toString().replace('\\', '/'))
        .sorted()
        .toList();

      assertThat(undocumented).as("пакеты без package-info.java").isEmpty();
    }
  }

  private static boolean containsClasses(Path directory) {
    try (var files = Files.list(directory)) {
      return files.anyMatch(file -> file.toString().endsWith(".java")
        && !file.getFileName().toString().equals("package-info.java"));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
