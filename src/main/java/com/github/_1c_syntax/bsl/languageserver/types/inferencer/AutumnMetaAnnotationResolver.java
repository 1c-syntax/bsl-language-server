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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.EntryKind;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndexedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Разрешение пользовательских аннотаций фреймворка «ОСень» через мета-аннотации.
 * <p>
 * Пользовательская аннотация определяется классом, конструктор которого помечен
 * {@code &Аннотация("Имя")} и базовой мета-аннотацией. Например, killjoy-алиас
 * {@code &Внедряемое} определён как:
 * <pre>
 *   &amp;Аннотация("Внедряемое")
 *   &amp;Пластилин
 *   Процедура ПриСозданииОбъекта(Значение = "", Тип = "")
 * </pre>
 * то есть {@code &Внедряемое} — это {@code &Пластилин}. Резолвер строит индекс
 * «имя аннотации → её мета-аннотации» и отвечает, разворачивается ли аннотация
 * (транзитивно) в базовую роль фреймворка.
 */
@Component
@Scope(value = WorkspaceScope.SCOPE_NAME, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class AutumnMetaAnnotationResolver {

  /** Базовая мета-аннотация, регистрирующая пользовательскую аннотацию. */
  private static final String ANNOTATION_MARKER = "Аннотация";

  private final OScriptLibraryIndex libraryIndex;
  private final ServerContextProvider serverContextProvider;

  /** Имя пользовательской аннотации (lowercase) → имена её мета-аннотаций. Доступ под {@code synchronized(this)}. */
  private final Map<String, List<String>> metaByAnnotation = new HashMap<>();
  /** URI .os-файла → имена объявленных в нём пользовательских аннотаций (для инкрементального обновления). */
  private final Map<URI, List<String>> namesByUri = new HashMap<>();
  private boolean built;

  /**
   * Является ли аннотация {@code annotationName} базовой ролью {@code baseRole}
   * напрямую или через цепочку мета-аннотаций.
   *
   * @param annotationName имя проверяемой аннотации (как в коде)
   * @param baseRole       базовое имя роли (например, {@link AutumnAnnotations#INJECTION})
   */
  public synchronized boolean isRole(String annotationName, String baseRole) {
    return resolvesTo(annotationName, baseRole, new HashSet<>());
  }

  /**
   * Первая аннотация из списка, разворачивающаяся в указанную базовую роль.
   */
  public Optional<Annotation> findByRole(Iterable<Annotation> annotations, String baseRole) {
    for (var annotation : annotations) {
      if (isRole(annotation.getName(), baseRole)) {
        return Optional.of(annotation);
      }
    }
    return Optional.empty();
  }

  /**
   * @return {@code true}, если среди аннотаций есть разворачивающаяся в роль.
   */
  public boolean hasRole(Iterable<Annotation> annotations, String baseRole) {
    return findByRole(annotations, baseRole).isPresent();
  }

  /**
   * Значения параметра {@link AutumnAnnotations#VALUE_PARAMETER} всех аннотаций,
   * разворачивающихся в указанную роль (например, все прозвища желудя).
   */
  public List<String> valuesByRole(Iterable<Annotation> annotations, String baseRole) {
    var result = new ArrayList<String>();
    for (var annotation : annotations) {
      if (!isRole(annotation.getName(), baseRole)) {
        continue;
      }
      var value = AutumnAnnotations.stringParameter(annotation, AutumnAnnotations.VALUE_PARAMETER, 0);
      if (value != null && !value.isBlank()) {
        result.add(value);
      }
    }
    return result;
  }

  private boolean resolvesTo(String annotationName, String baseRole, Set<String> visited) {
    if (baseRole.equals(annotationName)) {
      return true;
    }
    if (!visited.add(annotationName.toLowerCase(Locale.ROOT))) {
      return false;
    }
    ensureBuilt();
    for (var meta : metaByAnnotation.getOrDefault(annotationName.toLowerCase(Locale.ROOT), List.of())) {
      if (resolvesTo(meta, baseRole, visited)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Полный сброс индекса — будет перестроен при следующем обращении.
   * Реакция на переиндексацию библиотек.
   */
  @EventListener(OScriptLibraryIndexedEvent.class)
  public synchronized void invalidate() {
    metaByAnnotation.clear();
    namesByUri.clear();
    built = false;
  }

  /**
   * Инкрементально пере-сканировать изменённый .os-документ: правка
   * класса-определения аннотации ({@code &Аннотация}) меняет только его вклад
   * в цепочки мета-аннотаций. Если индекс ещё не построен — соберётся целиком
   * при первом обращении.
   */
  @EventListener
  public synchronized void handleDocumentChange(DocumentContextContentChangedEvent event) {
    var document = event.getSource();
    if (document.getFileType() != FileType.OS || !built) {
      return;
    }
    var uri = document.getUri();
    removeDocument(uri);
    indexDocument(uri);
  }

  private void ensureBuilt() {
    if (built) {
      return;
    }
    metaByAnnotation.clear();
    namesByUri.clear();
    libraryIndex.findEntries(EntryKind.CLASS).stream()
      .map(OScriptLibraryIndex.LibraryEntry::uri)
      .distinct()
      .forEach(this::indexDocument);
    built = true;
  }

  /** Удалить из индекса пользовательские аннотации, объявленные в указанном .os-файле. */
  private void removeDocument(URI uri) {
    var names = namesByUri.remove(uri);
    if (names != null) {
      names.forEach(metaByAnnotation::remove);
    }
  }

  /** Проиндексировать определения аннотаций ({@code &Аннотация}) в .os-классе по указанному URI. */
  private void indexDocument(URI uri) {
    var isClass = libraryIndex.findEntriesByUri(uri).stream()
      .anyMatch(entry -> entry.kind() == EntryKind.CLASS);
    if (!isClass) {
      return;
    }
    var serverContext = serverContextProvider.getServerContext(uri).orElse(null);
    if (serverContext == null) {
      return;
    }
    var document = serverContext.getDocument(uri);
    if (document == null) {
      return;
    }
    for (var method : document.getSymbolTree().getMethods()) {
      registerDefinition(method, uri);
    }
  }

  private void registerDefinition(MethodSymbol method, URI uri) {
    var annotations = method.getAnnotations();
    var marker = AutumnAnnotations.find(annotations, ANNOTATION_MARKER);
    if (marker == null) {
      return;
    }
    var customName = AutumnAnnotations.stringParameter(marker, AutumnAnnotations.VALUE_PARAMETER, 0);
    if (customName == null || customName.isBlank()) {
      return;
    }
    var metas = new ArrayList<String>();
    for (var annotation : annotations) {
      if (!ANNOTATION_MARKER.equals(annotation.getName())) {
        metas.add(annotation.getName());
      }
    }
    var key = customName.toLowerCase(Locale.ROOT);
    metaByAnnotation.put(key, metas);
    namesByUri.computeIfAbsent(uri, u -> new ArrayList<>()).add(key);
  }
}
