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

import com.github._1c_syntax.bsl.context.api.ContextNames;
import com.github._1c_syntax.bsl.context.api.ContextQueryTableField;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.mdo.children.ExternalDataSourceCubeDimensionTable;
import com.github._1c_syntax.bsl.mdo.children.ExternalDataSourceTable;
import com.github._1c_syntax.bsl.mdo.support.TableDataType;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Поля таблицы, которые объявляет платформа: стандартные реквизиты, псевдополя
 * ({@code Представление}, {@code МоментВремени}) и поля виртуальных таблиц
 * ({@code ПериодМесяц}, {@code <Имя ресурса>Оборот}).
 * <p>
 * Спрашивается последним: у стандартного реквизита платформа объявляет тип
 * обобщённо ({@code Владелец} у справочника — объединение пяти ссылочных
 * семейств), а метаданные знают настоящий, поэтому источник метаданных идёт
 * раньше и выигрывает.
 * <p>
 * Если платформа не установлена, источник не отдаёт ничего: подставить вместо
 * полей таблицы члены ссылочного типа объекта — значит выдать за них другой
 * набор, в котором нет ни {@code Представление}, ни {@code МоментВремени},
 * зато есть методы.
 */
@Component
@WorkspaceScope
@Order(PlatformTableFieldSource.ORDER)
@RequiredArgsConstructor
class PlatformTableFieldSource implements QueryTableFieldSource {

  static final int ORDER = 40;

  /**
   * Поля, которые есть только у таблицы с объектными данными.
   */
  private static final Set<String> OBJECT_ONLY_FIELDS = Set.of("Ссылка", "Представление");

  /**
   * Поле, которое есть только у иерархической таблицы измерения.
   */
  private static final Set<String> PARENT = Set.of("Родитель");

  private final TypeRegistry typeRegistry;

  @Override
  public List<MemberDescriptor> fields(QueryTableRequest request) {
    var table = request.table();
    if (table == null) {
      return List.of();
    }
    var result = new ArrayList<MemberDescriptor>(table.fields().size());
    for (var field : table.fields()) {
      // Голый плейсхолдер называет вид детей объекта метаданных целиком — такое
      // поле материализуют источники, которые знают их настоящие типы.
      if (!belongsTo(field, request) || QueryTableFieldSource.isBarePlaceholder(field)) {
        continue;
      }
      if (QueryTableFieldSource.placeholderNames(field).isEmpty()) {
        result.add(member(field, request.nameBindings()));
      } else {
        result.addAll(expand(field, request));
      }
    }
    return List.copyOf(result);
  }

  /**
   * Есть ли поле у этой конкретной таблицы. Одна страница синтакс-помощника
   * описывает целое семейство таблиц, и часть полей платформа оговаривает
   * отдельно: {@code Ссылка} и {@code Представление} — «доступно только для
   * объектных таблиц», {@code Родитель} таблицы измерения — «существует только
   * для иерархических таблиц измерений». Объектность и иерархичность объявлены
   * в метаданных самой таблицы.
   */
  private static boolean belongsTo(ContextQueryTableField field, QueryTableRequest request) {
    var mdo = request.mdo();
    if (mdo instanceof ExternalDataSourceTable table) {
      return table.getTableDataType() == TableDataType.OBJECT_DATA || !isNamed(field, OBJECT_ONLY_FIELDS);
    }
    if (mdo instanceof ExternalDataSourceCubeDimensionTable dimensionTable) {
      return dimensionTable.isHierarchical() || !isNamed(field, PARENT);
    }
    return true;
  }

  private static boolean isNamed(ContextQueryTableField field, Set<String> names) {
    return names.contains(field.name().getName());
  }

  /**
   * Материализует поле, у которого имя составлено платформой из плейсхолдера и
   * своей части ({@code <Имя ресурса>Оборот}, {@code Субконто<Номер субконто>}):
   * структуру имени задаёт платформа, а подставляемые имена — метаданные.
   */
  private List<MemberDescriptor> expand(ContextQueryTableField field, QueryTableRequest request) {
    var placeholder = QueryTableFieldSource.placeholderNames(field).get(0);
    var values = QueryTablePlaceholders.expansionValues(placeholder, request);
    if (values.isEmpty()) {
      return List.of();
    }
    var template = member(field, request.nameBindings());
    var result = new ArrayList<MemberDescriptor>(values.size());
    for (var value : values) {
      result.add(substituteName(template, field, placeholder, value));
    }
    return result;
  }

  /**
   * Подставляет имя в плейсхолдер обеих сторон имени члена. Позиции берутся
   * структурно из разбора имени — угловые скобки в LS не парсятся.
   */
  private static MemberDescriptor substituteName(MemberDescriptor template,
                                                 ContextQueryTableField field,
                                                 String placeholder,
                                                 String value) {
    var ru = substitute(field.name().getName(), placeholder, value);
    var en = substitute(field.name().getAlias(), placeholder, value);
    return template.withBilingualName(BilingualString.of(ru, en)).withGeneric(false);
  }

  private static String substitute(String name, String placeholder, String value) {
    for (var found : ContextNames.placeholders(name)) {
      if (found.name().equals(placeholder)) {
        return name.substring(0, found.start()) + value + name.substring(found.end());
      }
    }
    return name;
  }

  /**
   * Член из поля таблицы. Тип поля бывает шаблонным
   * ({@code СправочникСсылка.<Имя справочника>}) — в него подставляется имя
   * объекта, взятое из имени самой таблицы.
   */
  private MemberDescriptor member(ContextQueryTableField field, Map<String, String> nameBindings) {
    var name = BilingualString.of(field.name().getName(), field.name().getAlias());
    var descriptor = MdoMemberFactory.property(name, canonicalTypes(field)).withStandardLibrary(true);
    if (!field.description().isBlank()) {
      descriptor = descriptor.withBilingualDescription(BilingualString.of(field.description()));
    }
    if (nameBindings.isEmpty()) {
      return descriptor;
    }
    return descriptor.specialize(nameBindings, typeRegistry::canonicalRef);
  }

  /**
   * Типы поля, приведённые к каноническим. Вид типа приходит извне — из
   * справки либо из встроенного пака, — а какой он на самом деле, знает
   * реестр: имя там уже зарегистрировано.
   */
  private TypeSet canonicalTypes(ContextQueryTableField field) {
    var types = BslContextPlatformTypesProvider.typeSet(field.types());
    if (types.isEmpty()) {
      return types;
    }
    return TypeSet.of(types.refs().stream().map(typeRegistry::canonicalRef).toList());
  }
}
