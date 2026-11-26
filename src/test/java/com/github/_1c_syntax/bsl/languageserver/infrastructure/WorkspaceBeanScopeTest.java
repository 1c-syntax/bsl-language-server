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

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class WorkspaceBeanScopeTest {

  private static final URI WORKSPACE = URI.create("file:///ws");

  @Test
  void removeAlsoDropsDestructionCallback() {
    // given
    var scope = new WorkspaceBeanScope();
    var destroyed = new AtomicInteger();
    try (var ignored = WorkspaceContextHolder.forUri(WORKSPACE, "ws")) {
      var bean = new Object();
      scope.get("bean", () -> bean);
      scope.registerDestructionCallback("bean", destroyed::incrementAndGet);

      // when
      scope.remove("bean");
    }

    // then: контракт Scope.remove требует удалить и destruction callback,
    // поэтому teardown workspace его уже не выполнит.
    scope.removeWorkspace(WORKSPACE);
    assertThat(destroyed).hasValue(0);
  }

  @Test
  void removeWorkspaceRunsAllCallbacksEvenIfOneThrows() {
    // given
    var scope = new WorkspaceBeanScope();
    var destroyed = new AtomicInteger();
    try (var ignored = WorkspaceContextHolder.forUri(WORKSPACE, "ws")) {
      scope.get("boom", Object::new);
      scope.get("ok", Object::new);
      scope.registerDestructionCallback("boom", () -> {
        throw new IllegalStateException("boom");
      });
      scope.registerDestructionCallback("ok", destroyed::incrementAndGet);
    }

    // when / then: исключение одного callback не должно срывать остальные и очистку store.
    assertThatNoException().isThrownBy(() -> scope.removeWorkspace(WORKSPACE));
    assertThat(destroyed).hasValue(1);
    assertThat(scope.getRegisteredWorkspaceUris()).isEmpty();
  }
}
