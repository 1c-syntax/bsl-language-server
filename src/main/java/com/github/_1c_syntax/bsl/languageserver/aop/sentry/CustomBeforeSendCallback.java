/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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

import com.github._1c_syntax.bsl.languageserver.LanguageClientHolder;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.SendAnalyticsMode;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions.BeforeSendCallback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ServerInfo;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomBeforeSendCallback implements BeforeSendCallback {

  private final LanguageServerConfiguration configuration;

  private final LanguageClientHolder languageClientHolder;

  private final ServerInfo serverInfo;

  @Override
  public SentryEvent execute(@NotNull SentryEvent event, Object hint) {
    if (sendToSentry()) {
      event.setTag("server.version", serverInfo.getVersion());
      return event;
    }

    return null;
  }


  private boolean sendToSentry() {
    if (configuration.getSendAnalytics() == SendAnalyticsMode.ASK) {
      var sendAnalytics = Map.of(
        new MessageActionItem("Yes and always send"), SendAnalyticsMode.SEND,
        new MessageActionItem("No and never ask"), SendAnalyticsMode.NEVER,
        new MessageActionItem("Skip this error"), SendAnalyticsMode.ASK
      );

      languageClientHolder.getClient()
        .map(this::askUserForPermission)
        .map(CustomBeforeSendCallback::waitForPermission)
        .map(key -> sendAnalytics.getOrDefault(key, null))
        .ifPresent(configuration::setSendAnalytics);
    }

    return configuration.getSendAnalytics() == SendAnalyticsMode.SEND;
  }

  private CompletableFuture<MessageActionItem> askUserForPermission(LanguageClient languageClient) {
    var applicationName = serverInfo.getName();
    var message = applicationName + " throws en exception. Do you agree to send details to developers?";

    var actions = List.of(
      new MessageActionItem("Yes and always send"),
      new MessageActionItem("No and never ask"),
      new MessageActionItem("Skip this error")
    );

    var requestParams = new ShowMessageRequestParams();
    requestParams.setType(MessageType.Error);
    requestParams.setMessage(message);
    requestParams.setActions(actions);
    return languageClient.showMessageRequest(requestParams);
  }

  private static MessageActionItem waitForPermission(CompletableFuture<MessageActionItem> messageActionItemCompletableFuture) {
    try {
      return messageActionItemCompletableFuture.get();
    } catch (InterruptedException e) {
      LOGGER.error("Can't wait for permission", e);
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      LOGGER.error("Can't wait for permission", e);
      throw new RuntimeException(e);
    }
  }

}