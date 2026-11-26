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
package com.github._1c_syntax.bsl.languageserver.mcp.dto;

import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspaceFolderDtoTest {

  private static final java.net.URI FOLDER = Absolute.path("src/test/resources/cli").toUri();

  @AfterEach
  void tearDown() {
    WorkspaceContextHolder.unregisterWorkspace(FOLDER);
    WorkspaceContextHolder.clear();
  }

  @Test
  void describesRegisteredFolderWithItsName() {
    WorkspaceContextHolder.registerWorkspace(FOLDER, "Демо-конфигурация");

    assertThat(WorkspaceFolderDto.from(FOLDER))
      .isEqualTo(new WorkspaceFolderDto(FOLDER, "Демо-конфигурация"));
  }

  @Test
  void failsOnUnregisteredFolder() {
    assertThatThrownBy(() -> WorkspaceFolderDto.from(FOLDER))
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void snapshotKeepsRegisteredFolder() {
    WorkspaceContextHolder.registerWorkspace(FOLDER, "cli");

    assertThat(WorkspaceFolderDto.fromSnapshot(FOLDER))
      .contains(new WorkspaceFolderDto(FOLDER, "cli"));
  }

  @Test
  void snapshotDropsFolderUnregisteredMeanwhile() {
    // Снимок набора папок не замораживает реестр имён: папка, ушедшая между снимком и чтением
    // имени, обязана просто выпасть из выборки, а не рушить весь ответ инструмента.
    assertThat(WorkspaceFolderDto.fromSnapshot(FOLDER)).isEmpty();
  }
}
