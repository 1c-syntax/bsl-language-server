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
class WorkspaceContextManagerTest {

  @Autowired
  private WorkspaceContextManager workspaceContextManager;

  @Test
  void testAddWorkspace() {
    // given
    var workspaceUri = Absolute.path(PATH_TO_METADATA).toUri().toString();
    var workspaceFolder = new WorkspaceFolder(workspaceUri, "test-workspace");

    // when
    var workspaceContext = workspaceContextManager.addWorkspace(workspaceFolder);

    // then
    assertThat(workspaceContext).isNotNull();
    assertThat(workspaceContext.getName()).isEqualTo("test-workspace");
    assertThat(workspaceContext.getServerContext()).isNotNull();
    assertThat(workspaceContextManager.hasWorkspaces()).isTrue();

    // cleanup
    workspaceContextManager.removeWorkspace(workspaceFolder);
  }

  @Test
  void testRemoveWorkspace() {
    // given
    var workspaceUri = Absolute.path(PATH_TO_METADATA).toUri().toString();
    var workspaceFolder = new WorkspaceFolder(workspaceUri, "test-workspace");
    workspaceContextManager.addWorkspace(workspaceFolder);

    // when
    workspaceContextManager.removeWorkspace(workspaceFolder);

    // then
    assertThat(workspaceContextManager.getWorkspace(URI.create(workspaceUri))).isNull();
  }

  @Test
  void testFindWorkspaceForDocument() {
    // given
    var workspaceUri = Absolute.path(PATH_TO_METADATA).toUri().toString();
    var workspaceFolder = new WorkspaceFolder(workspaceUri, "test-workspace");
    workspaceContextManager.addWorkspace(workspaceFolder);

    var documentUri = Absolute.path(PATH_TO_METADATA).resolve("CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl").toUri();

    // when
    var foundWorkspace = workspaceContextManager.findWorkspaceForDocument(documentUri);

    // then
    assertThat(foundWorkspace).isPresent();
    assertThat(foundWorkspace.get().getName()).isEqualTo("test-workspace");

    // cleanup
    workspaceContextManager.removeWorkspace(workspaceFolder);
  }

  @Test
  void testGetAllWorkspaces() {
    // given
    var workspaceUri1 = Absolute.path(PATH_TO_METADATA).toUri().toString();
    var workspaceFolder1 = new WorkspaceFolder(workspaceUri1, "workspace-1");
    
    var workspaceUri2 = Absolute.path(PATH_TO_METADATA).getParent().toUri().toString();
    var workspaceFolder2 = new WorkspaceFolder(workspaceUri2, "workspace-2");

    // when
    workspaceContextManager.addWorkspace(workspaceFolder1);
    workspaceContextManager.addWorkspace(workspaceFolder2);

    // then
    var allWorkspaces = workspaceContextManager.getAllWorkspaces();
    assertThat(allWorkspaces).hasSize(2);
    assertThat(allWorkspaces).extracting(WorkspaceContext::getName)
      .containsExactlyInAnyOrder("workspace-1", "workspace-2");

    // cleanup
    workspaceContextManager.removeWorkspace(workspaceFolder1);
    workspaceContextManager.removeWorkspace(workspaceFolder2);
  }

  @Test
  void testClearAllWorkspaces() {
    // given
    var workspaceUri = Absolute.path(PATH_TO_METADATA).toUri().toString();
    var workspaceFolder = new WorkspaceFolder(workspaceUri, "test-workspace");
    workspaceContextManager.addWorkspace(workspaceFolder);

    // when
    workspaceContextManager.clear();

    // then
    assertThat(workspaceContextManager.hasWorkspaces()).isFalse();
    assertThat(workspaceContextManager.getAllWorkspaces()).isEmpty();
  }
}
