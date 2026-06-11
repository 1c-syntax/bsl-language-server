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
package com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.annotations.OScriptMetaAnnotationResolver;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.EntryKind;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.LibraryEntry;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndexedEvent;
import org.springframework.context.event.EventListener;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * База для воркспейс-скоуп индексов «ОСени», собираемых сканированием .os-классов библиотек
 * ({@link OScriptLibraryIndex}).
 * <p>
 * Содержит общую обвязку (наследники реализуют лишь {@link #clearIndex()}, {@link #indexClass}
 * и {@link #removeByUri(URI)}):
 * <ul>
 *   <li>ленивая сборка с барьером — {@link #ensureBuilt()} строит индекс ровно один раз;</li>
 *   <li>полный сброс на переиндексацию библиотек ({@link OScriptLibraryIndexedEvent});</li>
 *   <li>точечная переиндексация .os-класса на правку; класс-определение аннотации
 *       ({@code &Аннотация}) сбрасывает индекс на полную ленивую пересборку;</li>
 *   <li>удаление вклада удалённого документа.</li>
 * </ul>
 * Spring наследует {@code @EventListener} в конкретных подклассах-бинах, поэтому база сама бином
 * не является. Сборка ленива и зависит от целостного {@code AnnotationRepository}, поэтому
 * порядок индексации классов и класса-определения аннотации не важен.
 */
abstract class AbstractAutumnLibraryIndex {

  protected final OScriptLibraryIndex libraryIndex;
  protected final ServerContextProvider serverContextProvider;
  protected final OScriptMetaAnnotationResolver metaAnnotationResolver;

  /**
   * Барьер первичной сборки: завершённый future — индекс построен; {@code null} — не построен
   * (соберётся лениво при следующем обращении).
   */
  private final AtomicReference<CompletableFuture<Void>> ready = new AtomicReference<>();

  protected AbstractAutumnLibraryIndex(OScriptLibraryIndex libraryIndex,
                                       ServerContextProvider serverContextProvider,
                                       OScriptMetaAnnotationResolver metaAnnotationResolver) {
    this.libraryIndex = libraryIndex;
    this.serverContextProvider = serverContextProvider;
    this.metaAnnotationResolver = metaAnnotationResolver;
  }

  /**
   * Полный сброс индекса — будет перестроен лениво при следующем обращении.
   * Реакция на переиндексацию библиотек (мог измениться состав классов).
   */
  @EventListener(OScriptLibraryIndexedEvent.class)
  public void invalidate() {
    ready.set(null);
  }

  /**
   * Обновить индекс при правке .os-документа: обычный класс переиндексируется точечно,
   * класс-определение аннотации сбрасывает индекс на полную ленивую пересборку. До первой
   * сборки или для .bsl — ничего не делаем.
   *
   * @param event Событие изменения содержимого документа.
   */
  @EventListener
  public void handleDocumentChange(DocumentContextContentChangedEvent event) {
    var document = event.getSource();
    if (document.getFileType() != FileType.OS || ready.get() == null) {
      return;
    }
    if (metaAnnotationResolver.isAnnotationDefinition(document)) {
      ready.set(null);
      return;
    }
    var uri = document.getUri();
    removeByUri(uri);
    indexDocument(uri);
  }

  /**
   * Удалить вклад удалённого .os-документа.
   *
   * @param event Событие удаления документа.
   */
  @EventListener
  public void handleDocumentRemoved(ServerContextDocumentRemovedEvent event) {
    if (ready.get() == null) {
      return;
    }
    removeByUri(event.getUri());
  }

  /** Гарантировать, что индекс собран; сборка выполняется ровно один раз. */
  protected void ensureBuilt() {
    while (true) {
      var done = ready.get();
      if (done != null) {
        done.join();
        return;
      }
      var fresh = new CompletableFuture<Void>();
      if (ready.compareAndSet(null, fresh)) {
        try {
          rebuild();
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

  private void rebuild() {
    clearIndex();
    libraryIndex.findEntries(EntryKind.CLASS).stream()
      .map(LibraryEntry::uri)
      .distinct()
      .forEach(this::indexDocument);
  }

  private void indexDocument(URI uri) {
    var classEntries = libraryIndex.findEntriesByUri(uri).stream()
      .filter(entry -> entry.kind() == EntryKind.CLASS)
      .toList();
    if (classEntries.isEmpty()) {
      return;
    }
    serverContextProvider.getServerContext(uri)
      .map(serverContext -> serverContext.getDocument(uri))
      // Класс-определение пользовательской аннотации (&Аннотация("Имя")) — не предметный класс:
      // его конструкторные аннотации нужны лишь для разворачивания мета-аннотаций.
      .filter(document -> !metaAnnotationResolver.isAnnotationDefinition(document))
      .ifPresent(document -> indexClass(document, classEntries, uri));
  }

  /** Очистить все записи индекса перед полной пересборкой. */
  protected abstract void clearIndex();

  /**
   * Проиндексировать .os-класс по указанному URI. Классы-определения аннотаций сюда
   * не попадают — они отфильтрованы общей обвязкой.
   *
   * @param document     Контекст документа класса.
   * @param classEntries Записи-классы этого файла из {@link OScriptLibraryIndex} (одно имя файла
   *                     может объявлять несколько классов библиотеки).
   * @param uri          URI .os-файла.
   */
  protected abstract void indexClass(DocumentContext document, List<LibraryEntry> classEntries, URI uri);

  /**
   * Удалить из индекса вклад указанного .os-файла.
   *
   * @param uri URI .os-файла.
   */
  protected abstract void removeByUri(URI uri);
}
