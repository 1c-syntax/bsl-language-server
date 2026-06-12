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
package com.github._1c_syntax.bsl.languageserver.types.oscript.autumn;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.types.oscript.annotations.OScriptMetaAnnotationResolver;
import com.github._1c_syntax.bsl.languageserver.types.oscript.AbstractOScriptLazyIndex;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.EntryKind;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.LibraryEntry;

import java.net.URI;
import java.util.List;

/**
 * База для воркспейс-скоуп индексов «ОСени», собираемых сканированием .os-классов библиотек
 * ({@link OScriptLibraryIndex}).
 * <p>
 * Ленивая сборка, инвалидация и инкрементальные обновления — в
 * {@link AbstractOScriptLazyIndex}; здесь — источник пересборки (записи-классы
 * библиотек) и фильтр классов-определений аннотаций. Наследники реализуют лишь
 * {@link #clearIndex()}, {@link #indexClass} и {@link #removeByUri(URI)}.
 */
abstract class AbstractAutumnLibraryIndex extends AbstractOScriptLazyIndex {

  protected final OScriptLibraryIndex libraryIndex;
  protected final ServerContextProvider serverContextProvider;

  protected AbstractAutumnLibraryIndex(OScriptLibraryIndex libraryIndex,
                                       ServerContextProvider serverContextProvider,
                                       OScriptMetaAnnotationResolver metaAnnotationResolver) {
    super(metaAnnotationResolver);
    this.libraryIndex = libraryIndex;
    this.serverContextProvider = serverContextProvider;
  }

  /** Гарантировать, что индекс собран; сборка выполняется ровно один раз. */
  protected final void ensureBuilt() {
    ensureBuilt(this::rebuild);
  }

  private void rebuild() {
    clearIndex();
    libraryIndex.findEntries(EntryKind.CLASS).stream()
      .map(LibraryEntry::uri)
      .distinct()
      .forEach(this::indexDocumentByUri);
  }

  @Override
  protected final void indexDocument(DocumentContext document) {
    indexDocumentByUri(document.getUri());
  }

  private void indexDocumentByUri(URI uri) {
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
}
