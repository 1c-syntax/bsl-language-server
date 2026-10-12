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
package com.github._1c_syntax.bsl.languageserver.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Интеграционные тесты {@link BSLFiles#listBslFiles} на временной файловой системе. */
class BSLFilesTest {

  private static final String SRC_MODULE_BSL = "src/Module.bsl";

  /** Без excludePaths возвращаются все .bsl/.os файлы внутри корня. */
  @Test
  void listBslFilesReturnsAllBslFilesWhenNoExclusions(@TempDir Path projectRoot) throws IOException {
    // given
    Path moduleFile = createBslFile(projectRoot, SRC_MODULE_BSL);
    Path scriptFile = createBslFile(projectRoot, "src/Script.os");
    Path nestedFile = createBslFile(projectRoot, "src/sub/Nested.bsl");

    // when
    var found = BSLFiles.listBslFiles(projectRoot, null);

    // then
    assertThat(found).containsExactlyInAnyOrder(
      moduleFile.toFile(),
      scriptFile.toFile(),
      nestedFile.toFile()
    );
  }

  /** Каталоги с именами из excludePaths (".git", "node_modules") не разворачиваются при обходе. */
  @Test
  void listBslFilesSkipsExcludedDirectoriesBySimpleName(@TempDir Path projectRoot) throws IOException {
    // given
    Path keep = createBslFile(projectRoot, SRC_MODULE_BSL);
    createBslFile(projectRoot, ".git/HEAD.bsl");
    createBslFile(projectRoot, "node_modules/pkg/index.bsl");

    // when
    var found = BSLFiles.listBslFiles(projectRoot, List.of(".git", "node_modules"));

    // then
    assertThat(found).containsExactly(keep.toFile());
  }

  /** Glob "**\/.git/**" обрезает обход в самой .git-директории на любом уровне вложенности. */
  @Test
  void listBslFilesSkipsExcludedDirectoriesByDoubleStarSuffix(@TempDir Path projectRoot) throws IOException {
    // given
    Path keep = createBslFile(projectRoot, SRC_MODULE_BSL);
    createBslFile(projectRoot, "repo/.git/refs/HEAD.bsl");

    // when
    var found = BSLFiles.listBslFiles(projectRoot, List.of("**/.git/**"));

    // then
    assertThat(found).containsExactly(keep.toFile());
  }

  /** Файлы с расширениями вне списка BSL/OS не попадают в результат (даже без excludePaths). */
  @Test
  void listBslFilesIgnoresFilesNotInBslExtensions(@TempDir Path projectRoot) throws IOException {
    // given
    Path bsl = createBslFile(projectRoot, SRC_MODULE_BSL);
    Files.writeString(bsl.resolveSibling("notes.txt"), "ignored");

    // when
    var found = BSLFiles.listBslFiles(projectRoot, null);

    // then
    assertThat(found).containsExactly(bsl.toFile());
  }

  /** Несуществующий корень — пустой результат, а не ошибка. */
  @Test
  void listBslFilesReturnsNothingForMissingRoot(@TempDir Path projectRoot) {
    // when
    var found = BSLFiles.listBslFiles(projectRoot.resolve("missing"), null);

    // then
    assertThat(found).isEmpty();
  }

  /** Нечитаемый каталог пропускается, а модули рядом с ним находятся. */
  @Test
  @DisabledOnOs(OS.WINDOWS)
  void listBslFilesSkipsUnreadableDirectory(@TempDir Path projectRoot) throws IOException {
    // given
    Path before = createBslFile(projectRoot, "a/Module.bsl");
    Path after = createBslFile(projectRoot, "z/Module.bsl");
    createBslFile(projectRoot, "locked/Hidden.bsl");
    var locked = projectRoot.resolve("locked");
    Files.setPosixFilePermissions(locked, Set.of());
    try {
      // root читает каталог и без прав — тогда проверять нечего
      assumeFalse(Files.isReadable(locked), "каталог читается и без прав");

      // when
      var found = BSLFiles.listBslFiles(projectRoot, null);

      // then
      assertThat(found).containsExactlyInAnyOrder(before.toFile(), after.toFile());
    } finally {
      // иначе @TempDir не сможет удалить каталог
      Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"));
    }
  }

  /** Каталог с расширением модуля в имени — не модуль. */
  @Test
  void listBslFilesIgnoresDirectoriesNamedLikeModules(@TempDir Path projectRoot) throws IOException {
    // given
    Path bsl = createBslFile(projectRoot, SRC_MODULE_BSL);
    Files.createDirectories(projectRoot.resolve("src/Каталог.bsl"));

    // when
    var found = BSLFiles.listBslFiles(projectRoot, null);

    // then
    assertThat(found).containsExactly(bsl.toFile());
  }

  /** Символическая ссылка на модуль находится, как и сам модуль. */
  @Test
  void listBslFilesFindsSymbolicLinkToModule(@TempDir Path projectRoot) throws IOException {
    // given
    Path bsl = createBslFile(projectRoot, SRC_MODULE_BSL);
    Path link = projectRoot.resolve("src/Link.bsl");
    try {
      Files.createSymbolicLink(link, bsl);
    } catch (IOException | UnsupportedOperationException e) {
      // На Windows без прав разработчика ссылку создать нельзя.
      assumeTrue(false, "символические ссылки недоступны: " + e.getMessage());
    }

    // when
    var found = BSLFiles.listBslFiles(projectRoot, null);

    // then
    assertThat(found).containsExactlyInAnyOrder(bsl.toFile(), link.toFile());
  }

  /** Создаёт пустой BSL-файл по относительному пути в корне теста. */
  private static Path createBslFile(Path root, String relative) throws IOException {
    var file = root.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, "// BSL");
    return file;
  }
}
