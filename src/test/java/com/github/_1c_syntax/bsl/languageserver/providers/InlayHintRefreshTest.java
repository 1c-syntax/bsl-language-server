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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.client.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.client.LanguageClientHolder;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.events.ConfigurationTypesRegisteredEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextPopulatedEvent;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.InlayHintWorkspaceCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Подсказки о типах переменных и параметрах вызовов опираются на индекс ссылок и реестр типов,
 * которые заполняются после открытия документа: по их готовности клиент обновляет подсказки.
 */
@SpringBootTest
@DirtiesContext
class InlayHintRefreshTest {

  @Autowired
  private InlayHintProvider inlayHintProvider;
  @Autowired
  private ClientCapabilitiesHolder clientCapabilitiesHolder;
  @Autowired
  private LanguageClientHolder clientHolder;

  private LanguageClient languageClient;

  @BeforeEach
  void connectClientWithRefreshSupport() {
    languageClient = mock(LanguageClient.class);
    clientHolder.connect(languageClient);
    var workspaceClientCapabilities = new WorkspaceClientCapabilities();
    workspaceClientCapabilities.setInlayHint(new InlayHintWorkspaceCapabilities(true));
    clientCapabilitiesHolder.setCapabilities(new ClientCapabilities(
      workspaceClientCapabilities,
      mock(TextDocumentClientCapabilities.class),
      mock(Object.class)
    ));
  }

  @Test
  void inlayHintsRefreshWhenServerContextIsPopulated() {
    inlayHintProvider.handleServerContextPopulated(new ServerContextPopulatedEvent(mock(ServerContext.class)));

    verify(languageClient).refreshInlayHints();
  }

  @Test
  void inlayHintsRefreshWhenConfigurationTypesAreRegistered() {
    inlayHintProvider.handleConfigurationTypesRegistered(new ConfigurationTypesRegisteredEvent(mock(ServerContext.class)));

    verify(languageClient).refreshInlayHints();
  }
}
