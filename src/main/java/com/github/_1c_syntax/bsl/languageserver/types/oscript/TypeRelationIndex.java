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
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentAddedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextPopulatedEvent;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Обратный индекс ПРЯМЫХ отношений библиотеки extends: «имя родителя →
 * наследники» ({@code &Расширяет}) и «имя интерфейса → реализаторы»
 * ({@code &Реализует}).
 * <p>
 * Хранятся только прямые отношения и только сырые имена (lowercase) — без
 * разрешения имён в документы. Разрешение и транзитивные обходы выполняются
 * в {@link TypeRelations} на момент запроса по живым данным, поэтому
 * переиндексация библиотек ({@link OScriptLibraryIndex}) на содержимое карт
 * не влияет.
 * <p>
 * Сборка ленивая (барьер на {@link AtomicReference}, ровно один раз) по всем
 * {@code .os}-документам workspace; далее индекс обновляется инкрементально:
 * правка .os-документа заменяет его вклад, удаление — убирает. Правка
 * класса-определения аннотации ({@code &Аннотация}) меняет разворачивание
 * мета-аннотаций в чужих классах, поэтому сбрасывает индекс на полную ленивую
 * пересборку (как у autumn-индексов); туда же — переиндексация библиотек.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class TypeRelationIndex {

  private final OScriptExtends oScriptExtends;

  /** Имя родителя (lowercase) → URI документов, объявивших его в {@code &Расширяет}. */
  private final Map<String, Set<URI>> childUrisByParentName = new ConcurrentHashMap<>();
  /** URI → имя родителя, заявленное документом (для точечного удаления вклада). */
  private final Map<URI, String> parentNameByUri = new ConcurrentHashMap<>();
  /** Имя интерфейса (lowercase) → URI документов, объявивших его в {@code &Реализует}. */
  private final Map<String, Set<URI>> implementorUrisByInterfaceName = new ConcurrentHashMap<>();
  /** URI → имена интерфейсов, заявленные документом (для точечного удаления вклада). */
  private final Map<URI, Set<String>> interfaceNamesByUri = new ConcurrentHashMap<>();

  /**
   * Барьер первичной сборки: завершённый future — индекс построен; {@code null} —
   * не построен (соберётся лениво при следующем обращении).
   */
  private final AtomicReference<CompletableFuture<Void>> ready = new AtomicReference<>();

  /**
   * URI документов, объявивших прямое наследование от любого из имён
   * (без учёта регистра).
   *
   * @param parentNames   имена/псевдонимы родительского класса
   * @param serverContext контекст workspace (источник документов для ленивой сборки)
   * @return URI прямых наследников; пусто, если таких нет
   */
  public Set<URI> directSubtypeUris(Collection<String> parentNames, ServerContext serverContext) {
    ensureBuilt(serverContext);
    return collect(childUrisByParentName, parentNames);
  }

  /**
   * URI документов, объявивших прямую реализацию любого из интерфейсов
   * (без учёта регистра).
   *
   * @param interfaceNames имена/псевдонимы интерфейсов
   * @param serverContext  контекст workspace (источник документов для ленивой сборки)
   * @return URI прямых реализаторов; пусто, если таких нет
   */
  public Set<URI> directImplementorUris(Collection<String> interfaceNames, ServerContext serverContext) {
    ensureBuilt(serverContext);
    return collect(implementorUrisByInterfaceName, interfaceNames);
  }

  /**
   * Полный сброс индекса — перестроится лениво при следующем обращении.
   * Реакция на переиндексацию библиотек (мог измениться состав классов-определений
   * аннотаций) и на полную перепопуляцию контекста (состав документов заменён целиком).
   */
  @EventListener({OScriptLibraryIndexedEvent.class, ServerContextPopulatedEvent.class})
  public void invalidate() {
    ready.set(null);
  }

  /**
   * Учесть добавленный {@code .os}-документ (новый файл workspace).
   * До первой сборки — no-op: вклад попадёт в ленивую сборку.
   *
   * @param event событие добавления документа
   */
  @EventListener
  public void handleDocumentAdded(ServerContextDocumentAddedEvent event) {
    if (ready.get() == null) {
      return;
    }
    var document = ((ServerContext) event.getSource()).getDocument(event.getUri());
    if (document != null && document.getFileType() == FileType.OS) {
      removeContribution(document.getUri());
      indexDocument(document);
    }
  }

  /**
   * Обновить индекс при правке {@code .os}-документа: вклад документа заменяется
   * точечно; правка класса-определения аннотации сбрасывает индекс целиком
   * (мета-аннотации влияют на чужие классы). До первой сборки и для .bsl — no-op.
   *
   * @param event событие изменения содержимого документа
   */
  @EventListener
  public void handleDocumentChange(DocumentContextContentChangedEvent event) {
    var document = event.getSource();
    if (document.getFileType() != FileType.OS || ready.get() == null) {
      return;
    }
    if (oScriptExtends.isAnnotationDefinition(document)) {
      ready.set(null);
      return;
    }
    removeContribution(document.getUri());
    indexDocument(document);
  }

  /**
   * Удалить вклад удалённого {@code .os}-документа.
   *
   * @param event событие удаления документа
   */
  @EventListener
  public void handleDocumentRemoved(ServerContextDocumentRemovedEvent event) {
    if (ready.get() == null) {
      return;
    }
    removeContribution(event.getUri());
  }

  /** Гарантировать, что индекс собран; сборка выполняется ровно один раз. */
  private void ensureBuilt(ServerContext serverContext) {
    while (true) {
      var done = ready.get();
      if (done != null) {
        done.join();
        return;
      }
      var fresh = new CompletableFuture<Void>();
      if (ready.compareAndSet(null, fresh)) {
        try {
          rebuild(serverContext);
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

  private void rebuild(ServerContext serverContext) {
    childUrisByParentName.clear();
    parentNameByUri.clear();
    implementorUrisByInterfaceName.clear();
    interfaceNamesByUri.clear();
    for (var document : serverContext.getDocuments().values()) {
      if (document.getFileType() == FileType.OS) {
        indexDocument(document);
      }
    }
  }

  private void indexDocument(DocumentContext document) {
    var uri = document.getUri();
    oScriptExtends.parentClassName(document).ifPresent(parentName -> {
      var key = parentName.toLowerCase(Locale.ROOT);
      parentNameByUri.put(uri, key);
      childUrisByParentName.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(uri);
    });
    var interfaceNames = oScriptExtends.implementedInterfaceNames(document);
    if (!interfaceNames.isEmpty()) {
      var keys = ConcurrentHashMap.<String>newKeySet();
      for (var interfaceName : interfaceNames) {
        var key = interfaceName.toLowerCase(Locale.ROOT);
        keys.add(key);
        implementorUrisByInterfaceName.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(uri);
      }
      interfaceNamesByUri.put(uri, keys);
    }
  }

  private void removeContribution(URI uri) {
    var parentName = parentNameByUri.remove(uri);
    if (parentName != null) {
      removeUri(childUrisByParentName, parentName, uri);
    }
    var interfaceNames = interfaceNamesByUri.remove(uri);
    if (interfaceNames != null) {
      for (var interfaceName : interfaceNames) {
        removeUri(implementorUrisByInterfaceName, interfaceName, uri);
      }
    }
  }

  private static void removeUri(Map<String, Set<URI>> urisByName, String name, URI uri) {
    urisByName.computeIfPresent(name, (key, uris) -> {
      uris.remove(uri);
      return uris.isEmpty() ? null : uris;
    });
  }

  private static Set<URI> collect(Map<String, Set<URI>> urisByName, Collection<String> names) {
    var result = new LinkedHashSet<URI>();
    for (var name : names) {
      var uris = urisByName.get(name.toLowerCase(Locale.ROOT));
      if (uris != null) {
        result.addAll(uris);
      }
    }
    return result;
  }
}
