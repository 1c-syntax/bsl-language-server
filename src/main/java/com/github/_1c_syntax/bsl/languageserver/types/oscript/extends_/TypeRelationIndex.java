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
package com.github._1c_syntax.bsl.languageserver.types.oscript.extends_;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentAddedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextPopulatedEvent;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.oscript.annotations.OScriptMetaAnnotationResolver;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.types.oscript.AbstractOScriptLazyIndex;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Двунаправленный индекс прямых отношений библиотеки extends. Для запросов —
 * карты «имя родителя → наследники» ({@code &Расширяет}) и «имя интерфейса →
 * реализаторы» ({@code &Реализует}); обратные карты «URI → вклад документа»
 * служат точечной инвалидации.
 * <p>
 * Хранятся только прямые отношения и только сырые имена (lowercase) — без
 * разрешения имён в документы. Разрешение и транзитивные обходы выполняются
 * в {@link TypeRelations} на момент запроса по живым данным, поэтому
 * переиндексация библиотек ({@link OScriptLibraryIndex}) на содержимое карт
 * не влияет.
 * <p>
 * Ленивая сборка, инвалидация и инкрементальные обновления — в
 * {@link AbstractOScriptLazyIndex}; здесь — источник пересборки (все
 * {@code .os}-документы workspace) и реакция на полную перепопуляцию контекста
 * и добавление новых документов.
 */
@Component
@WorkspaceScope
public class TypeRelationIndex extends AbstractOScriptLazyIndex {

  private final OScriptExtends oScriptExtends;

  /** Имя родителя (lowercase) → URI документов, объявивших его в {@code &Расширяет}. */
  private final Map<String, Set<URI>> childUrisByParentName = new ConcurrentHashMap<>();
  /** URI → имя родителя, заявленное документом (для точечного удаления вклада). */
  private final Map<URI, String> parentNameByUri = new ConcurrentHashMap<>();
  /** Имя интерфейса (lowercase) → URI документов, объявивших его в {@code &Реализует}. */
  private final Map<String, Set<URI>> implementorUrisByInterfaceName = new ConcurrentHashMap<>();
  /** URI → имена интерфейсов, заявленные документом (для точечного удаления вклада). */
  private final Map<URI, Set<String>> interfaceNamesByUri = new ConcurrentHashMap<>();

  public TypeRelationIndex(OScriptExtends oScriptExtends,
                           OScriptMetaAnnotationResolver metaAnnotationResolver) {
    super(metaAnnotationResolver);
    this.oScriptExtends = oScriptExtends;
  }

  /**
   * URI документов, объявивших прямое наследование от любого из имён
   * (без учёта регистра).
   *
   * @param parentNames   имена/псевдонимы родительского класса
   * @param serverContext контекст workspace (источник документов для ленивой сборки)
   * @return URI прямых наследников; пусто, если таких нет
   */
  public Set<URI> directSubtypeUris(Collection<String> parentNames, ServerContext serverContext) {
    ensureBuilt(() -> rebuild(serverContext));
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
    ensureBuilt(() -> rebuild(serverContext));
    return collect(implementorUrisByInterfaceName, interfaceNames);
  }

  /**
   * Полный сброс на перепопуляцию контекста: состав документов заменён целиком.
   *
   * @param event событие перепопуляции контекста
   */
  @EventListener
  public void handleContextPopulated(ServerContextPopulatedEvent event) {
    invalidate();
  }

  /**
   * Учесть добавленный {@code .os}-документ (новый файл workspace).
   * До первой сборки — no-op: вклад попадёт в ленивую сборку.
   *
   * @param event событие добавления документа
   */
  @EventListener
  public void handleDocumentAdded(ServerContextDocumentAddedEvent event) {
    if (!isBuilt()) {
      return;
    }
    var document = event.getSource().getDocument(event.getUri());
    if (document != null && document.getFileType() == FileType.OS) {
      removeByUri(document.getUri());
      indexDocument(document);
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

  @Override
  protected void indexDocument(DocumentContext document) {
    var uri = document.getUri();
    oScriptExtends.parentClassName(document).ifPresent((String parentName) -> {
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

  @Override
  protected void removeByUri(URI uri) {
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
    urisByName.computeIfPresent(name, (String key, Set<URI> uris) -> {
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
