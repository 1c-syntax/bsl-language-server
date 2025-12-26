/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.context;

import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ServerContextProviderTest {

  @Autowired
  private ServerContextProvider serverContextProvider;

  @Test
  void testAddWorkspace() {
    // given
    var workspaceUri = Absolute.path(PATH_TO_METADATA).toUri().toString();
    var workspaceFolder = new WorkspaceFolder(workspaceUri, "test-workspace");

    // when
    var serverContext = serverContextProvider.addWorkspace(workspaceFolder);

    // then
    assertThat(serverContext).isNotNull();
    assertThat(serverContext.getConfigurationRoot()).isNotNull();
    assertThat(serverContextProvider.hasWorkspaces()).isTrue();

    // cleanup
    serverContextProvider.removeWorkspace(workspaceFolder);
  }

  @Test
  void testRemoveWorkspace() {
    // given
    var workspaceUri = Absolute.path(PATH_TO_METADATA).toUri().toString();
    var workspaceFolder = new WorkspaceFolder(workspaceUri, "test-workspace");
    serverContextProvider.addWorkspace(workspaceFolder);

    // when
    serverContextProvider.removeWorkspace(workspaceFolder);

    // then - workspace removed, should not find contexts
    assertThat(serverContextProvider.hasWorkspaces()).isFalse();
  }

  @Test
  void testGetServerContextForDocument() {
    // given
    var workspaceUri = Absolute.path(PATH_TO_METADATA).toUri().toString();
    var workspaceFolder = new WorkspaceFolder(workspaceUri, "test-workspace");
    serverContextProvider.addWorkspace(workspaceFolder);

    var documentUri = Absolute.path(PATH_TO_METADATA).resolve("CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl").toUri();

    // when
    var foundContext = serverContextProvider.getServerContext(documentUri);

    // then
    assertThat(foundContext).isPresent();
    assertThat(foundContext.get()).isNotNull();

    // cleanup
    serverContextProvider.removeWorkspace(workspaceFolder);
  }

  @Test
  void testGetAllContexts() {
    // given
    var workspaceUri1 = Absolute.path(PATH_TO_METADATA).toUri().toString();
    var workspaceFolder1 = new WorkspaceFolder(workspaceUri1, "workspace-1");
    
    var workspaceUri2 = Absolute.path(PATH_TO_METADATA).getParent().toUri().toString();
    var workspaceFolder2 = new WorkspaceFolder(workspaceUri2, "workspace-2");

    // when
    serverContextProvider.addWorkspace(workspaceFolder1);
    serverContextProvider.addWorkspace(workspaceFolder2);

    // then
    var allContexts = serverContextProvider.getAllContexts();
    assertThat(allContexts).hasSize(2);

    // cleanup
    serverContextProvider.removeWorkspace(workspaceFolder1);
    serverContextProvider.removeWorkspace(workspaceFolder2);
  }

  @Test
  void testClearAllWorkspaces() {
    // given
    var workspaceUri = Absolute.path(PATH_TO_METADATA).toUri().toString();
    var workspaceFolder = new WorkspaceFolder(workspaceUri, "test-workspace");
    serverContextProvider.addWorkspace(workspaceFolder);

    // when
    serverContextProvider.clear();

    // then
    assertThat(serverContextProvider.hasWorkspaces()).isFalse();
    assertThat(serverContextProvider.getAllContexts()).isEmpty();
  }
}
