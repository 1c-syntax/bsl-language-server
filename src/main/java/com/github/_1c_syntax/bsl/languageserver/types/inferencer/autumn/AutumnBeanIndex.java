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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

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
 * <p>
 * Индекс хранится как неизменяемый {@link Snapshot} за атомарной ссылкой, поэтому
 * чтение ({@link #resolve}) полностью lock-free и параллельно — это важно, т.к.
 * вывод типов вызывается из диагностик в parallel-стримах. Сборка ленивая и
 * выполняется ровно один раз: гонка читателей разрешается через общий
 * {@link CompletableFuture} (остальные ждут тот же результат, без дублирующих
 * сканов). Правки/удаления применяются copy-on-write через CAS.
 */
@Component
@Scope(value = WorkspaceScope.SCOPE_NAME, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class AutumnBeanIndex {

  private final OScriptLibraryIndex libraryIndex;
  private final ServerContextProvider serverContextProvider;
  private final TypeRegistry typeRegistry;
  private final AutumnMetaAnnotationResolver metaAnnotationResolver;

  /**
   * Future с текущим неизменяемым снимком индекса; {@code null} — индекс не
   * построен (соберётся лениво при первом обращении).
   */
  private final AtomicReference<CompletableFuture<Snapshot>> snapshotRef = new AtomicReference<>();

  /**
   * Кандидат-желудь: тип компонента, признак приоритетного ({@code &Верховный})
   * и URI .os-файла, из которого он зарегистрирован (для пере-сканирования при
   * правке файла).
   */
  private record BeanCandidate(TypeRef type, boolean primary, URI sourceUri) {
  }

  /** Неизменяемый снимок индекса: имя/прозвище желудя (lowercase) → кандидаты. */
  private record Snapshot(Map<String, List<BeanCandidate>> beansByName) {
  }

  /**
   * Разрешить тип желудя по его имени или прозвищу.
   *
   * @return тип(ы) желудя; при конфликте имён предпочитаются помеченные
   *         {@code &Верховный}, иначе объединяются все кандидаты. Пусто, если
   *         желудь с таким именем не найден.
   */
  public TypeSet resolve(String name) {
    if (name.isBlank()) {
      return TypeSet.EMPTY;
    }
    var candidates = current().beansByName().get(name.toLowerCase(Locale.ROOT));
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
  public void invalidate() {
    snapshotRef.set(null);
  }

  /**
   * Пере-сканировать изменённый .os-документ. Правка класса-определения
   * аннотации ({@code &Аннотация}) меняет роль аннотации во всех использующих её
   * классах — такой случай сбрасывает индекс целиком; обычный класс обновляется
   * точечно (copy-on-write). Если индекс ещё не построен — ничего не делаем, он
   * соберётся целиком при первом обращении.
   */
  @EventListener
  public void handleDocumentChange(DocumentContextContentChangedEvent event) {
    var document = event.getSource();
    // Lock-free быстрый путь: во время ServerContext#populateContext события
    // сыплются из многих потоков, а индекс ещё не построен (snapshotRef == null).
    // .bsl на желуди не влияют; правки .os до построения захватит ленивая сборка.
    if (document.getFileType() != FileType.OS || snapshotRef.get() == null) {
      return;
    }
    if (isAnnotationDefinition(document)) {
      snapshotRef.set(null);
      return;
    }
    reindexDocument(document.getUri());
  }

  /** Текущий снимок; строит лениво ровно один раз — гонщики ждут общий future. */
  private Snapshot current() {
    while (true) {
      var pending = snapshotRef.get();
      if (pending != null) {
        return pending.join();
      }
      var fresh = new CompletableFuture<Snapshot>();
      if (snapshotRef.compareAndSet(null, fresh)) {
        try {
          var snapshot = build();
          fresh.complete(snapshot);
          return snapshot;
        } catch (RuntimeException e) {
          // Дать возможность пересобрать при следующем обращении.
          snapshotRef.compareAndSet(fresh, null);
          fresh.completeExceptionally(e);
          throw e;
        }
      }
      // Другой поток уже устанавливает future — повторим и присоединимся к нему.
    }
  }

  /** Точечно переиндексировать один .os-документ поверх текущего снимка (CAS-retry). */
  private void reindexDocument(URI uri) {
    while (true) {
      var pending = snapshotRef.get();
      if (pending == null) {
        return;
      }
      Snapshot base;
      try {
        base = pending.join();
      } catch (RuntimeException e) {
        return;
      }
      var beans = mutableCopyWithout(base, uri);
      indexDocument(uri, beans);
      if (snapshotRef.compareAndSet(pending, CompletableFuture.completedFuture(freeze(beans)))) {
        return;
      }
    }
  }

  private Snapshot build() {
    var beans = new HashMap<String, List<BeanCandidate>>();
    libraryIndex.findEntries(EntryKind.CLASS).stream()
      .map(OScriptLibraryIndex.LibraryEntry::uri)
      .distinct()
      .forEach(uri -> indexDocument(uri, beans));
    return freeze(beans);
  }

  /** Изменяемая копия снимка без кандидатов, зарегистрированных из указанного .os-файла. */
  private static Map<String, List<BeanCandidate>> mutableCopyWithout(Snapshot base, URI uri) {
    var beans = new HashMap<String, List<BeanCandidate>>();
    base.beansByName().forEach((name, candidates) -> {
      var retained = new ArrayList<BeanCandidate>();
      for (var candidate : candidates) {
        if (!uri.equals(candidate.sourceUri())) {
          retained.add(candidate);
        }
      }
      if (!retained.isEmpty()) {
        beans.put(name, retained);
      }
    });
    return beans;
  }

  private static Snapshot freeze(Map<String, List<BeanCandidate>> beans) {
    var frozen = new HashMap<String, List<BeanCandidate>>(beans.size());
    beans.forEach((name, candidates) -> frozen.put(name, List.copyOf(candidates)));
    return new Snapshot(Map.copyOf(frozen));
  }

  /** Несёт ли конструктор класса маркер {@code &Аннотация} (класс-определение пользовательской аннотации). */
  private static boolean isAnnotationDefinition(DocumentContext document) {
    return document.getSymbolTree().getConstructor()
      .map(constructor ->
        AutumnAnnotations.find(constructor.getAnnotations(), AutumnAnnotations.ANNOTATION_MARKER).isPresent())
      .orElse(false);
  }

  /** Проиндексировать желуди и фабрики .os-класса по URI в переданную (локальную) мапу. */
  private void indexDocument(URI uri, Map<String, List<BeanCandidate>> beans) {
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
        registerComponent(constructorAnnotations, entry.qualifiedName(), ownerType, uri, beans);
      }
    }

    // &Завязь размещается над методом и допустима только в классе-дубе —
    // иначе методы как фабрики не трактуем.
    if (metaAnnotationResolver.hasRole(constructorAnnotations, AutumnAnnotations.OAK)) {
      for (var method : symbolTree.getMethods()) {
        registerFactory(method, uri, beans);
      }
    }
  }

  private void registerComponent(List<Annotation> annotations, String defaultName, TypeRef ownerType, URI uri,
                                 Map<String, List<BeanCandidate>> beans) {
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
    register(annotations, name, ownerType, uri, beans);
  }

  private void registerFactory(MethodSymbol method, URI uri, Map<String, List<BeanCandidate>> beans) {
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
    register(annotations, name, beanType, uri, beans);
  }

  private @Nullable TypeRef factoryBeanType(Annotation factory, String beanName) {
    // Параметр Тип задаётся только по имени (позиционно можно лишь Значение).
    return AutumnAnnotations.stringParameter(factory, AutumnAnnotations.TYPE_PARAMETER)
      .filter(type -> !type.isBlank() && !AutumnAnnotations.BEAN_TYPE.equalsIgnoreCase(type))
      .or(() -> Optional.of(beanName))
      .flatMap(typeRegistry::resolve)
      .orElse(null);
  }

  private void register(List<Annotation> annotations, String primaryName, TypeRef type, URI uri,
                        Map<String, List<BeanCandidate>> beans) {
    var primary = metaAnnotationResolver.hasRole(annotations, AutumnAnnotations.PRIMARY);
    var candidate = new BeanCandidate(type, primary, uri);

    addCandidate(primaryName, candidate, beans);
    for (var alias : metaAnnotationResolver.valuesByRole(annotations, AutumnAnnotations.QUALIFIER)) {
      addCandidate(alias, candidate, beans);
    }
  }

  private static void addCandidate(String name, BeanCandidate candidate, Map<String, List<BeanCandidate>> beans) {
    beans.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(candidate);
  }
}
