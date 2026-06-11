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
 * LSP-запросов (см. {@code BSLTextDocumentService#withFreshDocumentContext}).
 * <p>
 * Доступ различается по тому, нужен ли инструменту свежий AST:
 * <ul>
 *   <li>{@link #read} — для вычисленных данных (дерево символов, индекс ссылок), которые
 *       переживают очистку AST: уже проиндексированный документ читается как есть,
 *       без повторного разбора;</li>
 *   <li>{@link #analyze} — для диагностик: AST пересобирается из файла, после чтения
 *       освобождается ({@link ServerContext#tryClearDocument});</li>
 *   <li>открытый в редакторе документ в обоих случаях читается «живым» под read-lock'ом
 *       и никогда не очищается.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class McpDocumentReader {

  private final ServerContextProvider serverContextProvider;
  private final McpReadiness readiness;

  /**
   * Прочитать вычисленные данные документа (дерево символов, ссылки) без повторного разбора AST.
   *
   * @param path Путь к файлу (абсолютный или относительный).
   * @param action Действие над контекстом документа.
   * @param <T> Тип результата.
   * @return Результат действия.
   */
  public <T> T read(String path, Function<DocumentContext, T> action) {
    return access(path, action, false);
  }

  /**
   * Прочитать данные, требующие свежего AST (диагностики).
   * <p>
   * Не открытый в редакторе документ пересобирается из файла, после чтения AST освобождается.
   * Открытый в редакторе документ читается «живым» (как в LSP) — содержимое буфера может
   * отличаться от файла на диске.
   *
   * @param path Путь к файлу (абсолютный или относительный).
   * @param action Действие над контекстом документа.
   * @param <T> Тип результата.
   * @return Результат действия.
   */
  public <T> T analyze(String path, Function<DocumentContext, T> action) {
    return access(path, action, true);
  }

  private <T> T access(String path, Function<DocumentContext, T> action, boolean requireFreshAst) {
    readiness.awaitReady();

    var uri = Absolute.uri(new File(path));
    var serverContext = serverContextProvider.getServerContext(uri)
      .orElseThrow(() -> new IllegalArgumentException(
        "File is not part of any registered workspace: " + path));

    var lock = serverContext.getDocumentLock(uri);
    var existing = serverContext.getDocument(uri);

    // Открытый в редакторе документ — читаем живой буфер; уже проиндексированный документ для
    // read-доступа тоже читаем как есть: дерево символов и индекс ссылок переживают очистку AST.
    // Повторный разбор нужен только для свежих диагностик (requireFreshAst).
    if (existing != null && (serverContext.isDocumentOpened(existing) || !requireFreshAst)) {
      lock.readLock().lock();
      try (var ignored = WorkspaceContextHolder.forUri(serverContext.getWorkspaceUri())) {
        return action.apply(existing);
      } finally {
        lock.readLock().unlock();
      }
    }

    // Нужна сборка: новый документ либо инструмент, которому требуется свежий AST.
    lock.writeLock().lock();
    try (var ignored = WorkspaceContextHolder.forUri(serverContext.getWorkspaceUri())) {
      var documentContext = serverContext.addDocument(uri);
      serverContext.rebuildDocument(documentContext);
      try {
        return action.apply(documentContext);
      } finally {
        // Освобождаем AST, построенный под этот запрос, чтобы память не росла без границ
        // (как в AnalyzeCommand). tryClearDocument пропускает документы, открытые в редакторе,
        // поэтому live-буферы LSP не затрагиваются; глобальный индекс ссылок переживает очистку.
        serverContext.tryClearDocument(documentContext);
      }
    } finally {
      lock.writeLock().unlock();
    }
  }
}
