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
package com.github._1c_syntax.bsl.languageserver.client;

import com.github._1c_syntax.bsl.languageserver.client.events.ClientInitializedEvent;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.WindowClientCapabilities;
import org.eclipse.lsp4j.WorkDoneProgressBegin;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkDoneProgressEnd;
import org.eclipse.lsp4j.WorkDoneProgressNotification;
import org.eclipse.lsp4j.WorkDoneProgressReport;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Помощник для работы с progress-уведомлениями LSP.
 * <p>
 * Управляет созданием и обновлением индикаторов прогресса выполнения
 * длительных операций (WorkDoneProgress).
 * <p>
 * Индикатор, начатый до того, как клиент готов их показывать ({@link #handleEvent(ClientInitializedEvent)}),
 * клиенту не отправляется, а только запоминается: создать индикатор по запросу сервера клиент
 * может лишь после уведомления {@code initialized} — раньше запрос
 * {@code window/workDoneProgress/create} ему нечем обработать, и индикатор теряется. Когда
 * клиент готов, индикаторы, ещё не законченные к этому моменту, создаются у него с текущим
 * состоянием; законченные раньше не показываются вовсе.
 */
@Component
@RequiredArgsConstructor
public class WorkDoneProgressHelper {

  private final LanguageClientHolder languageClientHolder;
  private final ClientCapabilitiesHolder clientCapabilitiesHolder;

  private boolean isWorkDoneProgressSupported;

  /** Готов ли клиент показывать индикаторы (см. {@link #handleEvent(ClientInitializedEvent)}). */
  private volatile boolean clientInitialized;

  /** Индикаторы, начатые до готовности клиента и ещё не законченные. */
  private final Set<WorkDoneProgressReporter> deferred = ConcurrentHashMap.newKeySet();

  public WorkDoneProgressReporter createProgress(int size, String messagePostfix) {
    isWorkDoneProgressSupported = clientCapabilitiesHolder.getCapabilities()
      .map(ClientCapabilities::getWindow)
      .map(WindowClientCapabilities::getWorkDoneProgress)
      .orElse(false);

    if (!isWorkDoneProgressSupported) {
      return new WorkDoneProgressReporter("", 0, "");
    }

    var reporter = new WorkDoneProgressReporter(UUID.randomUUID().toString(), size, messagePostfix);
    if (clientInitialized) {
      reporter.reveal();
      return reporter;
    }
    deferred.add(reporter);
    // Клиент мог стать готов, пока индикатор откладывался, и показать его уже некому.
    if (clientInitialized && deferred.remove(reporter)) {
      reporter.reveal();
    }
    return reporter;
  }

  /**
   * Клиент готов показывать индикаторы — пришло уведомление {@code initialized}.
   * <p>
   * Индикаторы, начатые раньше и ещё не законченные, создаются у клиента с текущим
   * состоянием; новые создаются сразу.
   *
   * @param event событие окончания инициализации клиента.
   */
  @EventListener
  public void handleEvent(ClientInitializedEvent event) {
    clientInitialized = true;
    for (var reporter : List.copyOf(deferred)) {
      if (deferred.remove(reporter)) {
        reporter.reveal();
      }
    }
  }

  public class WorkDoneProgressReporter {
    private final String token;
    @Setter
    private int size;
    private final String messagePostfix;

    private final AtomicInteger counter = new AtomicInteger();

    /** Создан ли индикатор у клиента; до этого его состояние только запоминается. */
    private boolean revealed;
    private boolean ended;
    private @Nullable String title;
    private @Nullable WorkDoneProgressReport lastReport;

    WorkDoneProgressReporter(String token, int size, String messagePostfix) {
      this.token = token;
      this.size = size;
      this.messagePostfix = messagePostfix;
    }

    public void beginProgress(String title) {
      if (!isWorkDoneProgressSupported) {
        return;
      }

      var value = new WorkDoneProgressBegin();
      value.setTitle(title);

      synchronized (this) {
        this.title = title;
        if (revealed) {
          notifyProgress(value);
        }
      }
    }

    public void tick(String message, int percentage) {
      if (!isWorkDoneProgressSupported) {
        return;
      }

      var value = new WorkDoneProgressReport();
      value.setMessage(message);
      value.setCancellable(false);
      value.setPercentage(percentage);

      synchronized (this) {
        lastReport = value;
        if (revealed) {
          notifyProgress(value);
        }
      }
    }

    public void tick() {
      if (!isWorkDoneProgressSupported) {
        return;
      }

      var currentCounter = counter.incrementAndGet();
      var message = "%d/%d%s".formatted(currentCounter, size, messagePostfix);
      var percentage = (double) currentCounter / size * 100;

      tick(message, (int) percentage);
    }

    public void endProgress(String message) {
      if (!isWorkDoneProgressSupported) {
        return;
      }

      var value = new WorkDoneProgressEnd();
      value.setMessage(message);

      synchronized (this) {
        ended = true;
        if (revealed) {
          notifyProgress(value);
        } else {
          // Закончен раньше, чем клиент стал готов: показывать нечего.
          deferred.remove(this);
        }
      }
    }

    /**
     * Создать индикатор у клиента и показать накопленное состояние: заголовок и последний
     * отчёт о ходе работы.
     */
    private synchronized void reveal() {
      if (revealed || ended) {
        return;
      }
      revealed = true;
      languageClientHolder.execIfConnected(languageClient ->
        languageClient.createProgress(new WorkDoneProgressCreateParams(Either.forLeft(token)))
      );
      if (title != null) {
        var begin = new WorkDoneProgressBegin();
        begin.setTitle(title);
        notifyProgress(begin);
      }
      if (lastReport != null) {
        notifyProgress(lastReport);
      }
    }

    private void notifyProgress(WorkDoneProgressNotification value) {
      languageClientHolder.execIfConnected(languageClient ->
        languageClient.notifyProgress(new ProgressParams(Either.forLeft(token), Either.forLeft(value)))
      );
    }
  }
}
