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
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

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
  Map<TypeRef, Map<String, LazyField>> lazyFields,
  Map<TypeRef, TypeSet> describedTypes
) {

  public static final TypeSet EMPTY = new TypeSet(Collections.emptySet());

  /**
   * Предельная глубина вложенности декораций, на которую заходит слияние наборов.
   * <p>
   * Поле «открытого» объекта может ссылаться на набор, внутри которого лежит он сам:
   * значение поля пришло от того же значения, которому это поле принадлежит. Слияние
   * такой пары разворачивает вложенность ещё на уровень, а копия делается на каждом
   * уровне — следующее слияние обходится дороже предыдущего, и так до исчерпания памяти.
   * Глубже предела наборы сливаются поверхностно: столкнувшиеся ключи остаются за левым
   * набором, вглубь слияние не идёт. Восьми уровней вложенности структур с запасом
   * хватает на то, что встречается в коде.
   */
  static final int MAX_MERGE_DEPTH = 8;

  public TypeSet {
    refs = compactRefs(refs);
    elementTypes = TypeDecorations.immutableCopy(elementTypes);
    localFields = TypeDecorations.immutableNestedCopy(localFields);
    lazyElements = TypeDecorations.immutableCopy(lazyElements);
    lazyFields = TypeDecorations.immutableNestedCopy(lazyFields);
    describedTypes = TypeDecorations.immutableCopy(describedTypes);
  }

  public TypeSet(Set<TypeRef> refs) {
    this(refs, Collections.emptyMap(), Collections.emptyMap(),
      Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
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
    return union(other, 0);
  }

  /**
   * Объединение двух наборов на известной глубине вложенности декораций.
   *
   * @param other второй набор.
   * @param depth сколько уровней декораций пройдено от набора, с которого началось
   *              слияние.
   * @return объединение наборов; декорации глубже {@link #MAX_MERGE_DEPTH} не сливаются.
   */
  TypeSet union(TypeSet other, int depth) {
    // Объединение набора с самим собой — это он же. Проверка по ссылке, а не по равенству:
    // она бесплатна, а случай частый. При слиянии путей расчёта по потоку у переменной,
    // которой ни одна ветка не касалась, по обеим сторонам лежит один и тот же набор —
    // без этой проверки на каждой такой переменной создавались бы копии всех коллекций.
    if (this == other) {
      return this;
    }
    if (other.isEmpty() && !other.hasDecorations()) {
      return this;
    }
    if (this.isEmpty() && !this.hasDecorations()) {
      return other;
    }
    var merged = new LinkedHashSet<>(this.refs);
    merged.addAll(other.refs);

    if (depth >= MAX_MERGE_DEPTH) {
      return new TypeSet(
        merged,
        TypeDecorations.mergedFlat(this.elementTypes, other.elementTypes, TypeSet::keepFirst),
        TypeDecorations.mergedNested(this.localFields, other.localFields, TypeSet::keepFirst),
        TypeDecorations.mergedFlat(this.lazyElements, other.lazyElements, LazyTypeSet::combine),
        TypeDecorations.mergedNested(this.lazyFields, other.lazyFields, LazyField::merge),
        TypeDecorations.mergedFlat(this.describedTypes, other.describedTypes, TypeSet::keepFirst)
      );
    }

    var nested = depth + 1;
    return new TypeSet(
      merged,
      TypeDecorations.mergedFlat(this.elementTypes, other.elementTypes,
        (first, second) -> first.union(second, nested)),
      TypeDecorations.mergedNested(this.localFields, other.localFields,
        (first, second) -> LocalField.merge(first, second, nested)),
      TypeDecorations.mergedFlat(this.lazyElements, other.lazyElements, LazyTypeSet::combine),
      TypeDecorations.mergedNested(this.lazyFields, other.lazyFields, LazyField::merge),
      TypeDecorations.mergedFlat(this.describedTypes, other.describedTypes,
        (first, second) -> first.union(second, nested))
    );
  }

  /**
   * Слияние на предельной глубине: значение остаётся за левым набором.
   *
   * @param first  значение левого набора.
   * @param second значение правого набора.
   * @param <V>    вид декорации.
   * @return значение левого набора.
   */
  private static <V> V keepFirst(V first, V second) {
    return first;
  }

  /**
   * Применяет {@code mapper} к каждой ссылке набора — вместе с ключами декораций и
   * ссылками внутри них: типами элементов коллекции и типами полей объекта.
   * <p>
   * Нужно, чтобы после структурной специализации привести полученные ссылки к
   * каноническим: иначе за одним именем оказываются две разные ссылки, и объединение
   * наборов их не схлопывает. Декорации переезжают на новую ссылку, а если в одну
   * каноническую ссылку сходятся несколько исходных — сливаются между собой. Ленивая
   * декорация не форсится: преобразование применяется к тому, что её источник вернёт
   * при чтении.
   *
   * @param mapper преобразование ссылки.
   * @return набор с преобразованными ссылками; исходный, если ни одна не изменилась.
   */
  public TypeSet mapRefs(UnaryOperator<TypeRef> mapper) {
    var mappedRefs = new LinkedHashSet<TypeRef>();
    var changed = false;
    for (var ref : refs) {
      var mapped = mapper.apply(ref);
      changed = changed || !mapped.equals(ref);
      mappedRefs.add(mapped);
    }
    var mappedElements = TypeDecorations.mapKeys(elementTypes, mapper, TypeSet::union,
      types -> types.mapRefs(mapper));
    var mappedFields = TypeDecorations.mapNestedKeys(localFields, mapper, LocalField::merge,
      field -> new LocalField(field.types().mapRefs(mapper), field.description()));
    // Ленивая декорация равна прежней по ключу, поэтому изменилось ли её содержимое,
    // сравнением не узнать — набор с ленивыми декорациями пересобирается всегда.
    var mappedDescribed = TypeDecorations.mapKeys(describedTypes, mapper, TypeSet::union,
      types -> types.mapRefs(mapper));
    var decorationsChanged = !mappedElements.equals(elementTypes)
      || !mappedFields.equals(localFields)
      || !mappedDescribed.equals(describedTypes);
    var hasLazyDecorations = !lazyElements.isEmpty() || !lazyFields.isEmpty();
    changed = changed || decorationsChanged || hasLazyDecorations;
    if (!changed) {
      return this;
    }
    return new TypeSet(
      mappedRefs,
      mappedElements,
      mappedFields,
      // Ленивую декорацию не форсим — оборачиваем: приведение применится к тому, что
      // источник вернёт при чтении. Ключ у обёртки прежний, поэтому равенство и слияние
      // ленивых ссылок работают как раньше.
      TypeDecorations.mapKeys(lazyElements, mapper, LazyTypeSet::combine, lazy -> mapLazy(lazy, mapper)),
      TypeDecorations.mapNestedKeys(lazyFields, mapper, LazyField::merge,
        field -> new LazyField(mapLazy(field.types(), mapper), field.description())),
      mappedDescribed
    );
  }

  private static LazyTypeSet mapLazy(LazyTypeSet lazy, UnaryOperator<TypeRef> mapper) {
    return new LazyTypeSet(lazy.key(), () -> lazy.get().mapRefs(mapper));
  }

  /** Есть ли у набора декорации (element/field/описываемые типы, в т.ч. ленивые). */
  private boolean hasDecorations() {
    return !elementTypes.isEmpty() || !localFields.isEmpty()
      || !lazyElements.isEmpty() || !lazyFields.isEmpty() || !describedTypes.isEmpty();
  }

  public TypeSet add(TypeRef ref) {
    var merged = new LinkedHashSet<>(this.refs);
    merged.add(ref);
    return new TypeSet(merged, this.elementTypes, this.localFields, this.lazyElements, this.lazyFields,
      this.describedTypes);
  }

  /**
   * Сузить набор до одного типа: остаётся только {@code ref} со своими декорациями,
   * декорации остальных типов отбрасываются.
   * <p>
   * Операция над множеством, а не проверка утверждения о типе: если {@code ref} в
   * наборе нет, результат пуст. Решение, что делать в этом случае — довериться
   * проверке типа в коде и подставить её тип или оставить набор как есть, — принимает
   * вызывающий.
   *
   * @param ref тип, до которого сужается набор.
   * @return набор из одного типа; {@link #EMPTY}, если такого типа в наборе не было.
   */
  public TypeSet retaining(TypeRef ref) {
    if (!refs.contains(ref)) {
      return EMPTY;
    }
    return filtered(kept -> kept.equals(ref));
  }

  /**
   * Убрать из набора один тип вместе с его декорациями.
   *
   * @param ref убираемый тип.
   * @return набор без указанного типа; текущий набор, если его там не было;
   *     {@link #EMPTY}, если он был единственным.
   */
  public TypeSet without(TypeRef ref) {
    if (!refs.contains(ref)) {
      return this;
    }
    if (refs.size() == 1) {
      return EMPTY;
    }
    return filtered(kept -> !kept.equals(ref));
  }

  /** Набор из типов, прошедших отбор, вместе с их декорациями. */
  private TypeSet filtered(Predicate<TypeRef> keep) {
    var keptRefs = new LinkedHashSet<TypeRef>();
    for (var ref : refs) {
      if (keep.test(ref)) {
        keptRefs.add(ref);
      }
    }
    return new TypeSet(
      keptRefs,
      TypeDecorations.filterByKey(elementTypes, keep),
      TypeDecorations.filterByKey(localFields, keep),
      TypeDecorations.filterByKey(lazyElements, keep),
      TypeDecorations.filterByKey(lazyFields, keep),
      TypeDecorations.filterByKey(describedTypes, keep)
    );
  }

  /**
   * Прикрепить к указанному {@code ref} (который должен быть в наборе)
   * информацию о типах элементов коллекции.
   *
   * @return новый {@link TypeSet}, идентичный текущему, но с дополненным
   *         {@code elementTypes[ref]} (через {@link #union(TypeSet)}).
   */
  public TypeSet withElement(TypeRef ref, TypeSet element) {
    if (element.isEmpty() && !element.hasDecorations()) {
      return this;
    }
    var newRefs = this.refs.contains(ref) ? this.refs : addRef(ref);
    var merged = new LinkedHashMap<>(this.elementTypes);
    merged.merge(ref, element, TypeSet::union);
    return new TypeSet(newRefs, merged, this.localFields, this.lazyElements, this.lazyFields,
      this.describedTypes);
  }

  /**
   * Прикрепить к {@code ref} типы, которые он <b>описывает</b>: так значение-описатель
   * несёт то, о чём оно говорит, не становясь этим само.
   * <p>
   * Описателей два: {@code Новый ОписаниеТипов("СправочникСсылка.Товары")} и
   * {@code ФабрикаXDTO.Тип(URI, Имя)}. Их значения читают
   * {@code ПривестиЗначение} и {@code ФабрикаXDTO.Создать} — каждый забирает
   * описанные типы обратно ({@link #getDescribedTypes(TypeRef)}).
   * <p>
   * Отдельная декорация, а не типы элементов: описатель — не коллекция, обходить его
   * нечем, и {@code Для Каждого} по нему ничего давать не должен.
   *
   * @param ref       тип-описатель (добавляется в набор, если отсутствует).
   * @param described типы, которые он описывает.
   * @return набор с дополненным {@code describedTypes[ref]}.
   */
  public TypeSet withDescribed(TypeRef ref, TypeSet described) {
    if (described.isEmpty() && !described.hasDecorations()) {
      return this;
    }
    var newRefs = this.refs.contains(ref) ? this.refs : addRef(ref);
    var merged = new LinkedHashMap<>(this.describedTypes);
    merged.merge(ref, described, TypeSet::union);
    return new TypeSet(newRefs, this.elementTypes, this.localFields, this.lazyElements, this.lazyFields,
      merged);
  }

  /**
   * Типы, которые описывает указанный {@code ref}.
   *
   * @param ref тип-описатель.
   * @return описанные типы; {@link #EMPTY}, если {@code ref} ничего не описывает.
   */
  public TypeSet getDescribedTypes(TypeRef ref) {
    return describedTypes.getOrDefault(ref, EMPTY);
  }

  /**
   * @return типы, описанные всеми описателями набора; {@link #EMPTY}, если их нет.
   */
  public TypeSet allDescribedTypes() {
    TypeSet acc = EMPTY;
    for (var described : describedTypes.values()) {
      acc = acc.union(described);
    }
    return acc;
  }

  /**
   * Прикрепить к {@code ref} <b>ленивый</b> тип элемента коллекции — элемент,
   * заданный {@code см.}-ссылкой на локальную функцию (см. {@link LazyTypeSet}).
   *
   * @return новый {@link TypeSet} с дополненным {@code lazyElements[ref]}.
   */
  public TypeSet withLazyElement(TypeRef ref, LazyTypeSet element) {
    var newRefs = this.refs.contains(ref) ? this.refs : addRef(ref);
    var merged = new LinkedHashMap<>(this.lazyElements);
    merged.merge(ref, element, LazyTypeSet::combine);
    return new TypeSet(newRefs, this.elementTypes, this.localFields, merged, this.lazyFields,
      this.describedTypes);
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
    var newRefs = this.refs.contains(ref) ? this.refs : addRef(ref);
    var merged = new LinkedHashMap<TypeRef, Map<String, LocalField>>();
    for (var entry : this.localFields.entrySet()) {
      merged.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
    }
    var bucket = merged.computeIfAbsent(ref, k -> new LinkedHashMap<>());
    bucket.merge(name, new LocalField(types, description), LocalField::merge);
    return new TypeSet(newRefs, this.elementTypes, merged, this.lazyElements, this.lazyFields,
      this.describedTypes);
  }

  /**
   * Прикрепить к указанному {@code ref} сразу несколько полей «открытого» объекта данных.
   * <p>
   * Набор неизменяемый, поэтому каждое добавление копирует уже накопленные поля: набирать
   * их по одному — квадратично по их числу. Там, где поля известны разом (колонки таблицы,
   * ключи из doc-комментария), нужен этот метод, а не {@link #withField} в цикле.
   *
   * @param ref    тип-владелец полей (добавляется в набор, если отсутствует).
   * @param fields добавляемые поля по именам; пустая карта ничего не меняет.
   * @return новый {@link TypeSet} с дополненным {@code localFields[ref]}.
   */
  public TypeSet withFields(TypeRef ref, Map<String, LocalField> fields) {
    if (fields.isEmpty()) {
      return this;
    }
    var newRefs = this.refs.contains(ref) ? this.refs : addRef(ref);
    // Чужие бакеты кладутся по ссылке: неизменяемую копию всего один раз сделает
    // конструктор. Копируется только тот бакет, который меняем.
    var merged = new LinkedHashMap<TypeRef, Map<String, LocalField>>(this.localFields);
    var bucket = new LinkedHashMap<>(this.localFields.getOrDefault(ref, Map.of()));
    for (var entry : fields.entrySet()) {
      bucket.merge(entry.getKey(), entry.getValue(), LocalField::merge);
    }
    merged.put(ref, bucket);
    return new TypeSet(newRefs, this.elementTypes, merged, this.lazyElements, this.lazyFields,
      this.describedTypes);
  }

  /**
   * Прикрепить к {@code ref} <b>ленивое</b> поле «открытого» объекта — поле,
   * тип которого задан {@code см.}-ссылкой на локальную функцию
   * (см. {@link LazyTypeSet}), с текстовым описанием из doc-комментария.
   *
   * @return новый {@link TypeSet} с дополненным {@code lazyFields[ref][name]}.
   */
  public TypeSet withLazyField(TypeRef ref, String name, LazyTypeSet types, String description) {
    var newRefs = this.refs.contains(ref) ? this.refs : addRef(ref);
    var merged = new LinkedHashMap<TypeRef, Map<String, LazyField>>();
    for (var entry : this.lazyFields.entrySet()) {
      merged.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
    }
    var bucket = merged.computeIfAbsent(ref, k -> new LinkedHashMap<>());
    bucket.merge(name, new LazyField(types, description), LazyField::merge);
    return new TypeSet(newRefs, this.elementTypes, this.localFields, this.lazyElements, merged,
      this.describedTypes);
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
