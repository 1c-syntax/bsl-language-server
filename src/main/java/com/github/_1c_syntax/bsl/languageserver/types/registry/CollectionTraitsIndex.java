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
  /**
   * Тип ↔ признаки обхода и индексатора с описаниями, в разрезе языка. Тип, унаследовавший
   * признаки и не имеющий своих, держит тот же экземпляр {@link Traits}, что и источник: таких
   * типов на большой конфигурации сотни тысяч.
   */
  private final Map<FileType, Map<TypeRef, Traits>> traits =
    Map.of(FileType.BSL, new ConcurrentHashMap<>(), FileType.OS, new ConcurrentHashMap<>());

  /**
   * Признаки коллекции типа. Отсутствие признака и явное «нет» неразличимы: {@code false}
   * и пустое описание значат «не задано».
   *
   * @param forEach                 разрешён обход {@code Для Каждого}.
   * @param indexAccess             разрешён индексатор {@code [...]}.
   * @param forEachDescription     описание обхода из синтакс-помощника.
   * @param indexAccessDescription описание индексатора из синтакс-помощника.
   */
  private record Traits(boolean forEach, boolean indexAccess,
                        BilingualString forEachDescription, BilingualString indexAccessDescription) {

    static final Traits EMPTY = new Traits(false, false, BilingualString.EMPTY, BilingualString.EMPTY);

    boolean isEmpty() {
      return !forEach && !indexAccess && forEachDescription.isEmpty() && indexAccessDescription.isEmpty();
    }

    /** Свои признаки, а не заданные — из {@code fallback}. */
    Traits orElse(Traits fallback) {
      var merged = new Traits(
        forEach || fallback.forEach,
        indexAccess || fallback.indexAccess,
        forEachDescription.isEmpty() ? fallback.forEachDescription : forEachDescription,
        indexAccessDescription.isEmpty() ? fallback.indexAccessDescription : indexAccessDescription);
      return merged.equals(this) ? this : merged;
    }

    Traits withForEach(boolean value) {
      return new Traits(value, indexAccess, forEachDescription, indexAccessDescription);
    }
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
    var declared = new Traits(decl.supportsForEach(), decl.supportsIndexAccess(),
      decl.forEachDescription(), decl.indexAccessDescription());
    if (!declared.isEmpty()) {
      // Объявленное перетирает прежнее, не объявленное остаётся как было.
      traits.get(fileType).merge(ref, declared, (existing, update) -> update.orElse(existing));
    }
  }

  /**
   * Задать типы элементов коллекции явно — для типов, у которых нет описания от
   * платформенного провайдера. Первая регистрация выигрывает, как и при
   * наследовании ({@link #inherit}), поэтому вызывать надо до него.
   *
   * @param ref          ссылка на тип-коллекцию.
   * @param elementTypes типы элементов; пустой список ничего не меняет.
   */
  void registerDefaultElementTypes(TypeRef ref, List<TypeRef> elementTypes) {
    if (elementTypes.isEmpty()) {
      return;
    }
    defaultElementTypes.putIfAbsent(ref, List.copyOf(elementTypes));
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
    var byRef = traits.get(fileType);
    var inherited = byRef.get(source);
    if (inherited != null) {
      byRef.merge(target, inherited, Traits::orElse);
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
    traits.get(fileType).compute(ref, (key, existing) -> {
      var updated = (existing == null ? Traits.EMPTY : existing).withForEach(iterable);
      return updated.isEmpty() ? null : updated;
    });
  }

  /** Забыть всё, что известно о типе, во всех языковых скоупах. */
  void remove(TypeRef ref) {
    defaultElementTypes.remove(ref);
    traits.values().forEach(byRef -> byRef.remove(ref));
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
    return traitsOf(ref, fileType).forEach();
  }

  /** {@code true}, если у типа разрешён индексатор {@code [...]} в данном языке файла. */
  boolean supportsIndexAccess(TypeRef ref, FileType fileType) {
    return traitsOf(ref, fileType).indexAccess();
  }

  /** Описание обхода {@code Для Каждого} в указанной локали; пустая строка, если не задано. */
  String forEachDescription(TypeRef ref, FileType fileType, Language language) {
    return traitsOf(ref, fileType).forEachDescription().forLanguage(language);
  }

  /** Описание индексатора {@code [...]} в указанной локали; пустая строка, если не задано. */
  String indexAccessDescription(TypeRef ref, FileType fileType, Language language) {
    return traitsOf(ref, fileType).indexAccessDescription().forLanguage(language);
  }

  private Traits traitsOf(TypeRef ref, FileType fileType) {
    return traits.get(fileType).getOrDefault(ref, Traits.EMPTY);
  }
}
