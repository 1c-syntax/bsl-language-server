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
package com.github._1c_syntax.bsl.languageserver.configuration.watcher;

import com.github._1c_syntax.bsl.languageserver.configuration.GlobalLanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {"app.scheduling.enabled=false"})
@DirtiesContext
class ConfigurationFileSystemWatcherTest {

  @Autowired
  private ConfigurationFileSystemWatcher watcher;

  @Autowired
  private GlobalLanguageServerConfiguration globalConfiguration;

  @Autowired
  private ServerContextProvider serverContextProvider;

  @TempDir
  Path workspaceDir;

  private ServerContext serverContext;

  @BeforeEach
  void setUp() {
    serverContextProvider.clear();
    serverContext = serverContextProvider.addWorkspace(workspaceDir.toUri());
    WorkspaceContextHolder.set(workspaceDir.toUri().toString());
  }

  @AfterEach
  void tearDown() {
    WorkspaceContextHolder.clear();
    serverContextProvider.clear();
  }

  @Test
  void testGlobalConfigFileChange() throws IOException {
    // given
    var file = File.createTempFile("bsl-config", ".json");
    var content = "{\"language\": \"ru\"}";
    FileUtils.writeStringToFile(file, content, StandardCharsets.UTF_8);
    globalConfiguration.update(file);

    // when
    content = "{\"language\": \"en\"}";
    FileUtils.writeStringToFile(file, content, StandardCharsets.UTF_8);

    await().atMost(10, SECONDS).untilAsserted(() -> {
      // when
      watcher.watch();
      // then
      assertThat(globalConfiguration.getLanguage()).isEqualTo(Language.EN);
    });

    // when
    FileUtils.delete(file);

    await().atMost(10, SECONDS).untilAsserted(() -> {
      // when
      watcher.watch();
      // then
      assertThat(globalConfiguration.getLanguage()).isEqualTo(Language.RU);
    });
  }

  @Test
  void testWorkspaceConfigFileChange() throws IOException {
    // given
    var workspaceUri = workspaceDir.toUri().toString();
    var configuration = serverContext.getLanguageServerConfiguration();
    var configFile = new File(workspaceDir.toFile(), ".bsl-language-server.json");
    var content = "{\"diagnostics\": {\"computeTrigger\": \"onType\"}}";
    FileUtils.writeStringToFile(configFile, content, StandardCharsets.UTF_8);
    configuration.update(configFile);

    // Initial check
    assertThat(configuration.getDiagnosticsOptions().getComputeTrigger().name())
      .isEqualTo("ONTYPE");

    // when - change config to never
    content = "{\"diagnostics\": {\"computeTrigger\": \"never\"}}";
    FileUtils.writeStringToFile(configFile, content, StandardCharsets.UTF_8);

    await().atMost(10, SECONDS).untilAsserted(() -> {
      WorkspaceContextHolder.set(workspaceUri);
      // when
      watcher.watch();
      // then
      assertThat(configuration.getDiagnosticsOptions().getComputeTrigger().name())
        .isEqualTo("NEVER");
    });

    // when - delete config
    FileUtils.delete(configFile);

    await().atMost(10, SECONDS).untilAsserted(() -> {
      WorkspaceContextHolder.set(workspaceUri);
      // when
      watcher.watch();
      // then - should return to default (ONSAVE)
      assertThat(configuration.getDiagnosticsOptions().getComputeTrigger().name())
        .isEqualTo("ONSAVE");
    });
  }
}