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

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceBeanScope;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ServerContextProviderLifecycleTest {

  @Test
  void removalKeepsWorkspaceAvailableUntilPreparationHandlerCompletes(@TempDir Path root) {
    var context = mock(ServerContext.class);
    var scope = mock(WorkspaceBeanScope.class);
    var beans = new StaticListableBeanFactory();
    beans.addBean("serverContext", context);
    var provider = new ServerContextProvider(beans.getBeanProvider(ServerContext.class),
      mock(LanguageServerConfiguration.class), scope);
    var uri = Absolute.uri(root.toUri());
    var folder = new WorkspaceFolder(uri.toString(), "preparing");
    provider.addWorkspace(folder);
    try {
      doAnswer(invocation -> {
        // Registration may start after removal has begun and resolve its context then.
        var preparing = provider.getAllContexts().get(uri);
        assertThat(preparing).isSameAs(context);
        verify(context, never()).clear();
        verify(scope, never()).removeWorkspace(uri);
        preparing.finishPreparation();
        return null;
      }).when(context).awaitPreparation();

      provider.removeWorkspace(folder);

      assertThat(provider.getAllContexts()).doesNotContainKey(uri);
      var order = inOrder(context, scope);
      order.verify(context).awaitPreparation();
      order.verify(context).finishPreparation();
      order.verify(context).clear();
      order.verify(scope).removeWorkspace(uri);
    } finally {
      WorkspaceContextHolder.unregisterWorkspace(uri);
    }
  }
}
