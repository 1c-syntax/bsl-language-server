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

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.SendErrorsMode;
import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import org.eclipse.lsp4j.ClientInfo;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class SentryScopeConfigurerTest {

  @Autowired
  private SentryScopeConfigurer sentryScopeConfigurer;

  @Autowired
  private ApplicationEventPublisher eventPublisher;

  @Autowired
  private LanguageServerConfiguration configuration;

  private final AtomicReference<SentryEvent> capturedEvent = new AtomicReference<>();

  @BeforeEach
  void setUp() {
    // Initialize Sentry with a test DSN and BeforeSend callback to capture events
    Sentry.init(options -> {
      options.setDsn("https://key@sentry.io/123");
      options.setBeforeSend((event, hint) -> {
        capturedEvent.set(event);
        return null; // Don't actually send
      });
    });
    configuration.setSendErrors(SendErrorsMode.SEND);
  }

  @AfterEach
  void tearDown() {
    capturedEvent.set(null);
    Sentry.close();
  }

  @Test
  void testClientInfoTagsSetInSentryScope() {
    // given
    var initializeParams = new InitializeParams();
    initializeParams.setClientInfo(new ClientInfo("Test Client", "1.2.3"));

    var event = new LanguageServerInitializeRequestReceivedEvent(
      mock(LanguageServer.class),
      initializeParams
    );

    // when - publish initialize event which sets tags in scope
    eventPublisher.publishEvent(event);

    // then - capture a Sentry event and verify tags are applied
    Sentry.captureMessage("test");

    assertThat(capturedEvent.get()).isNotNull();
    assertThat(capturedEvent.get().getTags()).containsEntry("client.name", "Test Client");
    assertThat(capturedEvent.get().getTags()).containsEntry("client.version", "1.2.3");
  }

  @Test
  void testClientInfoTagsSetToUnknownWhenClientInfoIsNull() {
    // given
    var initializeParams = new InitializeParams();
    // clientInfo is null by default

    var event = new LanguageServerInitializeRequestReceivedEvent(
      mock(LanguageServer.class),
      initializeParams
    );

    // when - publish initialize event which sets tags in scope
    eventPublisher.publishEvent(event);

    // then - capture a Sentry event and verify tags are set to UNKNOWN
    Sentry.captureMessage("test");

    assertThat(capturedEvent.get()).isNotNull();
    assertThat(capturedEvent.get().getTags()).containsEntry("client.name", "UNKNOWN");
    assertThat(capturedEvent.get().getTags()).containsEntry("client.version", "UNKNOWN");
  }

  @Test
  void testClientInfoTagsSetToUnknownWhenFieldsAreNull() {
    // given
    var initializeParams = new InitializeParams();
    var clientInfo = mock(ClientInfo.class);
    when(clientInfo.getName()).thenReturn(null);
    when(clientInfo.getVersion()).thenReturn(null);
    initializeParams.setClientInfo(clientInfo);

    var event = new LanguageServerInitializeRequestReceivedEvent(
      mock(LanguageServer.class),
      initializeParams
    );

    // when - publish initialize event which sets tags in scope
    eventPublisher.publishEvent(event);

    // then - capture a Sentry event and verify tags are set to UNKNOWN
    Sentry.captureMessage("test");

    assertThat(capturedEvent.get()).isNotNull();
    assertThat(capturedEvent.get().getTags()).containsEntry("client.name", "UNKNOWN");
    assertThat(capturedEvent.get().getTags()).containsEntry("client.version", "UNKNOWN");
  }
}
