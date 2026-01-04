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
package com.github._1c_syntax.bsl.languageserver.context;

import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

/**
 * Управляет последовательной обработкой изменений документа для одного {@link DocumentContext}.
 * <p>
 *   Задачи попадают в приоритетную очередь и выполняются строго в порядке возрастания версии,
 * что позволяет консистентно накапливать изменения и применять их одним вызовом обработчика.
 * Дополнительно класс предоставляет барьер {@link #awaitLatest()}, позволяющий клиентским потокам дождаться
 * применения всех поставленных изменений и тем самым читать консистентное состояние документа.
 */
public final class DocumentChangeExecutor {

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
  /**
   * Наибольшая версия документа, для которой уже был поставлен {@code didChange}.
   */
  private final AtomicInteger latestSubmittedVersion;
  /**
   * Наибольшая версия документа, полностью применённая к {@link DocumentContext}.
   */
  private final AtomicInteger latestAppliedVersion;
  /**
   * Очередь ожиданий для запросов, которым нужно дождаться применения конкретных версий.
   */
  private final ConcurrentSkipListMap<Integer, CopyOnWriteArrayList<CompletableFuture<Void>>> versionWaiters
    = new ConcurrentSkipListMap<>();
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
    int initialVersion = documentContext.getVersion();
    this.latestSubmittedVersion = new AtomicInteger(initialVersion);
    this.latestAppliedVersion = new AtomicInteger(initialVersion);
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
    latestSubmittedVersion.accumulateAndGet(version, Math::max);
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

  /**
   * Дожидается применения всех изменений, поставленных на момент вызова.
   * <p>
   *   Метод безопасно вызывается из любых потоков: если все изменения уже применены, возвращает
   *   немедленно завершённый {@link CompletableFuture}, иначе регистрирует ожидание и завершит его,
   *   как только рабочий поток применит соответствующую версию.
   * </p>
   *
   * @return future, завершающийся после применения всех накопленных изменений.
   */
  public CompletableFuture<Void> awaitLatest() {
    int targetVersion = latestSubmittedVersion.get();
    if (targetVersion <= latestAppliedVersion.get()) {
      return CompletableFuture.completedFuture(null);
    }
    return registerWaiter(targetVersion);
  }

  private void runWorker() {
    var interrupted = false;
    try {
      while (running || !queue.isEmpty()) {
        ChangeTask task;
        try {
          task = queue.take();
        } catch (InterruptedException ie) {
          interrupted = true;
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
    } catch (Exception e) {
      LOGGER.error("Unexpected error in document executor worker", e);
    } finally {
      flushPendingChanges();
    }

    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Регистрирует новый future, который завершится, когда будет обработана указанная версия документа.
   *
   * @param version требуемая версия
   * @return future, завершение которого сигнализирует о достижении нужной версии
   */
  private CompletableFuture<Void> registerWaiter(int version) {
    var future = new CompletableFuture<Void>();
    versionWaiters.compute(version, (Integer key, CopyOnWriteArrayList<CompletableFuture<Void>> futures) -> {
      var list = futures;
      if (list == null) {
        list = new CopyOnWriteArrayList<>();
      }
      list.add(future);
      return list;
    });

    if (version <= latestAppliedVersion.get()) {
      completeWaitersUpTo(latestAppliedVersion.get());
    }

    return future;
  }

  /**
   * Накопить ещё одну задачу изменения, чтобы позже применить пакет изменений одним вызовом rebuild.
   */
  private void accumulate(ChangeTask task) {
    try {
      var baseContent = pendingContent == null ? documentContext.getContent() : pendingContent;
      pendingContent = changeApplier.apply(baseContent, task.contentChanges);
      pendingVersion = task.version;
    } catch (Exception e) {
      LOGGER.error("Error while accumulating document change task", e);
      pendingContent = null;
      pendingVersion = -1;
      latestAppliedVersion.accumulateAndGet(task.version, Math::max);
      completeWaitersUpTo(latestAppliedVersion.get());
    }
  }

  /**
   * Применяет накопленные изменения и уведомляет ожидающие запросы о доступности новой версии.
   */
  private void flushPendingChanges() {
    if (pendingContent == null || pendingVersion < 0) {
      return;
    }

    var lock = documentContext.getServerContext().getDocumentLock(documentContext.getUri());
    lock.writeLock().lock();

    try {
      changeListener.onChange(documentContext, pendingContent, pendingVersion);
      latestAppliedVersion.accumulateAndGet(pendingVersion, Math::max);
      completeWaitersUpTo(latestAppliedVersion.get());
    } catch (Exception e) {
      LOGGER.error("Error while applying accumulated document changes", e);
    } finally {
      lock.writeLock().unlock();
      pendingContent = null;
      pendingVersion = -1;
    }
  }

  /**
   * Завершает все ожидания, чей целевой номер версии оказался не больше указанного значения.
   */
  private void completeWaitersUpTo(int version) {
    var iterator = versionWaiters.entrySet().iterator();
    while (iterator.hasNext()) {
      var entry = iterator.next();
      if (entry.getKey() <= version) {
        entry.getValue().forEach(future -> future.complete(null));
        iterator.remove();
      } else {
        break;
      }
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
