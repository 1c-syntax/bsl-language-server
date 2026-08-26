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
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.mdo.storage.form.FormAttribute;
import com.github._1c_syntax.bsl.mdo.storage.form.FormDynamicListAttribute;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Типы строки динамического списка — того, что отдают {@code ТекущиеДанные} и
 * {@code ДанныеСтроки} таблицы над ним.
 * <p>
 * Колонки такой строки в форме не объявлены: за списком стоит <b>основная таблица</b>
 * ({@code Catalog.Номенклатура}), и колонки — это её поля. Поэтому тип заводится на
 * конкретный реквизит-список: у двух списков над разными таблицами поля разные.
 * <p>
 * Полями основной таблицы служат свойства её типа: у ссылочного объекта это его
 * реквизиты вместе со стандартными ({@code Ссылка}, {@code Код}, {@code Наименование},
 * {@code ПометкаУдаления} …) — ровно то, что видно в списке. Читаются они лениво: на
 * регистрации формы обращение к членам сбивало бы epoch кэша членов.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
class DynamicListTypesRegistrar {

  private final TypeRegistry typeRegistry;
  private final FormDataTypesRegistrar formDataTypes;
  private final QueryTableResolver queryTableResolver;

  /**
   * Строка динамического списка и идентификатор её строки.
   *
   * @param rowRef   тип строки с колонками основной таблицы: его отдают
   *                 {@code ТекущиеДанные} и {@code ДанныеСтроки}.
   * @param rowIdRef тип идентификатора строки — то, что лежит в {@code ТекущаяСтрока},
   *                 {@code ТекущийРодитель} и в элементах {@code ВыделенныеСтроки};
   *                 {@code null}, если основная таблица не ссылочная.
   */
  record DynamicList(TypeRef rowRef, @Nullable TypeRef rowIdRef) {
  }

  /**
   * Строки динамических списков формы: {@code имя реквизита (lower) → строка списка}.
   * Список без разобранного источника данных в карту не попадает — таблица над ним
   * остаётся с обобщённым типом вида данных, как и до появления источника.
   *
   * @param attributes реквизиты формы.
   * @param suffixRu   суффикс имени типов этой формы.
   * @return строки списков; пусто, если динамических списков с известным источником нет.
   */
  Map<String, DynamicList> prepareRows(List<FormAttribute> attributes, String suffixRu) {
    var result = LinkedHashMap.<String, DynamicList>newLinkedHashMap(attributes.size());
    for (var attribute : attributes) {
      var row = prepareRow(attribute, suffixRu);
      if (row != null) {
        result.put(attribute.getName().toLowerCase(Locale.ROOT), row);
      }
    }
    return Map.copyOf(result);
  }

  /**
   * Строка одного реквизита.
   *
   * @return строка списка; {@code null}, если реквизит не динамический список, безымянный
   *   либо источник его данных неизвестен.
   */
  private @Nullable DynamicList prepareRow(FormAttribute attribute, String suffixRu) {
    if (!(attribute instanceof FormDynamicListAttribute list) || list.getName().isBlank()) {
      return null;
    }
    if (!hasKnownSource(list)) {
      return null;
    }
    return new DynamicList(registerRow(list, suffixRu), rowIdRef(list));
  }

  /**
   * Есть ли у списка источник, из которого возьмутся колонки строки. Спрашивать
   * об этом сам источник нельзя: на регистрации формы это стоило бы разбора всех
   * таблиц под каждый из тысяч списков конфигурации.
   * <p>
   * У списка с произвольным запросом основная таблица колонок не даёт — она
   * задаёт лишь динамическое чтение, — поэтому колонки у него берутся из
   * объявленного состава полей, пока не разбирается текст запроса.
   */
  private static boolean hasKnownSource(FormDynamicListAttribute list) {
    if (!list.getFields().isEmpty()) {
      return true;
    }
    return !list.isCustomQuery() && !list.getMainTable().isBlank();
  }

  /**
   * Тип идентификатора строки: строку списка над ссылочной таблицей платформа
   * адресует ссылкой, и её же отдаёт {@code ТекущаяСтрока}.
   * <p>
   * Имя основной таблицы — это mdoRef объекта ({@code Catalog.Номенклатура}), а
   * {@code Справочник.X}/{@code Catalog.X} в реестре ведёт на ссылочный тип: имя
   * резолвится напрямую, разбирать его на части не нужно. У виртуальной таблицы
   * регистра ссылочного типа нет, и идентификатор строки остаётся неизвестным.
   *
   * @return ссылочный тип основной таблицы; {@code null}, если его нет.
   */
  private @Nullable TypeRef rowIdRef(FormDynamicListAttribute list) {
    if (list.isCustomQuery()) {
      // У списка с произвольным запросом основная таблица задаёт лишь
      // динамическое чтение, а строку идентифицируют поля выборки запроса.
      return null;
    }
    var mainTable = list.getMainTable();
    return mainTable.isBlank() ? null : typeRegistry.resolve(mainTable).orElse(null);
  }

  /**
   * Регистрирует тип строки конкретного списка: {@code ДанныеФормыЭлементКоллекции.
   * ДинамическийСписок.<форма>.<реквизит>}.
   * <p>
   * Расширяет он не базовую строку данных формы, а строку вида данных
   * ({@code ДанныеФормыЭлементКоллекции.ДинамическийСписок}): специфика строки списка
   * — «Расширение данных строки для динамического списка» — объявлена там и нужна
   * всякой строке списка, независимо от его таблицы.
   */
  private TypeRef registerRow(FormDynamicListAttribute list, String suffixRu) {
    var kindRowName = FormPlatformTypes.FORM_DATA_COLLECTION_ITEM_RU + "." + TableDataKind.DYNAMIC_LIST.suffix();
    var baseRef = typeRegistry.resolve(kindRowName)
      .or(() -> typeRegistry.resolve(FormPlatformTypes.FORM_DATA_COLLECTION_ITEM_RU))
      .orElse(null);
    var rowRef = typeRegistry.registerConfigurationType(
      kindRowName + "." + suffixRu + "." + list.getName());
    if (baseRef != null) {
      typeRegistry.registerExtension(rowRef, baseRef, FileType.BSL);
    }
    // Отображаемое имя — платформенное: синтетический суффикс нужен реестру, чтобы
    // различать строки разных списков, а показывать надо реальный тип значения.
    typeRegistry.registerDisplayName(rowRef, BilingualString.of(
      FormPlatformTypes.FORM_DATA_COLLECTION_ITEM_RU, FormPlatformTypes.FORM_DATA_COLLECTION_ITEM_EN));
    var columns = list.getColumns();
    typeRegistry.registerMemberSource(rowRef, () -> columnMembers(list, columns), FileType.BSL);
    return rowRef;
  }

  /**
   * Колонки строки списка: объявленные в самом реквизите — впереди, за ними поля
   * его таблицы. Порядок задаёт исход дедупа в {@code getMembers}: объявление
   * формы точнее, чем поле таблицы под тем же именем.
   * <p>
   * Поля считаются лениво: на регистрации формы обращение к членам сбивало бы
   * epoch кэша членов.
   */
  private List<MemberDescriptor> columnMembers(FormDynamicListAttribute list, List<FormAttribute> columns) {
    var members = new ArrayList<MemberDescriptor>();
    if (!columns.isEmpty()) {
      members.addAll(formDataTypes.buildAttributeMembers(columns, formDataTypes.declaredAttributeTypes(columns)));
    }
    var tableName = list.isCustomQuery() ? "" : list.getMainTable();
    members.addAll(queryTableResolver.fields(tableName, list));
    return List.copyOf(members);
  }
}
