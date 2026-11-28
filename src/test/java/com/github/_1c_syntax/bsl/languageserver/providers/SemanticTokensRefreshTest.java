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
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.events.LanguageServerConfigurationChangedEvent;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.SemanticTokensWorkspaceCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.annotation.DirtiesContext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@DirtiesContext
class SemanticTokensRefreshTest {

  @Autowired
  private LanguageServerConfiguration configuration;
  @Autowired
  private ApplicationEventPublisher applicationEventPublisher;
  @Autowired
  private ClientCapabilitiesHolder clientCapabilitiesHolder;
  @Autowired
  private LanguageClientHolder clientHolder;

  @Test
  void semanticTokensRefreshOnLanguageServerConfigurationChange() {
    // given — подсветка строк-шаблонов зависит от настройки semanticTokens.strTemplateMethods
    var languageClient = mock(LanguageClient.class);
    clientHolder.connect(languageClient);
    prepareRefreshSupport(true);

    // when
    applicationEventPublisher.publishEvent(new LanguageServerConfigurationChangedEvent(configuration));

    // then
    verify(languageClient).refreshSemanticTokens();
  }

  @Test
  void semanticTokensDoNotRefreshWithoutClientRefreshSupport() {
    // given
    var languageClient = mock(LanguageClient.class);
    clientHolder.connect(languageClient);
    prepareRefreshSupport(false);

    // when
    applicationEventPublisher.publishEvent(new LanguageServerConfigurationChangedEvent(configuration));

    // then
    verify(languageClient, never()).refreshSemanticTokens();
  }

  private void prepareRefreshSupport(boolean refreshSupport) {
    var workspaceClientCapabilities = new WorkspaceClientCapabilities();
    workspaceClientCapabilities.setSemanticTokens(new SemanticTokensWorkspaceCapabilities(refreshSupport));
    clientCapabilitiesHolder.setCapabilities(new ClientCapabilities(
      workspaceClientCapabilities,
      mock(TextDocumentClientCapabilities.class),
      mock(Object.class)
    ));
  }
}
