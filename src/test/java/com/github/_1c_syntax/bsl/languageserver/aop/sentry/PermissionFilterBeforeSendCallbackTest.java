/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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

import com.github._1c_syntax.bsl.languageserver.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.LanguageClientHolder;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.SendErrorsMode;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class PermissionFilterBeforeSendCallbackTest {

  @Autowired
  private PermissionFilterBeforeSendCallback permissionFilter;

  @Autowired
  private LanguageServerConfiguration configuration;

  @Autowired
  private LanguageClientHolder languageClientHolder;

  @Autowired
  private ClientCapabilitiesHolder clientCapabilitiesHolder;

  @Test
  void sendOnlyOnSendMode() {

    // given
    configuration.setSendErrors(SendErrorsMode.SEND);

    var event = new SentryEvent();

    // when
    var filteredEvent = permissionFilter.execute(event, mock(Hint.class));

    // then
    assertThat(filteredEvent).isNotNull();

  }

  @Test
  void dontSendIfClientIsNotConnectedAndModeIsAsk() {

    // given
    configuration.setSendErrors(SendErrorsMode.ASK);

    languageClientHolder.connect(null);

    var event = new SentryEvent();

    // when
    var filteredEvent = permissionFilter.execute(event, mock(Hint.class));

    // then
    assertThat(filteredEvent).isNull();

  }

  @Test
  void notSendInDontSendMode() {

    // given
    configuration.setSendErrors(SendErrorsMode.NEVER);

    var event = new SentryEvent();

    // when
    var filteredEvent = permissionFilter.execute(event, mock(Hint.class));

    // then
    assertThat(filteredEvent).isNull();

  }

  @Test
  void notSendIfUserDoesNotGivePermission() {

    // given
    configuration.setSendErrors(SendErrorsMode.ASK);

    var languageClient = mock(LanguageClient.class);
    var answerTitle = Resources.getResourceString(
      configuration.getLanguage(),
      PermissionFilterBeforeSendCallback.class,
      "answer_dontSend"
    );
    var answer = new MessageActionItem(answerTitle);
    when(languageClient.showMessageRequest(any())).thenReturn(CompletableFuture.completedFuture(answer));

    languageClientHolder.connect(languageClient);

    var event = new SentryEvent();

    // when
    var filteredEvent = permissionFilter.execute(event, mock(Hint.class));

    // then
    assertThat(filteredEvent).isNull();

  }

  @Test
  void notSendIfUserClosedQuestion() {

    // given
    configuration.setSendErrors(SendErrorsMode.ASK);

    var languageClient = mock(LanguageClient.class);
    when(languageClient.showMessageRequest(any())).thenReturn(CompletableFuture.completedFuture(new MessageActionItem()));

    languageClientHolder.connect(languageClient);

    var event = new SentryEvent();

    // when
    var filteredEvent = permissionFilter.execute(event, mock(Hint.class));

    // then
    assertThat(filteredEvent).isNull();

  }

  @Test
  void sendIfUserGavePermission() {

    // given
    configuration.setSendErrors(SendErrorsMode.ASK);

    var languageClient = mock(LanguageClient.class);
    var answerTitle = Resources.getResourceString(
      configuration.getLanguage(),
      PermissionFilterBeforeSendCallback.class,
      "answer_send"
    );
    var answer = new MessageActionItem(answerTitle);
    when(languageClient.showMessageRequest(any())).thenReturn(CompletableFuture.completedFuture(answer));

    languageClientHolder.connect(languageClient);
    clientCapabilitiesHolder.setCapabilities(mock(ClientCapabilities.class));

    var event = new SentryEvent();

    // when
    var filteredEvent = permissionFilter.execute(event, mock(Hint.class));

    // then
    assertThat(filteredEvent).isNotNull();
    assertThat(configuration.getSendErrors()).isEqualTo(SendErrorsMode.SEND);

  }

  @Test
  void dontAskIfServerWasNotInitialized() {

    // given
    configuration.setSendErrors(SendErrorsMode.ASK);

    var languageClient = mock(LanguageClient.class);
    var answerTitle = Resources.getResourceString(
      configuration.getLanguage(),
      PermissionFilterBeforeSendCallback.class,
      "answer_send"
    );
    var answer = new MessageActionItem(answerTitle);
    when(languageClient.showMessageRequest(any())).thenReturn(CompletableFuture.completedFuture(answer));

    languageClientHolder.connect(languageClient);
    clientCapabilitiesHolder.setCapabilities(null);

    var event = new SentryEvent();

    // when
    var filteredEvent = permissionFilter.execute(event, mock(Hint.class));

    // then
    assertThat(filteredEvent).isNull();
    assertThat(configuration.getSendErrors()).isEqualTo(SendErrorsMode.ASK);

  }

  @Test
  void notSendNextTimeIfUserGavePermissionOnce() {

    // given
    configuration.setSendErrors(SendErrorsMode.ASK);

    var languageClient = mock(LanguageClient.class);
    var answerTitle = Resources.getResourceString(
      configuration.getLanguage(),
      PermissionFilterBeforeSendCallback.class,
      "answer_sendOnce"
    );
    var answer = new MessageActionItem(answerTitle);
    when(languageClient.showMessageRequest(any())).thenReturn(CompletableFuture.completedFuture(answer));

    languageClientHolder.connect(languageClient);
    clientCapabilitiesHolder.setCapabilities(mock(ClientCapabilities.class));

    var event = new SentryEvent();

    // when
    var filteredEvent = permissionFilter.execute(event, mock(Hint.class));

    // then
    assertThat(filteredEvent).isNotNull();
    assertThat(configuration.getSendErrors()).isEqualTo(SendErrorsMode.ASK);
  }

  @Test
  void notSendIfUserSkippedButAsk() {

    // given
    configuration.setSendErrors(SendErrorsMode.ASK);

    var languageClient = mock(LanguageClient.class);
    var answerTitle = Resources.getResourceString(
      configuration.getLanguage(),
      PermissionFilterBeforeSendCallback.class,
      "answer_skip"
    );
    var answer = new MessageActionItem(answerTitle);
    when(languageClient.showMessageRequest(any())).thenReturn(CompletableFuture.completedFuture(answer));

    languageClientHolder.connect(languageClient);

    var event = new SentryEvent();

    // when
    var filteredEvent = permissionFilter.execute(event, mock(Hint.class));

    // then
    assertThat(filteredEvent).isNull();
    assertThat(configuration.getSendErrors()).isEqualTo(SendErrorsMode.ASK);
  }

}