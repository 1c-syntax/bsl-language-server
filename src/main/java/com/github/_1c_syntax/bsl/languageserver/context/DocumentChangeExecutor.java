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
package com.github._1c_syntax.bsl.languageserver.context;

import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/**
 * Управляет последовательной обработкой изменений документа для одного {@link DocumentContext}.
 * <p>
 *   Задачи попадают в приоритетную очередь и выполняются строго в порядке возрастания версии,
 * что позволяет консистентно накапливать изменения и применять их одним вызовом обработчика.
 */
public class DocumentChangeExecutor {

  @FunctionalInterface
  public interface DocumentChangeListener {
    void onChange(DocumentContext documentContext, String newContent, int version);
  }

  private static final Logger LOGGER = LoggerFactory.getLogger(DocumentChangeExecutor.class);

  private final DocumentContext documentContext;
  private final PriorityBlockingQueue<ChangeTask> queue = new PriorityBlockingQueue<>();
  private final Thread worker;
  private final BiFunction<String, List<TextDocumentContentChangeEvent>, String> changeApplier;
  private final DocumentChangeListener changeListener;
  private volatile boolean running = true;
  private @Nullable String pendingContent;
  private int pendingVersion = -1;

  /**
   * Создаёт executor для конкретного документа и запускает рабочий поток.
   *
   * @param documentContext контекст документа, для которого обрабатываются изменения
   * @param changeApplier функция, применяющая набор изменений к переданному тексту
   * @param changeListener слушатель, вызываемый после накопления изменений и перед rebuild
   * @param threadName префикс имени потока, чтобы упростить отладку
   */
  public DocumentChangeExecutor(
    DocumentContext documentContext,
    BiFunction<String, List<TextDocumentContentChangeEvent>, String> changeApplier,
    DocumentChangeListener changeListener,
    String threadName
  ) {
    this.documentContext = documentContext;
    this.changeApplier = changeApplier;
    this.changeListener = changeListener;
    worker = new Thread(this::runWorker);
    worker.setName(threadName + "executor");
    worker.setDaemon(true);
    worker.start();
  }

  /**
   * Помещает задачу в очередь на обработку.
   *
   * @param version версия документа, полученная от клиента LSP
   * @param contentChanges список изменений, которые необходимо применить
   */
  public void submit(int version, List<TextDocumentContentChangeEvent> contentChanges) {
    queue.put(new ChangeTask(version, List.copyOf(contentChanges)));
  }

  /**
   * Завершает работу executor'а, дожидаясь обработки уже поставленных задач.
   */
  public void shutdown() {
    running = false;
    worker.interrupt();
  }

  /**
   * Немедленно останавливает executor и очищает очередь.
   */
  public void shutdownNow() {
    running = false;
    queue.clear();
    worker.interrupt();
  }

  /**
   * Ожидает завершения рабочего потока в течение указанного таймаута.
   *
   * @param timeout длительность ожидания
   * @param unit единица измерения таймаута
   * @return {@code true}, если поток завершился за отведённое время; иначе {@code false}
   * @throws InterruptedException если ожидание было прервано
   */
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    long millis = unit.toMillis(timeout);
    worker.join(millis);
    return !worker.isAlive();
  }

  private void runWorker() {
    try {
      while (running || !queue.isEmpty()) {
        ChangeTask task;
        try {
          task = queue.take();
        } catch (InterruptedException ie) {
          if (!running && queue.isEmpty()) {
            break;
          }
          continue;
        }

        accumulate(task);

        if (queue.isEmpty()) {
          flushPendingChanges();
        }
      }
    } catch (Throwable t) {
      LOGGER.error("Unexpected error in document executor worker", t);
    } finally {
      flushPendingChanges();
    }
  }

  private void accumulate(ChangeTask task) {
    try {
      var baseContent = pendingContent == null ? documentContext.getContent() : pendingContent;
      pendingContent = changeApplier.apply(baseContent, task.contentChanges);
      pendingVersion = task.version;
    } catch (Throwable t) {
      LOGGER.error("Error while accumulating document change task", t);
      pendingContent = null;
    }
  }

  private void flushPendingChanges() {
    if (pendingContent == null) {
      return;
    }

    try {
      changeListener.onChange(documentContext, pendingContent, pendingVersion);
    } catch (Throwable t) {
      LOGGER.error("Error while applying accumulated document changes", t);
    } finally {
      pendingContent = null;
    }
  }

  private record ChangeTask(
    int version,
    List<TextDocumentContentChangeEvent> contentChanges
  ) implements Comparable<ChangeTask> {

    @Override
    public int compareTo(ChangeTask other) {
      return Integer.compare(this.version, other.version);
    }
  }
}
