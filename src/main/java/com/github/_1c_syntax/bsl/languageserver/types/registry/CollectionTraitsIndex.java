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
package com.github._1c_syntax.bsl.languageserver.types.registry;

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * Коллекционные свойства типов: типы элементов «по умолчанию», признаки обхода
 * {@code Для Каждого} и индексатора {@code [...]} вместе с их текстовыми описаниями.
 * <p>
 * Свойства хранятся отдельно от членов типа: они не приходят от источников членов,
 * а объявляются при регистрации типа ({@link TypePackProvider.TypeDecl}) и потому
 * живут своим набором индексов.
 * Признаки обхода и индексатора разведены по {@link FileType}: один и тот же тип
 * бывает коллекцией в одном языке и не бывает в другом. Типы элементов от языка
 * не зависят.
 * <p>
 * Значения не перетираются там, где это оговорено ({@link #inherit}): первая
 * регистрация выигрывает. Все индексы конкурентные, внешней синхронизации не требуют.
 */
final class CollectionTraitsIndex {

  /**
   * Тип ↔ типы элементов «по умолчанию» для коллекции. Источник истины —
   * bsl-context ({@code ContextCollection.collectionElementTypes()}) или builtin JSON.
   * Хранятся «как объявлено»: канонизация до интернированных {@link TypeRef}
   * выполняется на чтении ({@link #defaultElementTypes(TypeRef, UnaryOperator)}).
   */
  private final Map<TypeRef, List<TypeRef>> defaultElementTypes = new ConcurrentHashMap<>();
  /** Тип ↔ {@code supportsForEach} в разрезе языка ({@code true} — обход {@code Для Каждого} разрешён). */
  private final Map<FileType, Map<TypeRef, Boolean>> supportsForEach = perFileType();
  /** Тип ↔ {@code supportsIndexAccess} в разрезе языка ({@code true} — индексатор {@code [...]} разрешён). */
  private final Map<FileType, Map<TypeRef, Boolean>> supportsIndexAccess = perFileType();
  /** Тип ↔ текстовое описание обхода {@code Для Каждого} из синтакс-помощника, в разрезе языка. */
  private final Map<FileType, Map<TypeRef, BilingualString>> forEachDescriptions = perFileType();
  /** Тип ↔ текстовое описание индексатора {@code [...]} из синтакс-помощника, в разрезе языка. */
  private final Map<FileType, Map<TypeRef, BilingualString>> indexAccessDescriptions = perFileType();

  /** Пустой контейнер с разрезами по всем языкам. */
  private static <V> Map<FileType, Map<TypeRef, V>> perFileType() {
    return Map.of(FileType.BSL, new ConcurrentHashMap<>(), FileType.OS, new ConcurrentHashMap<>());
  }

  /**
   * Записать коллекционные свойства, объявленные в описании типа. Пустые
   * значения не записываются: отсутствие признака и явное «нет» здесь неразличимы.
   *
   * @param decl     описание типа от платформенного провайдера.
   * @param ref      ссылка на регистрируемый тип.
   * @param fileType языковой скоуп, в котором действуют признаки.
   */
  void registerPack(TypePackProvider.TypeDecl decl, TypeRef ref, FileType fileType) {
    if (!decl.defaultElementTypes().isEmpty()) {
      defaultElementTypes.put(ref, List.copyOf(decl.defaultElementTypes()));
    }
    if (decl.supportsForEach()) {
      supportsForEach.get(fileType).put(ref, Boolean.TRUE);
    }
    if (decl.supportsIndexAccess()) {
      supportsIndexAccess.get(fileType).put(ref, Boolean.TRUE);
    }
    if (!decl.forEachDescription().isEmpty()) {
      forEachDescriptions.get(fileType).put(ref, decl.forEachDescription());
    }
    if (!decl.indexAccessDescription().isEmpty()) {
      indexAccessDescriptions.get(fileType).put(ref, decl.indexAccessDescription());
    }
  }

  /**
   * Скопировать коллекционные свойства типа-источника на другой тип: типы
   * элементов, признаки {@code Для Каждого} и индексатора вместе с описаниями.
   * <p>
   * Уже заданные у {@code target} свойства не перетираются: собственная
   * регистрация приоритетнее унаследованной. Копирование типа на себя —
   * пустая операция.
   *
   * @param target   тип-получатель.
   * @param source   тип-источник.
   * @param fileType языковой скоуп.
   */
  void inherit(TypeRef target, TypeRef source, FileType fileType) {
    if (target.equals(source)) {
      return;
    }
    var elements = defaultElementTypes.get(source);
    if (elements != null && !elements.isEmpty()) {
      defaultElementTypes.putIfAbsent(target, elements);
    }
    copyTrait(supportsForEach.get(fileType), target, source);
    copyTrait(supportsIndexAccess.get(fileType), target, source);
    copyTrait(forEachDescriptions.get(fileType), target, source);
    copyTrait(indexAccessDescriptions.get(fileType), target, source);
  }

  private static <V> void copyTrait(Map<TypeRef, V> trait, TypeRef target, TypeRef source) {
    var value = trait.get(source);
    if (value != null) {
      trait.putIfAbsent(target, value);
    }
  }

  /**
   * Выставить или снять признак обхода {@code Для Каждого} для типа, у которого нет
   * описания от платформенного провайдера. Тип элемента при этом не задаётся.
   * Операция идемпотентна.
   *
   * @param ref      ссылка на тип.
   * @param iterable {@code true} — пометить коллекцией; {@code false} — снять признак.
   * @param fileType языковой скоуп, в котором действует признак.
   */
  void setIterable(TypeRef ref, boolean iterable, FileType fileType) {
    if (iterable) {
      supportsForEach.get(fileType).put(ref, Boolean.TRUE);
    } else {
      supportsForEach.get(fileType).remove(ref);
    }
  }

  /** Забыть всё, что известно о типе, во всех языковых скоупах. */
  void remove(TypeRef ref) {
    defaultElementTypes.remove(ref);
    supportsForEach.values().forEach(byRef -> byRef.remove(ref));
    supportsIndexAccess.values().forEach(byRef -> byRef.remove(ref));
    forEachDescriptions.values().forEach(byRef -> byRef.remove(ref));
    indexAccessDescriptions.values().forEach(byRef -> byRef.remove(ref));
  }

  /**
   * Типы элементов коллекции. Возвращает {@link TypeSet#EMPTY}, если тип не
   * зарегистрирован как коллекция либо элементы гетерогенные.
   *
   * @param ref          ссылка на тип-коллекцию.
   * @param canonicalize приведение объявленного элемента к каноническому
   *                     интернированному {@link TypeRef}.
   * @return типы элементов.
   */
  TypeSet defaultElementTypes(TypeRef ref, UnaryOperator<TypeRef> canonicalize) {
    var raw = defaultElementTypes.get(ref);
    if (raw == null || raw.isEmpty()) {
      return TypeSet.EMPTY;
    }
    var canonical = new ArrayList<TypeRef>(raw.size());
    for (var element : raw) {
      canonical.add(canonicalize.apply(element));
    }
    return TypeSet.of(canonical);
  }

  /** {@code true}, если у типа разрешён обход {@code Для Каждого} в данном языке файла. */
  boolean supportsForEach(TypeRef ref, FileType fileType) {
    return Boolean.TRUE.equals(supportsForEach.get(fileType).get(ref));
  }

  /** {@code true}, если у типа разрешён индексатор {@code [...]} в данном языке файла. */
  boolean supportsIndexAccess(TypeRef ref, FileType fileType) {
    return Boolean.TRUE.equals(supportsIndexAccess.get(fileType).get(ref));
  }

  /** Описание обхода {@code Для Каждого} в указанной локали; пустая строка, если не задано. */
  String forEachDescription(TypeRef ref, FileType fileType, Language language) {
    return forEachDescriptions.get(fileType).getOrDefault(ref, BilingualString.EMPTY).forLanguage(language);
  }

  /** Описание индексатора {@code [...]} в указанной локали; пустая строка, если не задано. */
  String indexAccessDescription(TypeRef ref, FileType fileType, Language language) {
    return indexAccessDescriptions.get(fileType).getOrDefault(ref, BilingualString.EMPTY).forLanguage(language);
  }
}
