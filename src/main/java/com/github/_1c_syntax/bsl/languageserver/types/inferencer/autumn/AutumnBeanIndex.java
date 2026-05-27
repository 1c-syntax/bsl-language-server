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
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.EntryKind;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndexedEvent;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Индекс желудей фреймворка «ОСень»: отображение «имя/прозвище желудя → тип».
 * <p>
 * Покрывает случаи, которые нельзя разрешить прямым резолвом имени типа:
 * <ul>
 *   <li>переименованный компонент — {@code &Желудь("ДругоеИмя")};</li>
 *   <li>класс-фабрика — {@code &Дуб} (сам тоже желудь);</li>
 *   <li>фабричный метод — {@code &Завязь} (только внутри {@code &Дуб});</li>
 *   <li>прозвища — {@code &Прозвище("Алиас")} (повторяемая);</li>
 *   <li>приоритет при конфликте имён/прозвищ — {@code &Верховный}.</li>
 * </ul>
 * Аннотации компонента ({@code &Желудь}/{@code &Дуб}) размещаются только над
 * конструктором, поэтому он берётся из дерева символов напрямую; методы
 * сканируются на {@code &Завязь} лишь когда класс — дуб.
 * Строится лениво из {@link OScriptLibraryIndex} (классы-желуди) и сбрасывается
 * при переиндексации библиотек.
 */
@Component
@Scope(value = WorkspaceScope.SCOPE_NAME, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class AutumnBeanIndex {

  private final OScriptLibraryIndex libraryIndex;
  private final ServerContextProvider serverContextProvider;
  private final TypeRegistry typeRegistry;
  private final AutumnMetaAnnotationResolver metaAnnotationResolver;

  /** Имя/прозвище желудя (lowercase) → кандидаты. Доступ под {@code synchronized(this)}. */
  private final Map<String, List<BeanCandidate>> beansByName = new HashMap<>();
  /** volatile — чтобы быстрый путь листенера читал его без блокировки (см. {@link #handleDocumentChange}). */
  private volatile boolean built;

  /**
   * Кандидат-желудь: тип компонента, признак приоритетного ({@code &Верховный})
   * и URI .os-файла, из которого он зарегистрирован (для инкрементального
   * пере-сканирования при правке файла).
   */
  private record BeanCandidate(TypeRef type, boolean primary, URI sourceUri) {
  }

  /**
   * Разрешить тип желудя по его имени или прозвищу.
   *
   * @return тип(ы) желудя; при конфликте имён предпочитаются помеченные
   *         {@code &Верховный}, иначе объединяются все кандидаты. Пусто, если
   *         желудь с таким именем не найден.
   */
  public synchronized TypeSet resolve(String name) {
    if (name.isBlank()) {
      return TypeSet.EMPTY;
    }
    ensureBuilt();
    var candidates = beansByName.get(name.toLowerCase(Locale.ROOT));
    if (candidates == null || candidates.isEmpty()) {
      return TypeSet.EMPTY;
    }

    var refs = new LinkedHashSet<TypeRef>();
    for (var candidate : candidates) {
      if (candidate.primary()) {
        refs.add(candidate.type());
      }
    }
    if (refs.isEmpty()) {
      for (var candidate : candidates) {
        refs.add(candidate.type());
      }
    }
    return TypeSet.of(refs);
  }

  /**
   * Полный сброс индекса — будет перестроен при следующем обращении.
   * Реакция на переиндексацию библиотек (мог измениться состав классов).
   */
  @EventListener(OScriptLibraryIndexedEvent.class)
  public synchronized void invalidate() {
    beansByName.clear();
    built = false;
  }

  /**
   * Инкрементально пере-сканировать изменённый .os-документ: правка желудя
   * (имя, прозвища, {@code &Завязь}) меняет только его вклад в индекс — остальное
   * не трогаем. Если индекс ещё не построен, ничего не делаем: он соберётся
   * целиком при первом обращении.
   */
  @EventListener
  public void handleDocumentChange(DocumentContextContentChangedEvent event) {
    var document = event.getSource();
    // Быстрый путь без блокировки. Во время ServerContext#populateContext события
    // сыплются из многих потоков, а индекс ещё не построен (built == false) —
    // synchronized-листенер линеаризовал бы populateContext на мониторе индекса.
    // Изменения .os-документов до построения захватит ленивый ensureBuilt, .bsl
    // на желуди не влияют. Лок берём, только когда реально есть что обновлять.
    if (document.getFileType() != FileType.OS || !built) {
      return;
    }
    applyDocumentChange(document);
  }

  private synchronized void applyDocumentChange(DocumentContext document) {
    if (!built) {
      return;
    }
    // Правка класса-определения аннотации (&Аннотация) может изменить роль любой
    // аннотации, разворачивающейся через него, а значит — состав желудей в любом
    // использующем её классе. Точечного обновления здесь мало: сбрасываем индекс
    // целиком (определения аннотаций правят редко, ребилд ленивый).
    if (isAnnotationDefinition(document)) {
      invalidate();
      return;
    }
    var uri = document.getUri();
    removeDocument(uri);
    indexDocument(uri);
  }

  /** Несёт ли конструктор класса маркер {@code &Аннотация} (класс-определение пользовательской аннотации). */
  private static boolean isAnnotationDefinition(DocumentContext document) {
    return document.getSymbolTree().getConstructor()
      .map(constructor ->
        AutumnAnnotations.find(constructor.getAnnotations(), AutumnAnnotations.ANNOTATION_MARKER).isPresent())
      .orElse(false);
  }

  private void ensureBuilt() {
    if (built) {
      return;
    }
    beansByName.clear();
    libraryIndex.findEntries(EntryKind.CLASS).stream()
      .map(OScriptLibraryIndex.LibraryEntry::uri)
      .distinct()
      .forEach(this::indexDocument);
    built = true;
  }

  /** Удалить из индекса все кандидаты, зарегистрированные из указанного .os-файла. */
  private void removeDocument(URI uri) {
    var iterator = beansByName.values().iterator();
    while (iterator.hasNext()) {
      var candidates = iterator.next();
      candidates.removeIf(candidate -> uri.equals(candidate.sourceUri()));
      if (candidates.isEmpty()) {
        iterator.remove();
      }
    }
  }

  /** Проиндексировать желуди и фабрики, объявленные в .os-классе по указанному URI. */
  private void indexDocument(URI uri) {
    var classEntries = libraryIndex.findEntriesByUri(uri).stream()
      .filter(entry -> entry.kind() == EntryKind.CLASS)
      .toList();
    if (classEntries.isEmpty()) {
      return;
    }
    var serverContext = serverContextProvider.getServerContext(uri).orElse(null);
    if (serverContext == null) {
      return;
    }
    DocumentContext document = serverContext.getDocument(uri);
    if (document == null) {
      return;
    }
    var symbolTree = document.getSymbolTree();
    // Аннотации компонента (&Желудь/&Дуб) размещаются исключительно над
    // конструктором — берём его напрямую, не сканируя все методы.
    var constructor = symbolTree.getConstructor().orElse(null);
    if (constructor == null) {
      return;
    }
    var constructorAnnotations = constructor.getAnnotations();

    // Класс-определение пользовательской аннотации (&Аннотация("Имя")) — не желудь:
    // его конструкторные аннотации нужны лишь для разворачивания мета-аннотаций.
    if (AutumnAnnotations.find(constructorAnnotations, AutumnAnnotations.ANNOTATION_MARKER).isPresent()) {
      return;
    }

    for (var entry : classEntries) {
      var ownerType = typeRegistry.resolve(entry.qualifiedName()).orElse(null);
      if (ownerType != null) {
        registerComponent(constructorAnnotations, entry.qualifiedName(), ownerType, uri);
      }
    }

    // &Завязь размещается над методом и допустима только в классе-дубе —
    // иначе методы как фабрики не трактуем.
    if (metaAnnotationResolver.hasRole(constructorAnnotations, AutumnAnnotations.OAK)) {
      for (var method : symbolTree.getMethods()) {
        registerFactory(method, uri);
      }
    }
  }

  private void registerComponent(List<Annotation> annotations, String defaultName, TypeRef ownerType, URI uri) {
    // &Желудь либо &Дуб: класс является желудём (дуб сам по себе тоже желудь).
    var component = metaAnnotationResolver.findByRole(annotations, AutumnAnnotations.COMPONENT)
      .or(() -> metaAnnotationResolver.findByRole(annotations, AutumnAnnotations.OAK))
      .orElse(null);
    if (component == null) {
      return;
    }
    var name = AutumnAnnotations.stringParameter(component, AutumnAnnotations.VALUE_PARAMETER)
      .filter(value -> !value.isBlank())
      .orElse(defaultName);
    register(annotations, name, ownerType, uri);
  }

  private void registerFactory(MethodSymbol method, URI uri) {
    var annotations = method.getAnnotations();
    var factory = metaAnnotationResolver.findByRole(annotations, AutumnAnnotations.FACTORY).orElse(null);
    if (factory == null) {
      return;
    }
    var name = AutumnAnnotations.stringParameter(factory, AutumnAnnotations.VALUE_PARAMETER)
      .filter(value -> !value.isBlank())
      .orElse(method.getName());
    var beanType = factoryBeanType(factory, name);
    if (beanType == null) {
      return;
    }
    register(annotations, name, beanType, uri);
  }

  private @Nullable TypeRef factoryBeanType(Annotation factory, String beanName) {
    // Параметр Тип задаётся только по имени (позиционно можно лишь Значение).
    return AutumnAnnotations.stringParameter(factory, AutumnAnnotations.TYPE_PARAMETER)
      .filter(type -> !type.isBlank() && !AutumnAnnotations.BEAN_TYPE.equalsIgnoreCase(type))
      .or(() -> Optional.of(beanName))
      .flatMap(typeRegistry::resolve)
      .orElse(null);
  }

  private void register(List<Annotation> annotations, String primaryName, TypeRef type, URI uri) {
    var primary = metaAnnotationResolver.hasRole(annotations, AutumnAnnotations.PRIMARY);
    var candidate = new BeanCandidate(type, primary, uri);

    addCandidate(primaryName, candidate);
    for (var alias : metaAnnotationResolver.valuesByRole(annotations, AutumnAnnotations.QUALIFIER)) {
      addCandidate(alias, candidate);
    }
  }

  private void addCandidate(String name, BeanCandidate candidate) {
    beansByName.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(candidate);
  }
}
