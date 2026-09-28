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

import com.github._1c_syntax.bsl.languageserver.client.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.client.LanguageClientHolder;
import com.github._1c_syntax.bsl.languageserver.configuration.GlobalLanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.Resources;
import com.github._1c_syntax.bsl.languageserver.configuration.SendErrorsMode;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.protocol.SentryException;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ошибка, на которой пользователя спросили о разрешении отправки, проходит весь конвейер
 * Sentry — вместе с его дедупликацией — и уходит, когда пользователь отправку разрешил.
 */
@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class PermissionFilterSentryPipelineTest {

  @Autowired
  private PermissionFilterBeforeSendCallback permissionFilter;

  @Autowired
  private GlobalLanguageServerConfiguration configuration;

  @Autowired
  private LanguageClientHolder languageClientHolder;

  @Autowired
  private ClientCapabilitiesHolder clientCapabilitiesHolder;

  private final List<SentryEvent> allowed = new CopyOnWriteArrayList<>();

  @BeforeEach
  void initSentry() {
    // Что пропустил фильтр, запоминается и никуда не отправляется.
    Sentry.init(options -> {
      options.setDsn("https://key@sentry.io/123");
      options.setBeforeSend((event, hint) -> {
        var result = permissionFilter.execute(event, hint);
        if (result != null) {
          allowed.add(result);
        }
        return null;
      });
    });
    configuration.setSendErrors(SendErrorsMode.ASK);
    clientCapabilitiesHolder.setCapabilities(mock(ClientCapabilities.class));
  }

  @AfterEach
  void closeSentry() {
    Sentry.close();
  }

  @Test
  void firstErrorIsSentWhenUserAllowsLater() {
    // given: вопрос задан, ответа ещё нет.
    var question = new CompletableFuture<MessageActionItem>();
    connectClientAnswering(question);
    Sentry.captureException(new IllegalStateException("первая ошибка"));
    assertThat(allowed).isEmpty();

    // when
    question.complete(answer("answer_send"));

    // then: ушла именно первая ошибка — с описанием исключения, хотя дедупликация уже видела его.
    awaitSent(1);
    assertThat(allowed).singleElement()
      .satisfies(event -> assertThat(event.getExceptions())
        .extracting(SentryException::getValue)
        .contains("первая ошибка"));
  }

  @Test
  void burstOfErrorsWhileAskingIsSentAfterPermission() {
    // given: пока висит вопрос, ошибки идут пачкой — больше, чем копится.
    var question = new CompletableFuture<MessageActionItem>();
    var languageClient = connectClientAnswering(question);
    var burst = PermissionFilterBeforeSendCallback.MAX_POSTPONED_EVENTS + 50;
    for (var number = 0; number < burst; number++) {
      Sentry.captureException(new IllegalStateException("ошибка " + number));
    }
    assertThat(allowed).isEmpty();

    // when
    question.complete(answer("answer_send"));

    // then: спросили один раз, ушли накопленные — по порядку, с первой ошибки.
    awaitSent(PermissionFilterBeforeSendCallback.MAX_POSTPONED_EVENTS);
    verify(languageClient, times(1)).showMessageRequest(any());
    assertThat(allowed.get(0).getExceptions())
      .extracting(SentryException::getValue)
      .contains("ошибка 0");
  }

  @Test
  void firstErrorIsSentOnceWhenUserAllowsOnce() {
    // given
    var question = new CompletableFuture<MessageActionItem>();
    connectClientAnswering(question);
    Sentry.captureException(new IllegalStateException("первая ошибка"));

    // when
    question.complete(answer("answer_sendOnce"));

    // then: разрешение на один раз израсходовано этой ошибкой.
    awaitSent(1);
    assertThat(configuration.getSendErrors()).isEqualTo(SendErrorsMode.ASK);
  }

  @Test
  void firstErrorIsNotSentWhenUserDeclines() {
    // given
    var question = new CompletableFuture<MessageActionItem>();
    connectClientAnswering(question);
    Sentry.captureException(new IllegalStateException("первая ошибка"));

    // when
    question.complete(answer("answer_dontSend"));

    // then
    assertThat(allowed).isEmpty();
    assertThat(configuration.getSendErrors()).isEqualTo(SendErrorsMode.NEVER);
  }

  @Test
  void errorAnsweredAtOnceIsSentExactlyOnce() {
    // given: ответ уже на руках к моменту вопроса.
    connectClientAnswering(CompletableFuture.completedFuture(answer("answer_send")));

    // when
    Sentry.captureException(new IllegalStateException("первая ошибка"));

    // then
    awaitSent(1);
  }

  /** Дождаться, пока уйдёт ровно столько событий: повторная отправка идёт в другом потоке. */
  private void awaitSent(int count) {
    await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(allowed).hasSize(count));
  }

  private LanguageClient connectClientAnswering(CompletableFuture<MessageActionItem> question) {
    var languageClient = mock(LanguageClient.class);
    when(languageClient.showMessageRequest(any())).thenReturn(question);
    languageClientHolder.connect(languageClient);
    return languageClient;
  }

  private MessageActionItem answer(String key) {
    return new MessageActionItem(Resources.getResourceString(
      configuration.getLanguage(), PermissionFilterBeforeSendCallback.class, key));
  }
}
