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
package com.github._1c_syntax.bsl.languageserver.util;

import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import lombok.NoArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

import java.net.URI;

/**
 * Устанавливает workspace ThreadLocal для всех @SpringBootTest тестов,
 * чтобы workspace-scoped proxy бины могли резолвиться.
 */
@NoArgsConstructor
public class WorkspaceContextTestExecutionListener extends AbstractTestExecutionListener {

  public static final URI DEFAULT_TEST_WORKSPACE = URI.create("file:///test-workspace");
  public static final String DEFAULT_TEST_WORKSPACE_NAME = "test-workspace";

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 2;
  }

  @Override
  public void prepareTestInstance(TestContext testContext) {
    ensureWorkspaceContext();
  }

  @Override
  public void beforeTestMethod(TestContext testContext) {
    ensureWorkspaceContext();
  }

  @Override
  public void afterTestMethod(TestContext testContext) {
    WorkspaceContextHolder.clear();
  }

  private void ensureWorkspaceContext() {
    if (WorkspaceContextHolder.get() == null) {
      WorkspaceContextHolder.registerWorkspace(DEFAULT_TEST_WORKSPACE, DEFAULT_TEST_WORKSPACE_NAME);
      WorkspaceContextHolder.set(DEFAULT_TEST_WORKSPACE);
    }
  }
}
