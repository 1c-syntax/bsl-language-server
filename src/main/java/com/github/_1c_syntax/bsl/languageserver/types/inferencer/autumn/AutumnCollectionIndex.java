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
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.EntryKind;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndexedEvent;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.parser.description.MethodDescription;
import com.github._1c_syntax.bsl.parser.description.TypeDescription;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Индекс прилепляемых коллекций фреймворка «ОСень» (autumn-collections):
 * отображение «имя коллекции → тип возвращаемого значения метода {@code Получить()}».
 * <p>
 * Прилепляемая коллекция — это класс, конструктор которого помечен
 * {@code &ПрилепляемаяКоллекция("Имя")}. Класс обязан реализовать метод
 * {@code Функция Получить() Экспорт}; именно его возвращаемый тип становится типом
 * внедряемой коллекции желудей в {@code &Пластилин(Тип = "Имя")}.
 * <p>
 * Тип читается из bsldoc-описания {@code Получить()}: вывода типа из выражения
 * {@code Возврат} пока нет, поэтому при отсутствии описания индекс возвращает
 * {@link TypeSet#EMPTY} — вызывающая сторона должна сделать фоллбэк (например,
 * прямой резолв имени коллекции через {@link TypeRegistry}).
 * <p>
 * Lifecycle и стратегия инвалидации скопированы с {@link AutumnBeanIndex}:
 * lock-free снимок на {@link ConcurrentHashMap}, ленивая первичная сборка,
 * точечное обновление при правке отдельного .os-документа и полный сброс на
 * переиндексацию библиотек или изменение класса-определения пользовательской
 * аннотации.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class AutumnCollectionIndex {

  private final OScriptLibraryIndex libraryIndex;
  private final ServerContextProvider serverContextProvider;
  private final TypeRegistry typeRegistry;
  private final AutumnMetaAnnotationResolver metaAnnotationResolver;

  /** Имя коллекции (lowercase) → типы её {@code Получить()}. */
  private final Map<String, TypeSet> typesByName = new ConcurrentHashMap<>();
  /** URI .os-файла → имена коллекций, зарегистрированные из него (для точечного удаления). */
  private final Map<URI, String> nameByUri = new ConcurrentHashMap<>();
  /**
   * Барьер первичной сборки: завершённый future — индекс построен; {@code null} —
   * не построен (соберётся лениво). Аналогично {@link AutumnBeanIndex}, сборка
   * ленива и зависит от полностью индексированного {@code AnnotationRepository},
   * поэтому порядок индексации классов коллекций и класса-определения аннотации
   * не важен.
   */
  private final AtomicReference<CompletableFuture<Void>> ready = new AtomicReference<>();

  /**
   * Разрешить тип прилепляемой коллекции по её имени.
   *
   * @return типы возвращаемого значения метода {@code Получить()}, описанные в
   *         bsldoc; пусто — если коллекции с таким именем нет либо её
   *         {@code Получить()} не описывает возвращаемый тип.
   */
  public TypeSet resolve(String collectionName) {
    if (collectionName.isBlank()) {
      return TypeSet.EMPTY;
    }
    ensureBuilt();
    return typesByName.getOrDefault(collectionName.toLowerCase(Locale.ROOT), TypeSet.EMPTY);
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
   * Обновить индекс при правке .os-документа. Обычный класс обновляется точечно
   * (удаление его прежнего вклада + переиндексация только его). Класс-определение
   * пользовательской аннотации ({@code &Аннотация}) затрагивает резолв ролей в
   * чужих классах — такой случай сбрасывает индекс на полную ленивую пересборку.
   * До первой сборки или для .bsl — ничего не делаем.
   */
  @EventListener
  public void handleDocumentChange(DocumentContextContentChangedEvent event) {
    var document = event.getSource();
    if (document.getFileType() != FileType.OS || ready.get() == null) {
      return;
    }
    if (isAnnotationDefinition(document)) {
      ready.set(null);
      return;
    }
    var uri = document.getUri();
    removeByUri(uri);
    indexDocument(uri);
  }

  /** Удалить вклад удалённого .os-документа. */
  @EventListener
  public void handleDocumentRemoved(ServerContextDocumentRemovedEvent event) {
    if (ready.get() == null) {
      return;
    }
    removeByUri(event.getUri());
  }

  /** Гарантировать, что индекс собран; сборка выполняется ровно один раз. */
  private void ensureBuilt() {
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
    typesByName.clear();
    nameByUri.clear();
    libraryIndex.findEntries(EntryKind.CLASS).stream()
      .map(OScriptLibraryIndex.LibraryEntry::uri)
      .distinct()
      .forEach(this::indexDocument);
  }

  /** Удалить из индекса вклад указанного .os-файла. */
  private void removeByUri(URI uri) {
    var name = nameByUri.remove(uri);
    if (name != null) {
      typesByName.remove(name);
    }
  }

  /** Несёт ли конструктор класса маркер {@code &Аннотация} (класс-определение пользовательской аннотации). */
  private static boolean isAnnotationDefinition(DocumentContext document) {
    return document.getSymbolTree().getConstructor()
      .map(constructor ->
        AutumnAnnotations.find(constructor.getAnnotations(), AutumnAnnotations.ANNOTATION_MARKER).isPresent())
      .orElse(false);
  }

  /** Проиндексировать прилепляемую коллекцию, если .os-класс — её реализация. */
  private void indexDocument(URI uri) {
    var hasClassEntry = libraryIndex.findEntriesByUri(uri).stream()
      .anyMatch(entry -> entry.kind() == EntryKind.CLASS);
    if (!hasClassEntry) {
      return;
    }
    serverContextProvider.getServerContext(uri)
      .map(serverContext -> serverContext.getDocument(uri))
      .ifPresent(document -> indexClass(document, uri));
  }

  private void indexClass(DocumentContext document, URI uri) {
    var symbolTree = document.getSymbolTree();
    symbolTree.getConstructor().ifPresent(constructor -> {
      var annotations = constructor.getAnnotations();
      // Класс-определение пользовательской аннотации ({@code &Аннотация("Имя")}) —
      // не реализация коллекции, его аннотации нужны лишь для разворачивания мета.
      if (AutumnAnnotations.find(annotations, AutumnAnnotations.ANNOTATION_MARKER).isPresent()) {
        return;
      }
      metaAnnotationResolver.findByRole(annotations, AutumnAnnotations.ATTACHABLE_COLLECTION)
        .flatMap(annotation -> metaAnnotationResolver
          .roleValues(annotation, AutumnAnnotations.ATTACHABLE_COLLECTION).stream()
          .findFirst())
        .filter(name -> !name.isBlank())
        .flatMap(name -> getterReturnType(symbolTree.getMethods())
          .map(types -> Map.entry(name, types)))
        .ifPresent(entry -> register(uri, entry.getKey(), entry.getValue()));
    });
  }

  /**
   * Резолвнуть возвращаемый тип экспортной функции {@code Получить()} из bsldoc.
   * Возвращает пусто, если такого метода нет, у него нет описания возвращаемого
   * значения или ни один из перечисленных типов не зарегистрирован в реестре.
   */
  private Optional<TypeSet> getterReturnType(List<MethodSymbol> methods) {
    return methods.stream()
      .filter(method -> AutumnAnnotations.ATTACHABLE_COLLECTION_GETTER.equalsIgnoreCase(method.getName())
        && method.isFunction()
        && method.isExport())
      .findFirst()
      .flatMap(MethodSymbol::getDescription)
      .map(MethodDescription::getReturnedValue)
      .map(this::resolveTypes)
      .filter(types -> !types.isEmpty());
  }

  private TypeSet resolveTypes(List<TypeDescription> typeDescriptions) {
    if (typeDescriptions.isEmpty()) {
      return TypeSet.EMPTY;
    }
    var refs = new LinkedHashSet<TypeRef>();
    for (var td : typeDescriptions) {
      typeRegistry.resolve(td.name()).ifPresent(refs::add);
    }
    return refs.isEmpty() ? TypeSet.EMPTY : TypeSet.of(new ArrayList<>(refs));
  }

  private void register(URI uri, String collectionName, TypeSet types) {
    var key = collectionName.toLowerCase(Locale.ROOT);
    typesByName.put(key, types);
    nameByUri.put(uri, key);
  }
}
