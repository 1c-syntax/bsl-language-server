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
package com.github._1c_syntax.bsl.languageserver.infrastructure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Тесты для {@link WorkspaceThreadLocalAccessor} — Micrometer SPI,
 * пропагирующего workspace URI через executor'ы.
 * <p>
 * Особое внимание уделяется методу {@link WorkspaceThreadLocalAccessor#restore(URI)},
 * который вызывается Micrometer при закрытии scope. Этот метод не должен бросать
 * исключение, если workspace был снят с регистрации пока задача выполнялась
 * (см. баг: {@code WorkspaceContextHolder.set(URI)} бросал {@code IllegalStateException}
 * для незарегистрированного workspace во время restore).
 */
class WorkspaceThreadLocalAccessorTest {

  private static final URI REGISTERED_URI = URI.create("file:///registered-workspace/");
  private static final URI UNREGISTERED_URI = URI.create("file:///gone-workspace/");
  private static final String WORKSPACE_NAME = "registered-workspace";

  private final WorkspaceThreadLocalAccessor accessor = new WorkspaceThreadLocalAccessor();

  @BeforeEach
  void setUp() {
    WorkspaceContextHolder.clear();
    WorkspaceContextHolder.registerWorkspace(REGISTERED_URI, WORKSPACE_NAME);
  }

  @AfterEach
  void tearDown() {
    WorkspaceContextHolder.clear();
    WorkspaceContextHolder.unregisterWorkspace(REGISTERED_URI);
    WorkspaceContextHolder.unregisterWorkspace(UNREGISTERED_URI);
  }

  @Test
  void getValue_returnsCurrentWorkspaceUri() {
    WorkspaceContextHolder.set(REGISTERED_URI, WORKSPACE_NAME);
    assertThat(accessor.getValue()).isEqualTo(REGISTERED_URI);
  }

  @Test
  void getValue_returnsNullWhenNotSet() {
    assertThat(accessor.getValue()).isNull();
  }

  /**
   * Регрессионный тест: setValue(registeredUri) должен устанавливать контекст.
   */
  @Test
  void setValue_withUri_setsContext() {
    accessor.setValue(REGISTERED_URI);
    assertThat(WorkspaceContextHolder.get()).isEqualTo(REGISTERED_URI);
  }

  @Test
  void setValue_noArg_clearsContext() {
    WorkspaceContextHolder.set(REGISTERED_URI, WORKSPACE_NAME);
    accessor.setValue();
    assertThat(WorkspaceContextHolder.get()).isNull();
  }

  /**
   * Регрессионный тест: restore(registeredUri) должен восстанавливать контекст.
   */
  @Test
  void restore_withRegisteredUri_setsContext() {
    accessor.restore(REGISTERED_URI);
    assertThat(WorkspaceContextHolder.get()).isEqualTo(REGISTERED_URI);
  }

  @Test
  void restore_withUnregisteredUri_throwsIllegalStateException() {
    WorkspaceContextHolder.registerWorkspace(UNREGISTERED_URI, "gone");
    WorkspaceContextHolder.unregisterWorkspace(UNREGISTERED_URI);

    assertThatThrownBy(() -> accessor.restore(UNREGISTERED_URI))
      .isInstanceOf(IllegalStateException.class);
  }
}
