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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BSLFilesTest {

  private static final String SRC_MODULE_BSL = "src/Module.bsl";

  @Test
  void listBslFilesReturnsAllBslFilesWhenNoExclusions(@TempDir Path projectRoot) throws IOException {
    Path moduleFile = createBslFile(projectRoot, SRC_MODULE_BSL);
    Path scriptFile = createBslFile(projectRoot, "src/Script.os");
    Path nestedFile = createBslFile(projectRoot, "src/sub/Nested.bsl");

    var found = BSLFiles.listBslFiles(projectRoot, null);

    assertThat(found).containsExactlyInAnyOrder(
      moduleFile.toFile(),
      scriptFile.toFile(),
      nestedFile.toFile()
    );
  }

  @Test
  void listBslFilesSkipsExcludedDirectoriesBySimpleName(@TempDir Path projectRoot) throws IOException {
    Path keep = createBslFile(projectRoot, SRC_MODULE_BSL);
    createBslFile(projectRoot, ".git/HEAD.bsl");
    createBslFile(projectRoot, "node_modules/pkg/index.bsl");

    var found = BSLFiles.listBslFiles(projectRoot, List.of(".git", "node_modules"));

    assertThat(found).containsExactly(keep.toFile());
  }

  @Test
  void listBslFilesSkipsExcludedDirectoriesByDoubleStarSuffix(@TempDir Path projectRoot) throws IOException {
    Path keep = createBslFile(projectRoot, SRC_MODULE_BSL);
    createBslFile(projectRoot, "repo/.git/refs/HEAD.bsl");

    var found = BSLFiles.listBslFiles(projectRoot, List.of("**/.git/**"));

    assertThat(found).containsExactly(keep.toFile());
  }

  @Test
  void listBslFilesIgnoresFilesNotInBslExtensions(@TempDir Path projectRoot) throws IOException {
    Path bsl = createBslFile(projectRoot, SRC_MODULE_BSL);
    Files.writeString(bsl.resolveSibling("notes.txt"), "ignored");

    var found = BSLFiles.listBslFiles(projectRoot, null);

    assertThat(found).containsExactly(bsl.toFile());
  }

  private static Path createBslFile(Path root, String relative) throws IOException {
    var file = root.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, "// BSL");
    return file;
  }
}
