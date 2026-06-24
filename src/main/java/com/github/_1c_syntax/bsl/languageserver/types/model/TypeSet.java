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
package com.github._1c_syntax.bsl.languageserver.types.model;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Неизменяемый, hash-stable union типов.
 * <p>
 * Используется как результат запроса типа выражения/символа: одно выражение
 * может иметь несколько возможных типов (например, переменная присваивается
 * в нескольких ветках разными значениями).
 * <p>
 * Дополнительно каждый {@link TypeRef} в наборе может быть «декорирован»
 * сайтовой информацией, которая не является частью идентичности типа в
 * {@link com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry},
 * но важна для качества подсказок:
 * <ul>
 *   <li>{@code elementTypes} — типы элементов коллекции
 *       ({@code Массив из СправочникСсылка.Товары});</li>
 *   <li>{@code localFields} — поля «открытого» объекта данных
 *       ({@code Структура} с известным набором ключей).</li>
 * </ul>
 * Декорации опциональны: пустые мапы означают «нет уточнений».
 *
 * @param refs         набор ссылок на типы, образующих union
 * @param elementTypes типы элементов коллекций, ключ — ссылка на тип-коллекцию
 * @param localFields  поля «открытого» объекта данных, ключ — ссылка на тип-объект,
 *                     значение — {@link LocalField} (типы + описание) по именам полей
 * <p>
 * Инвариант: {@code TypeSet} НЕ используется как ключ hash-коллекции
 * (везде в кэшах — значение), поэтому описание поля в составе
 * {@link LocalField} безопасно участвует в {@code equals/hashCode}.
 */
public record TypeSet(
  Set<TypeRef> refs,
  Map<TypeRef, TypeSet> elementTypes,
  Map<TypeRef, Map<String, LocalField>> localFields,
  Map<TypeRef, LazyTypeSet> lazyElements,
  Map<TypeRef, Map<String, LazyField>> lazyFields
) {

  public static final TypeSet EMPTY = new TypeSet(Collections.emptySet());

  public TypeSet {
    refs = compactRefs(refs);
    elementTypes = elementTypes == null || elementTypes.isEmpty()
      ? Collections.emptyMap()
      : Collections.unmodifiableMap(new LinkedHashMap<>(elementTypes));
    if (localFields == null || localFields.isEmpty()) {
      localFields = Collections.emptyMap();
    } else {
      var copy = new LinkedHashMap<TypeRef, Map<String, LocalField>>();
      for (var entry : localFields.entrySet()) {
        copy.put(entry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue())));
      }
      localFields = Collections.unmodifiableMap(copy);
    }
    lazyElements = lazyElements == null || lazyElements.isEmpty()
      ? Collections.emptyMap()
      : Collections.unmodifiableMap(new LinkedHashMap<>(lazyElements));
    if (lazyFields == null || lazyFields.isEmpty()) {
      lazyFields = Collections.emptyMap();
    } else {
      var copy = new LinkedHashMap<TypeRef, Map<String, LazyField>>();
      for (var entry : lazyFields.entrySet()) {
        copy.put(entry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue())));
      }
      lazyFields = Collections.unmodifiableMap(copy);
    }
  }

  public TypeSet(Set<TypeRef> refs) {
    this(refs, Collections.emptyMap(), Collections.emptyMap(),
      Collections.emptyMap(), Collections.emptyMap());
  }

  public static TypeSet of(TypeRef... refs) {
    return switch (refs.length) {
      case 0 -> EMPTY;
      case 1 -> new TypeSet(Set.of(refs[0]));
      default -> new TypeSet(new LinkedHashSet<>(Arrays.asList(refs)));
    };
  }

  public static TypeSet of(Collection<TypeRef> refs) {
    return switch (refs.size()) {
      case 0 -> EMPTY;
      case 1 -> new TypeSet(Set.of(refs.iterator().next()));
      default -> new TypeSet(new LinkedHashSet<>(refs));
    };
  }

  /**
   * Компактное неизменяемое представление набора ссылок: одно- и пустые наборы
   * (подавляющее большинство) обходятся без {@link LinkedHashSet}/обёртки, что
   * экономит память на каждый {@code TypeSet}. Для наборов из двух и более ссылок
   * сохраняется порядок вставки через {@link LinkedHashSet}.
   *
   * @param refs исходный набор ссылок на типы
   * @return неизменяемый набор тех же ссылок, компактный для размеров 0 и 1
   */
  private static Set<TypeRef> compactRefs(Set<TypeRef> refs) {
    return switch (refs.size()) {
      case 0 -> Collections.emptySet();
      case 1 -> Set.of(refs.iterator().next());
      default -> Collections.unmodifiableSet(new LinkedHashSet<>(refs));
    };
  }

  public boolean isEmpty() {
    return refs.isEmpty();
  }

  public int size() {
    return refs.size();
  }

  /**
   * @return объединение двух множеств типов; декорации (element/field) обоих
   *         наборов сохраняются, при пересечении ref union-ятся per-key.
   */
  public TypeSet union(TypeSet other) {
    if (other.isEmpty() && !other.hasDecorations()) {
      return this;
    }
    if (this.isEmpty() && !this.hasDecorations()) {
      return other;
    }
    var merged = new LinkedHashSet<>(this.refs);
    merged.addAll(other.refs);

    var mergedElements = new LinkedHashMap<>(this.elementTypes);
    for (var entry : other.elementTypes.entrySet()) {
      mergedElements.merge(entry.getKey(), entry.getValue(), TypeSet::union);
    }

    var mergedFields = new LinkedHashMap<TypeRef, Map<String, LocalField>>();
    for (var entry : this.localFields.entrySet()) {
      mergedFields.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
    }
    for (var entry : other.localFields.entrySet()) {
      var existing = mergedFields.computeIfAbsent(entry.getKey(), k -> new LinkedHashMap<>());
      for (var fieldEntry : entry.getValue().entrySet()) {
        existing.merge(fieldEntry.getKey(), fieldEntry.getValue(), LocalField::merge);
      }
    }

    var mergedLazyElements = new LinkedHashMap<>(this.lazyElements);
    for (var entry : other.lazyElements.entrySet()) {
      mergedLazyElements.merge(entry.getKey(), entry.getValue(), LazyTypeSet::combine);
    }

    var mergedLazyFields = new LinkedHashMap<TypeRef, Map<String, LazyField>>();
    for (var entry : this.lazyFields.entrySet()) {
      mergedLazyFields.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
    }
    for (var entry : other.lazyFields.entrySet()) {
      var existing = mergedLazyFields.computeIfAbsent(entry.getKey(), k -> new LinkedHashMap<>());
      for (var fieldEntry : entry.getValue().entrySet()) {
        existing.merge(fieldEntry.getKey(), fieldEntry.getValue(), LazyField::merge);
      }
    }

    return new TypeSet(merged, mergedElements, mergedFields, mergedLazyElements, mergedLazyFields);
  }

  /** Есть ли у набора декорации (element/field, в т.ч. ленивые). */
  private boolean hasDecorations() {
    return !elementTypes.isEmpty() || !localFields.isEmpty()
      || !lazyElements.isEmpty() || !lazyFields.isEmpty();
  }

  public TypeSet add(TypeRef ref) {
    var merged = new LinkedHashSet<>(this.refs);
    merged.add(ref);
    return new TypeSet(merged, this.elementTypes, this.localFields, this.lazyElements, this.lazyFields);
  }

  /**
   * Прикрепить к указанному {@code ref} (который должен быть в наборе)
   * информацию о типах элементов коллекции.
   *
   * @return новый {@link TypeSet}, идентичный текущему, но с дополненным
   *         {@code elementTypes[ref]} (через {@link #union(TypeSet)}).
   */
  public TypeSet withElement(TypeRef ref, TypeSet element) {
    Objects.requireNonNull(ref, "ref");
    Objects.requireNonNull(element, "element");
    if (element.isEmpty() && !element.hasDecorations()) {
      return this;
    }
    var newRefs = this.refs.contains(ref) ? this.refs : addRef(ref);
    var merged = new LinkedHashMap<>(this.elementTypes);
    merged.merge(ref, element, TypeSet::union);
    return new TypeSet(newRefs, merged, this.localFields, this.lazyElements, this.lazyFields);
  }

  /**
   * Прикрепить к {@code ref} <b>ленивый</b> тип элемента коллекции — элемент,
   * заданный {@code см.}-ссылкой на локальную функцию (см. {@link LazyTypeSet}).
   *
   * @return новый {@link TypeSet} с дополненным {@code lazyElements[ref]}.
   */
  public TypeSet withLazyElement(TypeRef ref, LazyTypeSet element) {
    Objects.requireNonNull(ref, "ref");
    Objects.requireNonNull(element, "element");
    var newRefs = this.refs.contains(ref) ? this.refs : addRef(ref);
    var merged = new LinkedHashMap<>(this.lazyElements);
    merged.merge(ref, element, LazyTypeSet::combine);
    return new TypeSet(newRefs, this.elementTypes, this.localFields, merged, this.lazyFields);
  }

  /**
   * Прикрепить к указанному {@code ref} одно поле «открытого» объекта данных
   * без текстового описания (рантайм-ключи: {@code Структура.Вставить(...)},
   * колонки ТЗ, литеральный {@code Новый Структура("К1,К2")}).
   *
   * @param ref   тип-владелец поля (добавляется в набор, если отсутствует)
   * @param name  имя поля
   * @param types типы значения поля
   * @return новый {@link TypeSet} с дополненным {@code localFields[ref][name]}.
   */
  public TypeSet withField(TypeRef ref, String name, TypeSet types) {
    return withField(ref, name, types, "");
  }

  /**
   * Прикрепить к указанному {@code ref} одно поле «открытого» объекта данных
   * с текстовым описанием (поля из doc-комментария: {@code * Поле - Тип - текст}).
   *
   * @param ref         тип-владелец поля (добавляется в набор, если отсутствует)
   * @param name        имя поля
   * @param types       типы значения поля
   * @param description текстовое описание поля (может быть пустым)
   * @return новый {@link TypeSet} с дополненным {@code localFields[ref][name]}.
   */
  public TypeSet withField(TypeRef ref, String name, TypeSet types, String description) {
    Objects.requireNonNull(ref, "ref");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(types, "types");
    var newRefs = this.refs.contains(ref) ? this.refs : addRef(ref);
    var merged = new LinkedHashMap<TypeRef, Map<String, LocalField>>();
    for (var entry : this.localFields.entrySet()) {
      merged.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
    }
    var bucket = merged.computeIfAbsent(ref, k -> new LinkedHashMap<>());
    bucket.merge(name, new LocalField(types, description), LocalField::merge);
    return new TypeSet(newRefs, this.elementTypes, merged, this.lazyElements, this.lazyFields);
  }

  /**
   * Прикрепить к {@code ref} <b>ленивое</b> поле «открытого» объекта — поле,
   * тип которого задан {@code см.}-ссылкой на локальную функцию
   * (см. {@link LazyTypeSet}), с текстовым описанием из doc-комментария.
   *
   * @return новый {@link TypeSet} с дополненным {@code lazyFields[ref][name]}.
   */
  public TypeSet withLazyField(TypeRef ref, String name, LazyTypeSet types, String description) {
    Objects.requireNonNull(ref, "ref");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(types, "types");
    var newRefs = this.refs.contains(ref) ? this.refs : addRef(ref);
    var merged = new LinkedHashMap<TypeRef, Map<String, LazyField>>();
    for (var entry : this.lazyFields.entrySet()) {
      merged.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
    }
    var bucket = merged.computeIfAbsent(ref, k -> new LinkedHashMap<>());
    bucket.merge(name, new LazyField(types, description), LazyField::merge);
    return new TypeSet(newRefs, this.elementTypes, this.localFields, this.lazyElements, merged);
  }

  /**
   * @return типы элементов коллекции для указанного {@code ref}, либо
   *         {@link #EMPTY}.
   */
  public TypeSet getElementTypes(TypeRef ref) {
    var eager = elementTypes.getOrDefault(ref, EMPTY);
    var lazy = lazyElements.get(ref);
    if (lazy == null) {
      return eager;
    }
    return eager.isEmpty() ? lazy.get() : eager.union(lazy.get());
  }

  /**
   * @return объединение типов элементов по всем коллекционным ref'ам в наборе.
   */
  public TypeSet getElementTypes() {
    TypeSet acc = EMPTY;
    for (var ts : elementTypes.values()) {
      acc = acc.union(ts);
    }
    for (var lazy : lazyElements.values()) {
      acc = acc.union(lazy.get());
    }
    return acc;
  }

  /**
   * @return поля «открытого» объекта для указанного {@code ref}, либо пустую
   *         мапу. Ленивые поля ({@code см.}-ссылки) форсятся на чтении.
   */
  public Map<String, LocalField> getLocalFields(TypeRef ref) {
    var eager = localFields.getOrDefault(ref, Collections.emptyMap());
    var lazy = lazyFields.get(ref);
    if (lazy == null) {
      return eager;
    }
    var merged = new LinkedHashMap<>(eager);
    for (var entry : lazy.entrySet()) {
      merged.merge(entry.getKey(), entry.getValue().materialize(), LocalField::merge);
    }
    return Collections.unmodifiableMap(merged);
  }

  /**
   * @return типы значения поля {@code name} по всем ref'ам, у которых это поле
   *         объявлено; case-insensitive.
   */
  public TypeSet getFieldTypes(String name) {
    TypeSet acc = EMPTY;
    var lookup = name.toLowerCase(Locale.ROOT);
    for (var fields : localFields.values()) {
      for (var entry : fields.entrySet()) {
        if (entry.getKey().toLowerCase(Locale.ROOT).equals(lookup)) {
          acc = acc.union(entry.getValue().types());
        }
      }
    }
    for (var fields : lazyFields.values()) {
      for (var entry : fields.entrySet()) {
        if (entry.getKey().toLowerCase(Locale.ROOT).equals(lookup)) {
          acc = acc.union(entry.getValue().types().get());
        }
      }
    }
    return acc;
  }

  /**
   * @return имена всех известных полей открытых объектов в наборе (включая
   *         ленивые поля — их имена известны без форса).
   */
  public Set<String> getAllFieldNames() {
    var names = new LinkedHashSet<String>();
    for (var fields : localFields.values()) {
      names.addAll(fields.keySet());
    }
    for (var fields : lazyFields.values()) {
      names.addAll(fields.keySet());
    }
    return Collections.unmodifiableSet(names);
  }

  private Set<TypeRef> addRef(TypeRef ref) {
    var copy = new LinkedHashSet<>(this.refs);
    copy.add(ref);
    return copy;
  }
}
