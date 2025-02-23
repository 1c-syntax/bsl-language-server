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
package com.github._1c_syntax.bsl.languageserver.aop.sentry;

import com.github._1c_syntax.bsl.languageserver.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.LanguageClientHolder;
import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.SendErrorsMode;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions.BeforeSendCallback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ServerInfo;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Перехватчик сообщения в Sentry, выполняющий проверку получения явного разрешения
 * отправки данных в Sentry.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PermissionFilterBeforeSendCallback implements BeforeSendCallback {

  private static final Map<Language, Map<String, SendErrorsMode>> answers = createAnswersMap();

  private final LanguageServerConfiguration configuration;

  private final LanguageClientHolder languageClientHolder;

  private final ClientCapabilitiesHolder clientCapabilitiesHolder;

  private final ServerInfo serverInfo;

  private final AtomicBoolean questionWasSend = new AtomicBoolean(false);

  @Override
  public SentryEvent execute(SentryEvent event, Hint hint) {
    if (sendToSentry()) {
      return event;
    }

    return null;
  }

  private boolean sendToSentry() {
    if (configuration.getSendErrors() == SendErrorsMode.ASK) {
      if (!languageClientHolder.isConnected() || clientCapabilitiesHolder.getCapabilities().isEmpty()) {
        return false;
      }

      // if CAS returns false then question was already sent but no answer has received yet.
      // Otherwise, set atomic to true and send question.
      if (!questionWasSend.compareAndSet(false, true)) {
        return false;
      }

      languageClientHolder.execIfConnected((LanguageClient languageClient) -> {
        var sendQuestion = askUserForPermission(languageClient);
        var answerItem = waitForPermission(sendQuestion);
        Optional.ofNullable(answerItem)
          .map(MessageActionItem::getTitle)
          .map(title -> answers.get(configuration.getLanguage()).get(title))
          .ifPresent(configuration::setSendErrors);

        questionWasSend.set(false);
      });
    }

    var currentErrorsMode = configuration.getSendErrors();
    var result = currentErrorsMode == SendErrorsMode.SEND || currentErrorsMode == SendErrorsMode.SEND_ONCE;
    if (currentErrorsMode == SendErrorsMode.SEND_ONCE) {
      configuration.setSendErrors(SendErrorsMode.ASK);
    }

    return result;
  }

  private CompletableFuture<MessageActionItem> askUserForPermission(LanguageClient languageClient) {
    var message = Resources.getResourceString(
      configuration.getLanguage(),
      getClass(),
      "question",
      serverInfo.getName()
    );

    var actions = answers.get(configuration.getLanguage()).keySet().stream()
      .map(MessageActionItem::new)
      .collect(Collectors.toList());

    var requestParams = new ShowMessageRequestParams();
    requestParams.setType(MessageType.Error);
    requestParams.setMessage(message);
    requestParams.setActions(actions);

    return languageClient.showMessageRequest(requestParams);
  }

  @Nullable
  private MessageActionItem waitForPermission(CompletableFuture<MessageActionItem> sendQuestion) {
    try {
      return sendQuestion.get();
    } catch (InterruptedException e) {
      LOGGER.error("Can't wait for permission", e);
      questionWasSend.set(false);
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (ExecutionException e) {
      LOGGER.error("Can't execute permission request", e);
      questionWasSend.set(false);
      throw new IllegalStateException(e);
    }
  }

  private static Map<Language, Map<String, SendErrorsMode>> createAnswersMap() {
    return Map.of(
      Language.EN, getAnswersWithModes(Language.EN),
      Language.RU, getAnswersWithModes(Language.RU)
    );
  }

  private static Map<String, SendErrorsMode> getAnswersWithModes(Language language) {
    var clazz = PermissionFilterBeforeSendCallback.class;
    Map<String, SendErrorsMode> map = new LinkedHashMap<>();

    map.put(Resources.getResourceString(language, clazz, "answer_sendOnce"), SendErrorsMode.SEND_ONCE);
    map.put(Resources.getResourceString(language, clazz, "answer_skip"), SendErrorsMode.ASK);
    map.put(Resources.getResourceString(language, clazz, "answer_send"), SendErrorsMode.SEND);
    map.put(Resources.getResourceString(language, clazz, "answer_dontSend"), SendErrorsMode.NEVER);

    return map;
  }
}
