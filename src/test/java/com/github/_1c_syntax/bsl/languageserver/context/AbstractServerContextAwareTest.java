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
package com.github._1c_syntax.bsl.languageserver.context;

import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.annotation.PostConstruct;
import java.nio.file.Path;

@SpringBootTest
public abstract class AbstractServerContextAwareTest {

  private static final String TEST_WORKSPACE_NAME = "test-workspace";

  @Autowired
  protected ServerContextProvider serverContextProvider;

  protected ServerContext context;

  @PostConstruct
  public void abstractServerContextAwareTestInit() {
    // Clear previous state
    serverContextProvider.clear();
  }

  protected void initServerContext(String path) {
    var configurationRoot = Absolute.path(path);
    initServerContext(configurationRoot);
  }

  protected void initServerContext(Path configurationRoot) {
    // Register workspace and get context from provider
    var workspaceFolder = new WorkspaceFolder(configurationRoot.toUri().toString(), TEST_WORKSPACE_NAME);
    context = serverContextProvider.addWorkspace(workspaceFolder);
    context.setConfigurationRoot(configurationRoot);
    context.populateContext();
  }
}
