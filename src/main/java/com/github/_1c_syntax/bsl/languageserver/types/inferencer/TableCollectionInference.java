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
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.MethodCallNode;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Вывод типов у членов табличных коллекций — таблицы значений и табличной части
 * объекта, — результат которых зависит от <b>колонок конкретной коллекции</b>, а иногда
 * и от аргументов в точке вызова.
 * <p>
 * Платформа описывает такие члены обобщённо: {@code Выгрузить()} возвращает просто
 * {@code ТаблицаЗначений}, {@code ВыгрузитьКолонку()} — просто {@code Массив},
 * {@code Колонки} — просто коллекцию колонок. Состав же определяется тем, у чего
 * вызвали и что передали: {@code Товары.Выгрузить(, "Цена,Количество")} даёт таблицу
 * ровно из двух колонок. Объявленных типов для этого мало, поэтому уточнение считается
 * здесь, на месте вызова.
 * <p>
 * Колонки получателя берутся из двух источников сразу: {@code localFields} строки,
 * накопленные по месту (локальная {@code ТаблицаЗначений} с {@code Колонки.Добавить}),
 * и свойства зарегистрированного типа строки (специализированная строка табличной части).
 * <p>
 * Ограничение: имена колонок разбираются только из строкового литерала. Если список
 * собран в переменной, уточнить нечем — возвращается {@code null}, и работает общий путь.
 */
@Component
@RequiredArgsConstructor
class TableCollectionInference {

  private static final String UNLOAD = "Выгрузить";
  private static final String UNLOAD_COLUMNS = "ВыгрузитьКолонки";
  private static final String UNLOAD_COLUMN = "ВыгрузитьКолонку";
  private static final String COLUMNS = "Колонки";

  private static final String VALUE_TABLE = "ТаблицаЗначений";
  /** Строка таблицы значений — общая для всего пакета: её же резолвит {@link ExpressionTypeInferencer}. */
  static final String VALUE_TABLE_ROW = "СтрокаТаблицыЗначений";
  private static final String COLUMNS_COLLECTION = "КоллекцияКолонокТаблицыЗначений";
  private static final String COLUMN = "КолонкаТаблицыЗначений";
  private static final String ARRAY = "Массив";

  /** Позиция параметра со списком колонок: у {@code Выгрузить} он второй, после отбора. */
  private static final int UNLOAD_COLUMNS_ARG = 1;

  private static final int FIRST_ARG = 0;

  private final TypeRegistry typeRegistry;

  /**
   * Уточнённый тип члена табличной коллекции.
   *
   * @param receiver   типы получателя (то, что слева от точки).
   * @param memberName имя члена.
   * @param call       узел вызова, если член — метод; {@code null} для свойства.
   * @param fileType   язык файла-потребителя.
   * @return уточнённый тип; {@code null}, если правило неприменимо и нужен общий путь.
   */
  @Nullable
  TypeSet infer(TypeSet receiver, String memberName, @Nullable MethodCallNode call, FileType fileType) {
    if (receiver.isEmpty()) {
      return null;
    }
    var kind = call == null ? MemberKind.PROPERTY : MemberKind.METHOD;
    // Уточняем только реально существующие члены: иначе выдумали бы тип
    // несуществующему вызову и спрятали бы ошибку в коде.
    var member = resolveMember(receiver, memberName, kind, fileType);
    if (member == null) {
      return null;
    }
    // Дальше сверяемся с найденным дескриптором, а не с написанием в коде:
    // `matches` знает оба языка, поэтому `Unload` получает то же уточнение, что
    // и `Выгрузить`. Колонки считаются только под сработавшее правило.
    if (call == null) {
      return member.matches(COLUMNS) ? columnsCollection(columnsOf(receiver, fileType)) : null;
    }
    if (member.matches(UNLOAD_COLUMN)) {
      return unloadColumn(columnsOf(receiver, fileType), call);
    }
    if (member.matches(UNLOAD)) {
      return unloaded(selected(columnsOf(receiver, fileType), call, UNLOAD_COLUMNS_ARG));
    }
    if (member.matches(UNLOAD_COLUMNS)) {
      return unloaded(selected(columnsOf(receiver, fileType), call, FIRST_ARG));
    }
    return null;
  }

  /**
   * Член получателя с таким именем и видом. Ищется один раз: он же отвечает на вопрос
   * «существует ли член» и он же служит для выбора правила — по дескриптору, а не по
   * написанию, чтобы русское и английское имя вели себя одинаково.
   *
   * @return дескриптор; {@code null}, если такого члена у получателя нет.
   */
  @Nullable
  private MemberDescriptor resolveMember(TypeSet receiver, String memberName, MemberKind kind,
                                         FileType fileType) {
    for (var ref : receiver.refs()) {
      for (var member : typeRegistry.getMembers(ref, fileType)) {
        if (member.kind() == kind && member.matches(memberName)) {
          return member;
        }
      }
    }
    return null;
  }

  /**
   * Колонки табличной коллекции: {@code имя → типы значения}. Собираются и из уточнений,
   * накопленных на месте, и из свойств зарегистрированного типа строки. Generic-слот
   * ({@code <Имя колонки>}) колонкой не считается — это заготовка, а не колонка.
   */
  private Map<String, TypeSet> columnsOf(TypeSet receiver, FileType fileType) {
    var columns = new LinkedHashMap<String, TypeSet>();
    for (var ref : receiver.refs()) {
      var elements = receiver.getElementTypes(ref);
      for (var elementRef : elements.refs()) {
        collectColumnsOfRow(elements, elementRef, fileType, columns);
      }
    }
    return columns;
  }

  /** Колонки одной строки: сначала уточнения с места, затем свойства её типа. */
  private void collectColumnsOfRow(TypeSet elements, TypeRef elementRef, FileType fileType,
                                   Map<String, TypeSet> columns) {
    elements.getLocalFields(elementRef)
      .forEach((name, field) -> columns.merge(name, field.types(), TypeSet::union));
    for (var member : typeRegistry.getMembers(elementRef, fileType)) {
      if (member.kind() == MemberKind.PROPERTY && !member.generic()) {
        columns.merge(member.name(), member.returnTypes(), TypeSet::union);
      }
    }
  }

  /**
   * Колонки, оставшиеся после отбора по списку имён из аргумента. Аргумент не передан
   * или задан не литералом — остаются все: {@code Выгрузить()} без списка выгружает
   * таблицу целиком, а из переменной список не развернуть.
   */
  private static Map<String, TypeSet> selected(Map<String, TypeSet> columns, MethodCallNode call, int argIndex) {
    var names = literalNames(call, argIndex);
    if (names.isEmpty()) {
      return columns;
    }
    var selected = new LinkedHashMap<String, TypeSet>();
    for (var name : names) {
      columns.entrySet().stream()
        .filter(column -> column.getKey().equalsIgnoreCase(name))
        .forEach(column -> selected.put(column.getKey(), column.getValue()));
    }
    return selected.isEmpty() ? columns : selected;
  }

  /** Имена из строкового литерала-аргумента, разделённые запятыми. */
  private static List<String> literalNames(MethodCallNode call, int argIndex) {
    var arguments = call.arguments();
    if (arguments.size() <= argIndex) {
      return List.of();
    }
    var literal = ExpressionTypeInferencer.extractStringLiteral(arguments.get(argIndex));
    if (literal == null) {
      return List.of();
    }
    return Arrays.stream(literal.split(","))
      .map(String::trim)
      .filter(name -> !name.isEmpty())
      .toList();
  }

  /** {@code ТаблицаЗначений}, строка которой несёт указанные колонки. */
  @Nullable
  private TypeSet unloaded(Map<String, TypeSet> columns) {
    var tableRef = resolve(VALUE_TABLE);
    var rowRef = resolve(VALUE_TABLE_ROW);
    if (columns.isEmpty() || tableRef == null || rowRef == null) {
      return null;
    }
    var row = TypeSet.of(rowRef);
    for (var column : columns.entrySet()) {
      row = row.withField(rowRef, column.getKey(), column.getValue());
    }
    return TypeSet.of(tableRef).withElement(tableRef, row);
  }

  /** {@code Массив} значений одной колонки — с типом элемента этой колонки. */
  @Nullable
  private TypeSet unloadColumn(Map<String, TypeSet> columns, MethodCallNode call) {
    var names = literalNames(call, FIRST_ARG);
    if (names.size() != 1) {
      return null;
    }
    var wanted = names.get(0).toLowerCase(Locale.ROOT);
    var columnTypes = columns.entrySet().stream()
      .filter(column -> column.getKey().toLowerCase(Locale.ROOT).equals(wanted))
      .map(Map.Entry::getValue)
      .findFirst()
      .orElse(null);
    var arrayRef = resolve(ARRAY);
    if (columnTypes == null || columnTypes.isEmpty() || arrayRef == null) {
      return null;
    }
    return TypeSet.of(arrayRef).withElement(arrayRef, columnTypes);
  }

  /** Коллекция колонок, в которой каждая колонка таблицы — своё свойство. */
  @Nullable
  private TypeSet columnsCollection(Map<String, TypeSet> columns) {
    var collectionRef = resolve(COLUMNS_COLLECTION);
    var columnRef = resolve(COLUMN);
    if (columns.isEmpty() || collectionRef == null || columnRef == null) {
      return null;
    }
    var columnType = TypeSet.of(columnRef);
    var result = TypeSet.of(collectionRef);
    for (var name : columns.keySet()) {
      result = result.withField(collectionRef, name, columnType);
    }
    return result.withElement(collectionRef, columnType);
  }

  @Nullable
  private TypeRef resolve(String qualifiedName) {
    return typeRegistry.resolve(qualifiedName).orElse(null);
  }
}
