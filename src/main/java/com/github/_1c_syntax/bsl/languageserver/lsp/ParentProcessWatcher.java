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
package com.github._1c_syntax.bsl.languageserver.lsp;

import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.services.LanguageServer;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Наблюдатель за жизнью родительского процесса, запустившего Language Server.
 * <p>
 * Завершение родительского процесса отслеживается средствами JDK ({@link ProcessHandle#onExit()}),
 * без периодического опроса.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ParentProcessWatcher {

  private final LanguageServer languageServer;

  /**
   * Обработчик события {@link LanguageServerInitializedEvent}.
   * <p>
   * Начинает слежение за процессом, идентификатор которого передан в параметрах инициализации.
   * Когда этот процесс завершается, Language Server завершает работу. Если процесс с таким
   * идентификатором уже не существует, Language Server завершает работу сразу.
   * Завершение выполняется асинхронно, вне потока обработчика.
   * <p>
   * Если идентификатор не передан или совпадает с идентификатором текущего процесса,
   * слежение не выполняется.
   *
   * @param event Событие
   */
  @EventListener
  public void handleEvent(LanguageServerInitializedEvent event) {
    var processId = event.getParams().getProcessId();
    if (processId == null) {
      return;
    }

    long parentProcessId = processId;
    // Для текущего процесса ProcessHandle#onExit() не поддерживается.
    if (parentProcessId == ProcessHandle.current().pid()) {
      return;
    }

    ProcessHandle.of(parentProcessId)
      .map(ProcessHandle::onExit)
      .orElseGet(() -> CompletableFuture.completedFuture(null))
      .thenRunAsync(() -> onParentProcessExit(parentProcessId));
  }

  private void onParentProcessExit(long parentProcessId) {
    var message = "Parent process with pid " + parentProcessId + " is not found. Closing application...";
    try {
      LOGGER.info(message);
    } catch (NoClassDefFoundError | Exception e) {
      System.out.println(message);
    }
    languageServer.exit();
  }
}
