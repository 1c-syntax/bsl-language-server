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
import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.SendErrorsMode;
import com.github._1c_syntax.bsl.languageserver.configuration.Resources;
import io.sentry.Hint;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions.BeforeSendCallback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ServerInfo;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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

  private final GlobalLanguageServerConfiguration configuration;

  private final LanguageClientHolder languageClientHolder;

  private final ClientCapabilitiesHolder clientCapabilitiesHolder;

  private final ServerInfo serverInfo;

  /**
   * Сколько событий копится, пока пользователь не ответил: ошибки часто идут пачкой, но держать
   * их без предела в памяти нельзя. Сверх этого числа события отбрасываются.
   */
  static final int MAX_POSTPONED_EVENTS = 100;

  /** Метка подсказки у события, отправку которого пользователь уже разрешил. */
  private static final String SEND_PERMITTED_HINT = "bsl-ls.send-permitted";

  private final Object askLock = new Object();

  /**
   * События, ждущие ответа пользователя; {@code null}, пока вопрос не задан.
   * Доступ — под {@link #askLock}.
   */
  private @Nullable List<PostponedEvent> postponed;

  /**
   * Решить, уходит ли событие в Sentry.
   * <p>
   * Ответа пользователя на вопрос о разрешении метод не ждёт: вызывающий поток может держать
   * блокировку, без которой сервер не прочитает сам ответ клиента. Пока ответа нет, события
   * не отправляются, а копятся — и уходят, когда пользователь отправку разрешит. Ответ
   * «отправить один раз» относится ко всей накопленной пачке.
   */
  @Override
  public @Nullable SentryEvent execute(SentryEvent event, Hint hint) {
    if (Boolean.TRUE.equals(hint.getAs(SEND_PERMITTED_HINT, Boolean.class))) {
      return event;
    }

    Optional<LanguageClient> clientToAsk;
    synchronized (askLock) {
      var currentErrorsMode = configuration.getSendErrors();
      if (currentErrorsMode != SendErrorsMode.ASK) {
        if (currentErrorsMode == SendErrorsMode.SEND_ONCE) {
          configuration.setSendErrors(SendErrorsMode.ASK);
        }
        return isSendingAllowed(currentErrorsMode) ? event : null;
      }

      var client = languageClientHolder.getClient();
      if (client.isEmpty() || clientCapabilitiesHolder.getCapabilities().isEmpty()) {
        return null;
      }

      var waiting = postponed;
      clientToAsk = waiting == null ? client : Optional.empty();
      if (waiting == null) {
        waiting = new ArrayList<>();
        postponed = waiting;
      }
      if (waiting.size() < MAX_POSTPONED_EVENTS) {
        waiting.add(new PostponedEvent(event, hint));
      }
    }

    clientToAsk.ifPresent(this::ask);
    return null;
  }

  private void ask(LanguageClient languageClient) {
    try {
      askUserForPermission(languageClient).whenComplete(this::onAnswer);
    } catch (RuntimeException e) {
      onAnswer(null, e);
    }
  }

  private void onAnswer(@Nullable MessageActionItem answer, @Nullable Throwable error) {
    List<PostponedEvent> permitted;
    synchronized (askLock) {
      if (error == null) {
        applyAnswer(answer);
      } else {
        LOGGER.warn("Can't execute permission request", error);
      }
      var currentErrorsMode = configuration.getSendErrors();
      var waiting = postponed;
      permitted = isSendingAllowed(currentErrorsMode) && waiting != null ? waiting : List.of();
      if (currentErrorsMode == SendErrorsMode.SEND_ONCE) {
        configuration.setSendErrors(SendErrorsMode.ASK);
      }
      postponed = null;
    }
    // Не в потоке клиента, где пришёл ответ, и не изнутри обработки события самим Sentry:
    // пачка может быть большой, а повторный захват изнутри beforeSend до него не доходит.
    if (!permitted.isEmpty()) {
      CompletableFuture.runAsync(() -> permitted.forEach(PermissionFilterBeforeSendCallback::sendAgain));
    }
  }

  private static boolean isSendingAllowed(SendErrorsMode errorsMode) {
    return errorsMode == SendErrorsMode.SEND || errorsMode == SendErrorsMode.SEND_ONCE;
  }

  /**
   * Отправить заново событие, отложенное до ответа пользователя.
   * <p>
   * Исключение с события снимается: дедупликация Sentry запомнила его ещё на первом проходе
   * и отбросила бы повтор. В отчёт оно всё равно попадает — разобранное из него описание
   * исключений у события к этому моменту уже есть.
   */
  private static void sendAgain(PostponedEvent postponedEvent) {
    var event = postponedEvent.event();
    var hint = postponedEvent.hint();
    event.setThrowable(null);
    hint.set(SEND_PERMITTED_HINT, Boolean.TRUE);
    Sentry.captureEvent(event, hint);
  }

  private record PostponedEvent(SentryEvent event, Hint hint) {
  }

  private void applyAnswer(@Nullable MessageActionItem answer) {
    Optional.ofNullable(answer)
      .map(MessageActionItem::getTitle)
      .map(title -> answers.get(configuration.getLanguage()).get(title))
      .ifPresent(configuration::setSendErrors);
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
