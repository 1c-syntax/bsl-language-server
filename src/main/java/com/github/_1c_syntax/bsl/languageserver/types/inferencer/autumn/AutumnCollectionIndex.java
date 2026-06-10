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
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.LibraryEntry;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.parser.description.MethodDescription;
import com.github._1c_syntax.bsl.parser.description.TypeDescription;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
 * Lifecycle ленивой сборки и инвалидации — в {@link AbstractAutumnLibraryIndex}.
 */
@Component
@WorkspaceScope
public class AutumnCollectionIndex extends AbstractAutumnLibraryIndex {

  private final TypeRegistry typeRegistry;
  private final AutumnMetaAnnotationResolver metaAnnotationResolver;

  /** Имя коллекции (lowercase) → типы её {@code Получить()}. */
  private final Map<String, TypeSet> typesByName = new ConcurrentHashMap<>();
  /** URI .os-файла → имя коллекции, зарегистрированное из него (для точечного удаления). */
  private final Map<URI, String> nameByUri = new ConcurrentHashMap<>();

  public AutumnCollectionIndex(OScriptLibraryIndex libraryIndex,
                               ServerContextProvider serverContextProvider,
                               TypeRegistry typeRegistry,
                               AutumnMetaAnnotationResolver metaAnnotationResolver) {
    super(libraryIndex, serverContextProvider);
    this.typeRegistry = typeRegistry;
    this.metaAnnotationResolver = metaAnnotationResolver;
  }

  /**
   * Разрешить тип прилепляемой коллекции по её имени.
   *
   * @param collectionName Имя прилепляемой коллекции.
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

  @Override
  protected void clearIndex() {
    typesByName.clear();
    nameByUri.clear();
  }

  @Override
  protected void indexClass(DocumentContext document, List<LibraryEntry> classEntries, URI uri) {
    var symbolTree = document.getSymbolTree();
    symbolTree.getConstructor().ifPresent(constructor -> {
      var annotations = constructor.getAnnotations();
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

  @Override
  protected void removeByUri(URI uri) {
    var name = nameByUri.remove(uri);
    if (name != null) {
      typesByName.remove(name);
    }
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
