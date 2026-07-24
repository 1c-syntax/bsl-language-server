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

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import org.junit.jupiter.api.AfterEach;
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

  @Autowired
  private LanguageServerConfiguration configuration;

  @AfterEach
  void resetOscriptOptions() {
    configuration.getOscriptOptions().getLibRoots().clear();
    configuration.getOscriptOptions().setUseEnvLibLocation(false);
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

  @Test
  void getRootsFromNullServerContextReturnsOnlyLibRoots() {
    // given / when
    var roots = discovery.getRoots((ServerContext) null);

    // then — без workspace получаем только глобальные источники (libRoots/env)
    assertThat(roots).isNotNull();
  }

  @Test
  void getRootsIncludesAbsoluteLibRoot(@TempDir Path workspace, @TempDir Path libDir) {
    // given
    configuration.getOscriptOptions().getLibRoots().add(libDir.toAbsolutePath().toString());

    // when
    var roots = discovery.getRoots(workspace);

    // then
    assertThat(roots).contains(libDir.toAbsolutePath().normalize());
  }

  @Test
  void getRootsResolvesRelativeLibRootAgainstWorkspace(@TempDir Path workspace) throws IOException {
    // given
    Files.createDirectories(workspace.resolve("vendor/lib"));
    configuration.getOscriptOptions().getLibRoots().add("vendor/lib");

    // when
    var roots = discovery.getRoots(workspace);

    // then
    assertThat(roots).contains(workspace.resolve("vendor/lib").toAbsolutePath().normalize());
  }

  @Test
  void getRootsWithEnvLibLocationDisabledIgnoresEnv(@TempDir Path workspace) {
    // given — useEnvLibLocation=false (по умолчанию)
    configuration.getOscriptOptions().setUseEnvLibLocation(false);

    // when
    var roots = discovery.getRoots(workspace);

    // then — only workspace + oscript_modules children (нет лишних путей из env)
    assertThat(roots).hasSize(1).contains(workspace.toAbsolutePath().normalize());
  }

  @Test
  void getRootsHandlesWorkspaceWithoutOscriptModules(@TempDir Path workspace) {
    // given — нет oscript_modules директории

    // when
    var roots = discovery.getRoots(workspace);

    // then — только сам workspace
    assertThat(roots).containsExactly(workspace.toAbsolutePath().normalize());
  }
}
