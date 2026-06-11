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
package com.github._1c_syntax.bsl.languageserver.mcp;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.utils.Absolute;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.function.Function;

/**
 * Доступ MCP-инструментов к документам через общий {@link ServerContextProvider} —
 * тот же, что наполняет LSP-сессия (и {@code analyze}/{@code format}).
 * <p>
 * Никакого отдельного состояния: документ ищется в зарегистрированных рабочих
 * пространствах провайдера. Чтение выполняется в контексте рабочего пространства
 * ({@link WorkspaceContextHolder}) и под блокировкой документа — так же, как обработчики
 * LSP-запросов (см. {@code BSLTextDocumentService#withFreshDocumentContext}):
 * <ul>
 *   <li>открытый в редакторе документ читается «живым», под read-lock'ом;</li>
 *   <li>не открытый — достраивается из файла под write-lock'ом
 *       ({@link ServerContext#rebuildDocument} идемпотентен для уже собранных).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class McpDocumentReader {

  private final ServerContextProvider serverContextProvider;
  private final McpReadiness readiness;

  /**
   * Прочитать данные документа через общий контекст сервера.
   *
   * @param path Путь к файлу (абсолютный или относительный).
   * @param action Действие над контекстом документа.
   * @param <T> Тип результата.
   * @return Результат действия.
   */
  public <T> T readDocument(String path, Function<DocumentContext, T> action) {
    readiness.awaitReady();

    var uri = Absolute.uri(new File(path));
    var serverContext = serverContextProvider.getServerContext(uri)
      .orElseThrow(() -> new IllegalArgumentException(
        "File is not part of any registered workspace: " + path));

    var lock = serverContext.getDocumentLock(uri);

    var existing = serverContext.getDocument(uri);
    if (existing != null && serverContext.isDocumentOpened(existing)) {
      lock.readLock().lock();
      try (var ignored = WorkspaceContextHolder.forUri(serverContext.getWorkspaceUri())) {
        return action.apply(existing);
      } finally {
        lock.readLock().unlock();
      }
    }

    lock.writeLock().lock();
    try (var ignored = WorkspaceContextHolder.forUri(serverContext.getWorkspaceUri())) {
      var documentContext = serverContext.addDocument(uri);
      serverContext.rebuildDocument(documentContext);
      try {
        return action.apply(documentContext);
      } finally {
        // Free the AST built for this query to avoid unbounded memory growth over a long-running
        // session (same as AnalyzeCommand). tryClearDocument is a no-op for documents that became
        // open in an editor meanwhile, so live LSP buffers are never touched. The global reference
        // index survives the clear; the document is rebuilt on demand on the next access.
        serverContext.tryClearDocument(documentContext);
      }
    } finally {
      lock.writeLock().unlock();
    }
  }
}
