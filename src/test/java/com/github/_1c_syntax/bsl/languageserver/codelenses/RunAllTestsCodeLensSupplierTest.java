/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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
package com.github._1c_syntax.bsl.languageserver.codelenses;

import com.github._1c_syntax.bsl.languageserver.codelenses.testrunner.TestRunnerAdapter;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.ClientInfo;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class RunAllTestsCodeLensSupplierTest {

  @Autowired
  private RunAllTestsCodeLensSupplier supplier;

  @Autowired
  private ApplicationEventPublisher eventPublisher;

  @SpyBean
  private TestRunnerAdapter testRunnerAdapter;

  private DocumentContext documentContext;

  @BeforeEach
  void init() {
    var filePath = "./src/test/resources/codelenses/RunAllTestsCodeLensSupplier.os";
    documentContext = TestUtils.getDocumentContextFromFile(filePath);
  }

  @Test
  void noLensesIfClientIsNotSupported() {
    // given
    initializeServer("Unknown client");

    // when
    var codeLenses = supplier.getCodeLenses(documentContext);

    // then
    assertThat(codeLenses).isEmpty();
  }

  @Test
  void testDryRun() {
    // given
    initializeServer("Visual Studio Code");

    // when
    var codeLenses = supplier.getCodeLenses(documentContext);

    // then
    assertThat(codeLenses).isNotNull();
  }

  @Test
  void testRunWithMockedTestIds() {
    // given
    initializeServer("Visual Studio Code");

    when(testRunnerAdapter.getTestIds(documentContext))
      .thenReturn(List.of("testName"));

    // when
    var codeLenses = supplier.getCodeLenses(documentContext);

    // then
    assertThat(codeLenses).isNotNull();
  }

  @Test
  void testResolve() {
    // given
    CodeLens codeLens = new CodeLens();
    DefaultCodeLensData codeLensData = new DefaultCodeLensData(
      documentContext.getUri(),
      supplier.getId()
    );

    // when
    var resolvedCodeLens = supplier.resolve(documentContext, codeLens, codeLensData);

    // then
    assertThat(resolvedCodeLens.getCommand()).isNotNull();
  }

  private void initializeServer(String clientName) {
    var initializeParams = new InitializeParams();
    initializeParams.setClientInfo(
      new ClientInfo(clientName, "1.0.0")
    );

    var event = new LanguageServerInitializeRequestReceivedEvent(
      mock(LanguageServer.class),
      initializeParams
    );
    eventPublisher.publishEvent(event);
  }
}