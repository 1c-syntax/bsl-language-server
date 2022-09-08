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
package com.github._1c_syntax.bsl.languageserver;

import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.services.LanguageServer;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.CheckForNull;
import java.util.Optional;

/**
 * Наблюдатель за жизнью родительского процесса, запустившего Language Server.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ParentProcessWatcher {

  private final LanguageServer languageServer;

  /**
   * Обработчик события {@link LanguageServerInitializeRequestReceivedEvent}.
   * <p>
   * Анализирует параметры запроса и подготавливает данные для слежения за родительским процессом.
   *
   * @param event Событие
   */
  @EventListener
  public void handleEvent(LanguageServerInitializeRequestReceivedEvent event) {
    @CheckForNull Integer processId = event.getParams().getProcessId();
    if (processId == null) {
      return;
    }

    // Can't register onExit callback on current process.
    if (ProcessHandle.current().pid() == processId) {
      return;
    }

    Optional.of(processId)
      .flatMap(ProcessHandle::of)
      .map(ProcessHandle::onExit)
      .ifPresent(onExitCallback -> onExitCallback.thenRun(() -> {
        LOGGER.warn("Parent process with pid {} is not found. Closing application...", processId);
        languageServer.exit();
      }));
  }

}
