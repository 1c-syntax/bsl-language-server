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
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspaceContextHolderTest {

  private static final String URI_1 = "file:///workspace1";
  private static final String URI_2 = "file:///workspace2";
  private static final String NAME_1 = "workspace1";
  private static final String NAME_2 = "workspace2";

  @AfterEach
  void tearDown() {
    WorkspaceContextHolder.clear();
  }

  // forUri (AutoCloseable) tests

  @Test
  void forUri_setsAndClearsContext() {
    assertThat(WorkspaceContextHolder.get()).isNull();

    try (var ctx = WorkspaceContextHolder.forUri(URI_1)) {
      assertThat(WorkspaceContextHolder.get()).isEqualTo(URI_1);
      assertThat(WorkspaceContextHolder.getName()).isEqualTo(NAME_1);
    }

    assertThat(WorkspaceContextHolder.get()).isNull();
    assertThat(WorkspaceContextHolder.getName()).isNull();
  }

  @Test
  void forUri_withName_setsAndClearsContext() {
    try (var ctx = WorkspaceContextHolder.forUri(URI_1, "custom-name")) {
      assertThat(WorkspaceContextHolder.get()).isEqualTo(URI_1);
      assertThat(WorkspaceContextHolder.getName()).isEqualTo("custom-name");
    }

    assertThat(WorkspaceContextHolder.get()).isNull();
  }

  @Test
  void forUri_restoresPreviousValue() {
    WorkspaceContextHolder.set(URI_1, NAME_1);

    try (var ctx = WorkspaceContextHolder.forUri(URI_2, NAME_2)) {
      assertThat(WorkspaceContextHolder.get()).isEqualTo(URI_2);
      assertThat(WorkspaceContextHolder.getName()).isEqualTo(NAME_2);
    }

    assertThat(WorkspaceContextHolder.get()).isEqualTo(URI_1);
    assertThat(WorkspaceContextHolder.getName()).isEqualTo(NAME_1);
  }

  @Test
  void forUri_nestedContexts() {
    try (var outer = WorkspaceContextHolder.forUri(URI_1, NAME_1)) {
      assertThat(WorkspaceContextHolder.get()).isEqualTo(URI_1);

      try (var inner = WorkspaceContextHolder.forUri(URI_2, NAME_2)) {
        assertThat(WorkspaceContextHolder.get()).isEqualTo(URI_2);
      }

      assertThat(WorkspaceContextHolder.get()).isEqualTo(URI_1);
      assertThat(WorkspaceContextHolder.getName()).isEqualTo(NAME_1);
    }

    assertThat(WorkspaceContextHolder.get()).isNull();
  }

  @Test
  void forUri_cleansUpOnException() {
    try {
      try (var ctx = WorkspaceContextHolder.forUri(URI_1)) {
        throw new RuntimeException("test exception");
      }
    } catch (RuntimeException ignored) {
      // expected
    }

    assertThat(WorkspaceContextHolder.get()).isNull();
  }

  // run() tests

  @Test
  void run_setsAndClearsContext() {
    var captured = new AtomicReference<String>();

    WorkspaceContextHolder.run(URI_1, () -> captured.set(WorkspaceContextHolder.get()));

    assertThat(captured.get()).isEqualTo(URI_1);
    assertThat(WorkspaceContextHolder.get()).isNull();
  }

  @Test
  void run_withName_setsAndClearsContext() {
    var capturedName = new AtomicReference<String>();

    WorkspaceContextHolder.run(URI_1, "custom-name",
      () -> capturedName.set(WorkspaceContextHolder.getName()));

    assertThat(capturedName.get()).isEqualTo("custom-name");
    assertThat(WorkspaceContextHolder.get()).isNull();
  }

  @Test
  void run_restoresPreviousValue() {
    WorkspaceContextHolder.set(URI_1, NAME_1);

    WorkspaceContextHolder.run(URI_2, () ->
      assertThat(WorkspaceContextHolder.get()).isEqualTo(URI_2));

    assertThat(WorkspaceContextHolder.get()).isEqualTo(URI_1);
    assertThat(WorkspaceContextHolder.getName()).isEqualTo(NAME_1);
  }

  @Test
  void run_cleansUpOnException() {
    assertThatThrownBy(() ->
      WorkspaceContextHolder.run(URI_1, () -> {
        throw new RuntimeException("test exception");
      })
    ).isInstanceOf(RuntimeException.class);

    assertThat(WorkspaceContextHolder.get()).isNull();
  }

  // call() tests

  @Test
  void call_setsContextAndReturnsValue() throws Exception {
    var result = WorkspaceContextHolder.call(URI_1, WorkspaceContextHolder::get);

    assertThat(result).isEqualTo(URI_1);
    assertThat(WorkspaceContextHolder.get()).isNull();
  }

  @Test
  void call_withName_setsContextAndReturnsValue() throws Exception {
    var result = WorkspaceContextHolder.call(URI_1, "custom-name", WorkspaceContextHolder::getName);

    assertThat(result).isEqualTo("custom-name");
    assertThat(WorkspaceContextHolder.get()).isNull();
  }

  @Test
  void call_restoresPreviousValue() throws Exception {
    WorkspaceContextHolder.set(URI_1, NAME_1);

    var result = WorkspaceContextHolder.call(URI_2, WorkspaceContextHolder::get);

    assertThat(result).isEqualTo(URI_2);
    assertThat(WorkspaceContextHolder.get()).isEqualTo(URI_1);
    assertThat(WorkspaceContextHolder.getName()).isEqualTo(NAME_1);
  }

  @Test
  void call_cleansUpOnException() {
    assertThatThrownBy(() ->
      WorkspaceContextHolder.call(URI_1, () -> {
        throw new Exception("checked exception");
      })
    ).isInstanceOf(Exception.class);

    assertThat(WorkspaceContextHolder.get()).isNull();
  }

  // extractName tests

  @Test
  void forUri_extractsNameFromUri() {
    try (var ctx = WorkspaceContextHolder.forUri("file:///path/to/my-project")) {
      assertThat(WorkspaceContextHolder.getName()).isEqualTo("my-project");
    }
  }

  @Test
  void forUri_extractsNameFromUriWithTrailingSlash() {
    try (var ctx = WorkspaceContextHolder.forUri("file:///path/to/my-project/")) {
      assertThat(WorkspaceContextHolder.getName()).isEqualTo("my-project");
    }
  }
}
