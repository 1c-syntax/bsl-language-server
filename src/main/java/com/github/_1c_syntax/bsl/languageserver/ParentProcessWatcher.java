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
package com.github._1c_syntax.bsl.languageserver;

import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.services.LanguageServer;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Наблюдатель за жизнью родительского процесса, запустившего Language Server.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ParentProcessWatcher {

  private final LanguageServer languageServer;
  private long parentProcessId;

  /**
   * Обработчик события {@link LanguageServerInitializeRequestReceivedEvent}.
   * <p>
   * Анализирует параметры запроса и подготавливает данные для слежения за родительским процессом.
   *
   * @param event Событие
   */
  @EventListener
  public void handleEvent(LanguageServerInitializeRequestReceivedEvent event) {
    var processId = event.getParams().getProcessId();
    if (processId == null) {
      return;
    }
    parentProcessId = processId;
  }

  /**
   * Фоновая процедура, отслеживающая родительский процесс.
   */
  @Scheduled(fixedDelay = 30000L)
  public void watch() {
    if (parentProcessId == 0) {
      return;
    }

    boolean processIsAlive = ProcessHandle.of(parentProcessId)
      .map(ProcessHandle::isAlive)
      .orElse(false);

    if (!processIsAlive) {
      LOGGER.info("Parent process with pid {} is not found. Closing application...", parentProcessId);
      languageServer.exit();
    }
  }
}
