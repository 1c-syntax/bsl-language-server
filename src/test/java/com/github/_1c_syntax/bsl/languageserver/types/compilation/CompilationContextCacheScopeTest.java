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
package com.github._1c_syntax.bsl.languageserver.types.compilation;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CompilationContextCacheScopeTest extends AbstractServerContextAwareTest {
  @Autowired
  private ApplicationContext application;
  @Autowired
  private ServerContextProvider provider;
  @Autowired
  private CompilationContextResolver resolver;

  @Test
  void declarationsAreSharedWithinAWorkspaceAndIsolatedBetweenWorkspaces(@TempDir Path root) throws Exception {
    var first = provider.addWorkspace(Files.createDirectory(root.resolve("first")).toUri());
    var second = provider.addWorkspace(Files.createDirectory(root.resolve("second")).toUri());
    first.awaitPreparation();
    second.awaitPreparation();
    var one = TestUtils.getDocumentContext(root.resolve("first/module.bsl").toUri(),
      "Процедура Первый()\nКонецПроцедуры", first);
    var two = TestUtils.getDocumentContext(root.resolve("second/module.bsl").toUri(),
      "Процедура Второй()\nКонецПроцедуры", second);
    var cache = application.getBean("compilationContextDeclarationsCache", Cache.class);
    try (var scope = WorkspaceContextHolder.forUri(first.getWorkspaceUri())) {
      var declarations = resolver.declarations(one);
      assertThat(resolver.declarations(one)).isSameAs(declarations);
      assertThat(cache.getIfPresent(one.getSymbolTree())).isSameAs(declarations);
    }
    try (var scope = WorkspaceContextHolder.forUri(second.getWorkspaceUri())) {
      assertThat(cache.getIfPresent(one.getSymbolTree())).isNull();
      assertThat(resolver.declarations(two)).containsKey("Второй").doesNotContainKey("Первый");
    }
    try (var scope = WorkspaceContextHolder.forUri(first.getWorkspaceUri())) {
      assertThat(cache.getIfPresent(two.getSymbolTree())).isNull();
      assertThat(resolver.declarations(one)).containsKey("Первый").doesNotContainKey("Второй");
    }
  }
}
