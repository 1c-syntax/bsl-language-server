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
package com.github._1c_syntax.bsl.languageserver.codelenses;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.ClientInfo;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class AbstractRunTestsCodeLensSupplierTest extends AbstractServerContextAwareTest {

  @Autowired
  private AbstractRunTestsCodeLensSupplier<DefaultCodeLensData> supplier;

  @Autowired
  private ApplicationEventPublisher eventPublisher;

  @ParameterizedTest
  @CsvSource({
    "./src/test/resources/codelenses/AbstractRunTestCodeLensSupplier.os, unknown, false",
    "./src/test/resources/codelenses/tests/AbstractRunTestCodeLensSupplier.os, unknown, false",
    "./src/test/resources/codelenses/AbstractRunTestCodeLensSupplier.os, Visual Studio Code, false",
    "./src/test/resources/codelenses/tests/AbstractRunTestCodeLensSupplier.os, Visual Studio Code, true"
  })
  void testIsApplicable(String filePath, String clientName, boolean expected) {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(filePath);
    initializeServer("./src/test/resources/codelenses", clientName);

    // when
    var result = supplier.isApplicable(documentContext);

    // then
    assertThat(result).isEqualTo(expected);
  }

  private void initializeServer(String path, String clientName) {
    initServerContext(path);

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

  @TestConfiguration
  static class TestConfig {
    @Bean
    public AbstractRunTestsCodeLensSupplier<DefaultCodeLensData> supplier(LanguageServerConfiguration configuration) {
      return new AbstractRunTestsCodeLensSupplier<>(configuration) {

        @Override
        public List<CodeLens> getCodeLenses(DocumentContext documentContext) {
          return Collections.emptyList();
        }

        @Override
        public Class<DefaultCodeLensData> getCodeLensDataClass() {
          return DefaultCodeLensData.class;
        }

        @Override
        protected AbstractRunTestsCodeLensSupplier<DefaultCodeLensData> getSelf() {
          return this;
        }
      };
    }
  }

}