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
package com.github._1c_syntax.bsl.languageserver.types.oscript;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class LibConfigDiscoveryTest extends AbstractServerContextAwareTest {

  @Autowired
  private LibConfigDiscovery discovery;

  @Test
  void discoversLibConfigInsideWorkspace(@TempDir Path workspace) throws IOException {
    var libDir = workspace.resolve("oscript-libs/mylib");
    Files.createDirectories(libDir);
    var libConfig = libDir.resolve("lib.config");
    Files.writeString(libConfig, "<package-def/>");

    var nested = workspace.resolve("a/b/c/d/deeplib");
    Files.createDirectories(nested);
    Files.writeString(nested.resolve("lib.config"), "<package-def/>");

    var result = discovery.discover(workspace);

    assertThat(result).contains(libConfig.toAbsolutePath().normalize());
    assertThat(result).contains(nested.resolve("lib.config").toAbsolutePath().normalize());
  }

  @Test
  void deduplicatesPaths(@TempDir Path workspace) throws IOException {
    var libDir = workspace.resolve("lib");
    Files.createDirectories(libDir);
    Files.writeString(libDir.resolve("lib.config"), "<package-def/>");

    var result = discovery.discover(workspace);

    assertThat(result).hasSize(1);
  }

  @Test
  void exposesOscriptModulesChildrenAsRoots(@TempDir Path workspace) throws IOException {
    var fsLib = workspace.resolve("oscript_modules/fs/Модули");
    Files.createDirectories(fsLib);
    Files.writeString(fsLib.resolve("ФС.os"), "// pass");

    var roots = discovery.getRoots(workspace);

    assertThat(roots).contains(
      workspace.toAbsolutePath().normalize(),
      workspace.resolve("oscript_modules/fs").toAbsolutePath().normalize()
    );
  }
}
