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
package com.github._1c_syntax.bsl.languageserver.lsp;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextPopulatedEvent;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.lsp.client.LanguageClientHolder;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class AnalyzeProjectOnStartTest {

  private static final URI TEST_WORKSPACE_URI = Absolute.uri("file:///test-analyze-workspace");

  @Autowired
  private AnalyzeProjectOnStart analyzeProjectOnStart;

  @Autowired
  private ServerContextProvider serverContextProvider;

  @MockitoBean
  private LanguageClient languageClient;

  @Autowired
  private LanguageClientHolder languageClientHolder;

  private ServerContext serverContext;
  @Autowired
  private LanguageServerConfiguration configuration;

  @BeforeEach
  void setUp() {
    serverContextProvider.clear();
    var realContext = serverContextProvider.addWorkspace(TEST_WORKSPACE_URI);
    serverContext = Mockito.spy(realContext);
    WorkspaceContextHolder.set(realContext.getWorkspaceUri());
  }

  @AfterEach
  void tearDown() {
    WorkspaceContextHolder.clear();
  }

  @Test
  void noExecutionIfDisabled() {
    // given
    configuration.getDiagnosticsOptions().setAnalyzeOnStart(false);
    languageClientHolder.connect(languageClient);

    // when
    analyzeProjectOnStart.handleEvent(new ServerContextPopulatedEvent(serverContext));

    verify(serverContext, never()).getDocuments(any());
    verify(languageClient, never()).publishDiagnostics(any());
  }

  @Test
  void noExecutionIfLanguageClientIsNotConnected() {
    // given
    configuration.getDiagnosticsOptions().setAnalyzeOnStart(true);
    languageClientHolder.connect(null);

    // when
    analyzeProjectOnStart.handleEvent(new ServerContextPopulatedEvent(serverContext));

    verify(serverContext, never()).getDocuments(any());
    verify(languageClient, never()).publishDiagnostics(any());
  }

  @Test
  void runAnalysisIfEnabled() {
    // given
    configuration.getDiagnosticsOptions().setAnalyzeOnStart(true);
    languageClientHolder.connect(languageClient);

    TestUtils.getDocumentContext("A = 0", serverContext);

    // when
    analyzeProjectOnStart.handleEvent(new ServerContextPopulatedEvent(serverContext));

    verify(serverContext, times(1)).getDocuments();
    verify(languageClient, times(1)).publishDiagnostics(any());
  }
}
