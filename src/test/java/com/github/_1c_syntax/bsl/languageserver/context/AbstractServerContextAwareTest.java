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
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.nio.file.Path;

@SpringBootTest
public abstract class AbstractServerContextAwareTest {

  private static final URI EMPTY_WORKSPACE_URI = URI.create("file:///empty-workspace");

  @Autowired
  protected ServerContextProvider serverContextProvider;

  protected ServerContext context;

  @BeforeEach
  void resetContext() {
    // Reset context field to ensure clean state between tests
    context = null;
  }

  /**
   * Initialize empty server context without metadata.
   */
  protected void initServerContext() {
    serverContextProvider.clear();
    context = serverContextProvider.addWorkspace(EMPTY_WORKSPACE_URI);
  }

  protected void initServerContext(String path) {
    var configurationRoot = Absolute.path(path);
    initServerContext(configurationRoot, true);
  }

  protected void initServerContext(Path configurationRoot) {
    initServerContext(configurationRoot, true);
  }

  protected void initServerContext(String path, boolean populate) {
    var configurationRoot = Absolute.path(path);
    initServerContext(configurationRoot, populate);
  }

  protected void initServerContext(Path configurationRoot, boolean populate) {
    serverContextProvider.clear();
    context = serverContextProvider.addWorkspace(configurationRoot.toUri());
    context.setConfigurationRoot(configurationRoot);
    if (populate) {
      context.populateContext();
    }
  }
}
