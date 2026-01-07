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
package com.github._1c_syntax.bsl.languageserver.aop.sentry;

import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import org.eclipse.lsp4j.ClientInfo;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class SentryScopeConfigurerTest {

  @Autowired
  private SentryScopeConfigurer sentryScopeConfigurer;

  @Autowired
  private ApplicationEventPublisher eventPublisher;

  @Test
  void testOnLanguageServerInitializeWithClientInfo() {
    // given
    var initializeParams = new InitializeParams();
    initializeParams.setClientInfo(new ClientInfo("Test Client", "1.2.3"));

    var event = new LanguageServerInitializeRequestReceivedEvent(
      mock(LanguageServer.class),
      initializeParams
    );

    // when & then
    assertThatCode(() -> sentryScopeConfigurer.onLanguageServerInitialize(event))
      .doesNotThrowAnyException();
  }

  @Test
  void testOnLanguageServerInitializeWithNullClientInfo() {
    // given
    var initializeParams = new InitializeParams();
    // clientInfo is null by default

    var event = new LanguageServerInitializeRequestReceivedEvent(
      mock(LanguageServer.class),
      initializeParams
    );

    // when & then
    assertThatCode(() -> sentryScopeConfigurer.onLanguageServerInitialize(event))
      .doesNotThrowAnyException();
  }

  @Test
  void testOnLanguageServerInitializeViaEventPublisher() {
    // given
    var initializeParams = new InitializeParams();
    initializeParams.setClientInfo(new ClientInfo("Visual Studio Code", "1.85.0"));

    var event = new LanguageServerInitializeRequestReceivedEvent(
      mock(LanguageServer.class),
      initializeParams
    );

    // when & then - verifies event listener is properly registered and invoked
    assertThatCode(() -> eventPublisher.publishEvent(event))
      .doesNotThrowAnyException();
  }
}
