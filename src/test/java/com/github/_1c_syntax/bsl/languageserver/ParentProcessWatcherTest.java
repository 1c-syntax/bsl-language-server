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
package com.github._1c_syntax.bsl.languageserver;

import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class ParentProcessWatcherTest {

  @InjectMocks
  private ParentProcessWatcher parentProcessWatcher;

  @Mock
  private LanguageServer languageServer;

  @Test
  void testParentProcessIsDead() {
    // given
    var params = new InitializeParams();
    params.setProcessId(-1);

    var event = new LanguageServerInitializeRequestReceivedEvent(languageServer, params);
    parentProcessWatcher.handleEvent(event);

    // when
    parentProcessWatcher.watch();

    // then
    verify(languageServer, times(1)).exit();
  }

  @Test
  void testParentProcessIsAlive() {
    // given
    var params = new InitializeParams();
    params.setProcessId((int) ProcessHandle.current().pid());

    var event = new LanguageServerInitializeRequestReceivedEvent(languageServer, params);
    parentProcessWatcher.handleEvent(event);

    // when
    parentProcessWatcher.watch();

    // then
    verify(languageServer, never()).exit();
  }

}