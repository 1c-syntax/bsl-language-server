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
package com.github._1c_syntax.bsl.languageserver.types.oscript;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.types.oscript.annotations.OScriptMetaAnnotationResolver;
import com.github._1c_syntax.bsl.languageserver.types.oscript.events.OScriptLibraryIndexedEvent;
import org.springframework.context.event.EventListener;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * База для воркспейс-скоуп индексов по {@code .os}-документам с ленивой сборкой
 * и инкрементальной инвалидацией.
 * <p>
 * Содержит общую обвязку (наследники реализуют лишь {@link #indexDocument} и
 * {@link #removeByUri(URI)} и вызывают {@link #ensureBuilt(Runnable)} перед чтением):
 * <ul>
 *   <li>ленивая сборка с барьером — ровно один раз, переносимо между потоками;</li>
 *   <li>полный сброс на переиндексацию библиотек ({@link OScriptLibraryIndexedEvent});</li>
 *   <li>точечная переиндексация {@code .os}-документа на правку; правка
 *       класса-определения аннотации ({@code &Аннотация}) меняет разворачивание
 *       мета-аннотаций в чужих классах и потому сбрасывает индекс на полную
 *       ленивую пересборку;</li>
 *   <li>удаление вклада удалённого документа.</li>
 * </ul>
 * Spring наследует {@code @EventListener} в конкретных подклассах-бинах, поэтому
 * база сама бином не является. Сборка ленива и зависит от целостного
 * {@code AnnotationRepository}, поэтому порядок индексации классов и
 * класса-определения аннотации не важен.
 */
public abstract class AbstractOScriptLazyIndex {

  /** Резолвер мета-аннотаций (проверка классов-определений аннотаций). */
  protected final OScriptMetaAnnotationResolver metaAnnotationResolver;

  /**
   * Барьер первичной сборки: завершённый future — индекс построен; {@code null} —
   * не построен (соберётся лениво при следующем обращении).
   */
  private final AtomicReference<CompletableFuture<Void>> ready = new AtomicReference<>();

  protected AbstractOScriptLazyIndex(OScriptMetaAnnotationResolver metaAnnotationResolver) {
    this.metaAnnotationResolver = metaAnnotationResolver;
  }

  /**
   * Полный сброс индекса — перестроится лениво при следующем обращении.
   * Реакция на переиндексацию библиотек (мог измениться состав классов).
   */
  @EventListener(OScriptLibraryIndexedEvent.class)
  public void invalidate() {
    ready.set(null);
  }

  /**
   * Обновить индекс при правке {@code .os}-документа: вклад документа заменяется
   * точечно; правка класса-определения аннотации сбрасывает индекс целиком.
   * До первой сборки и для {@code .bsl} — no-op.
   *
   * @param event событие изменения содержимого документа
   */
  @EventListener
  public void handleDocumentChange(DocumentContextContentChangedEvent event) {
    var document = event.getSource();
    if (document.getFileType() != FileType.OS || !isBuilt()) {
      return;
    }
    if (metaAnnotationResolver.isAnnotationDefinition(document)) {
      ready.set(null);
      return;
    }
    removeByUri(document.getUri());
    indexDocument(document);
  }

  /**
   * Удалить вклад удалённого {@code .os}-документа.
   *
   * @param event событие удаления документа
   */
  @EventListener
  public void handleDocumentRemoved(ServerContextDocumentRemovedEvent event) {
    if (!isBuilt()) {
      return;
    }
    removeByUri(event.getUri());
  }

  /**
   * Построен ли индекс. До первой сборки инкрементальные обновления не нужны:
   * вклад документов попадёт в ленивую сборку.
   *
   * @return {@code true}, если первичная сборка уже выполнена или идёт
   */
  protected final boolean isBuilt() {
    return ready.get() != null;
  }

  /**
   * Гарантировать, что индекс собран; сборка выполняется ровно один раз.
   *
   * @param rebuild полная пересборка индекса с чистого состояния
   */
  protected final void ensureBuilt(Runnable rebuild) {
    while (true) {
      var done = ready.get();
      if (done != null) {
        done.join();
        return;
      }
      var fresh = new CompletableFuture<Void>();
      if (ready.compareAndSet(null, fresh)) {
        try {
          rebuild.run();
          fresh.complete(null);
        } catch (RuntimeException e) {
          ready.compareAndSet(fresh, null);
          fresh.completeExceptionally(e);
          throw e;
        }
        return;
      }
      // Другой поток уже строит — повторим и присоединимся к его future.
    }
  }

  /**
   * Учесть вклад {@code .os}-документа в индекс (при точечной переиндексации
   * прежний вклад уже снят через {@link #removeByUri(URI)}).
   *
   * @param document контекст {@code .os}-документа
   */
  protected abstract void indexDocument(DocumentContext document);

  /**
   * Удалить из индекса вклад указанного {@code .os}-файла.
   *
   * @param uri URI {@code .os}-файла
   */
  protected abstract void removeByUri(URI uri);
}
