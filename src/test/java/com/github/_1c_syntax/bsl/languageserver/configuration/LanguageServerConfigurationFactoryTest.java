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
package com.github._1c_syntax.bsl.languageserver.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LanguageServerConfigurationFactoryTest {

  @Autowired
  private LanguageServerConfigurationFactory factory;

  @TempDir
  Path workspaceDir;

  @Test
  void testCreateConfigurationReturnsNonNull() {
    // when
    var configuration = factory.createConfiguration(workspaceDir);

    // then
    assertThat(configuration).isNotNull();
  }

  @Test
  void testCreateConfigurationReturnsSameInstanceForSameWorkspace() {
    // when
    var configuration1 = factory.createConfiguration(workspaceDir);
    var configuration2 = factory.createConfiguration(workspaceDir);

    // then - workspace-scoped: same proxy for same workspace context
    assertThat(configuration1).isSameAs(configuration2);
  }

  @Test
  void testCreateConfigurationHasDefaultValues() {
    // when
    var configuration = factory.createConfiguration(workspaceDir);

    // then - should have default values
    assertThat(configuration.getLanguage()).isNotNull();
    assertThat(configuration.getDiagnosticsOptions()).isNotNull();
    assertThat(configuration.getCodeLensOptions()).isNotNull();
  }
}
