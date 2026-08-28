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
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.mdo.storage.FormData;
import com.github._1c_syntax.bsl.mdo.storage.form.FormAttribute;
import com.github._1c_syntax.bsl.mdo.storage.form.FormDynamicListAttribute;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
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

  /** Платформенный тип ключа строки — им список адресует строку при виде ключа «КлючСтроки». */
  private static final String DYNAMIC_LIST_ROW_KEY = "КлючСтрокиДинамическогоСписка";

  /**
   * Поле, которым таблица называет ссылку на свою запись.
   */
  private static final String REF_RU = "Ссылка";

  /**
   * Стандартная картинка строки. Полем таблицы она не является — в запросе её не
   * выбрать, — но платформа называет её стандартным реквизитом строки списка
   * наравне со {@code Ссылка}, {@code ЭтоГруппа} и {@code Родитель}
   * (синтакс-помощник, {@code СтрокаДинамическогоСписка.Данные}), и именно на неё
   * ссылается оформление строк у 2077 таблиц разобранной конфигурации
   * ({@code <RowPictureDataPath>Список.DefaultPicture</RowPictureDataPath>}).
   * Типа помощник ей не объявляет, поэтому колонка отдаётся без типа.
   */
  private static final BilingualString DEFAULT_PICTURE =
    BilingualString.of("СтандартнаяКартинка", "DefaultPicture");

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
  Map<String, DynamicList> prepareRows(FormData data, String suffixRu) {
    var attributes = data.getAttributes();
    var usedFields = DynamicListUsedFields.collect(data);
    var result = LinkedHashMap.<String, DynamicList>newLinkedHashMap(attributes.size());
    for (var attribute : attributes) {
      var row = prepareRow(attribute, DynamicListUsedFields.of(usedFields, attribute), suffixRu);
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
  private @Nullable DynamicList prepareRow(FormAttribute attribute, List<String> usedFields, String suffixRu) {
    if (!(attribute instanceof FormDynamicListAttribute list) || list.getName().isBlank()) {
      return null;
    }
    if (!hasKnownSource(list)) {
      return null;
    }
    return new DynamicList(registerRow(list, usedFields, suffixRu), rowIdRef(list));
  }

  /**
   * Есть ли у списка источник, из которого возьмутся колонки строки. Спрашивать
   * об этом сам источник нельзя: на регистрации формы это стоило бы разбора всех
   * таблиц и текстов запросов под каждый из тысяч списков конфигурации.
   * <p>
   * У списка с произвольным запросом основная таблица колонок не даёт — она
   * задаёт лишь динамическое чтение, — поэтому колонками служат поля выборки
   * его запроса и объявленный состав полей.
   */
  private static boolean hasKnownSource(FormDynamicListAttribute list) {
    if (!list.getFields().isEmpty()) {
      return true;
    }
    return list.isCustomQuery() ? !list.getQueryText().isBlank() : !list.getMainTable().isBlank();
  }

  /**
   * Тип идентификатора строки — того, что отдают {@code ТекущаяСтрока},
   * {@code ТекущийРодитель} и элементы {@code ВыделенныеСтроки}. Чем список
   * адресует строку, задаёт его вид ключа:
   * <ul>
   *   <li>{@code Авто} — ссылкой основной таблицы;</li>
   *   <li>{@code ЗначениеПоля} — значением поля ключа;</li>
   *   <li>{@code КлючСтроки} — {@code КлючСтрокиДинамическогоСписка};</li>
   *   <li>{@code НомерСтроки} — числом.</li>
   * </ul>
   * Считается на регистрации формы, а не в источнике членов: под
   * {@code ВыделенныеСтроки} регистрируется специализация массива, а регистрация
   * изнутри {@code getMembers} сбивала бы epoch кэша членов.
   *
   * @return тип идентификатора строки; {@code null}, если он неизвестен.
   */
  private @Nullable TypeRef rowIdRef(FormDynamicListAttribute list) {
    return switch (list.getKeyType()) {
      case ROW_NUMBER -> typeRegistry.resolve(FormPlatformTypes.NUMBER_RU).orElse(null);
      case ROW_KEY -> typeRegistry.resolve(DYNAMIC_LIST_ROW_KEY).orElse(null);
      case FIELD_VALUE -> keyFieldRef(list);
      default -> mainTableRef(list);
    };
  }

  /**
   * Ссылочный тип основной таблицы. Имя основной таблицы — это mdoRef объекта
   * ({@code Catalog.Номенклатура}), а {@code Справочник.X}/{@code Catalog.X}
   * в реестре ведёт на ссылочный тип: имя резолвится напрямую. У виртуальной
   * таблицы регистра ссылочного типа нет.
   * <p>
   * Именем разрешается не всякая таблица: {@code ExternalDataSource.X.Table.Y}
   * псевдонимом типа не является. Ссылку такой строки называет поле
   * {@code Ссылка} самой таблицы — его и спрашиваем, но только когда имя не
   * разрешилось: разбирать таблицу под каждый из тысяч списков конфигурации на
   * регистрации формы слишком дорого.
   */
  private @Nullable TypeRef mainTableRef(FormDynamicListAttribute list) {
    if (list.isCustomQuery()) {
      // У списка с произвольным запросом основная таблица задаёт лишь
      // динамическое чтение, а строку идентифицируют поля выборки запроса.
      return null;
    }
    var mainTable = list.getMainTable();
    if (mainTable.isBlank()) {
      return null;
    }
    return typeRegistry.resolve(mainTable).orElseGet(() -> refFieldRef(mainTable));
  }

  /**
   * Тип поля {@code Ссылка} таблицы. У необъектной таблицы такого поля нет
   * вовсе — тогда ссылки у строки нет, и адресует её вид ключа.
   */
  private @Nullable TypeRef refFieldRef(String tableName) {
    var types = QueryFieldChain.memberTypes(queryTableResolver.fields(tableName), REF_RU);
    return types.size() == 1 ? types.refs().iterator().next() : null;
  }

  /**
   * Тип поля ключа. Берётся, только если поле ключа одно и тип у него один:
   * чем платформа адресует строку по нескольким полям, в синтакс-помощнике не
   * сказано, а правдоподобная догадка тут хуже незнания.
   */
  private @Nullable TypeRef keyFieldRef(FormDynamicListAttribute list) {
    if (list.getKeyFields().size() != 1) {
      return null;
    }
    var keyField = list.getKeyFields().get(0);
    var tableName = list.isCustomQuery() ? "" : list.getMainTable();
    return queryTableResolver.fields(tableName, list).stream()
      .filter(member -> member.matches(keyField))
      .findFirst()
      .map(MemberDescriptor::returnTypes)
      .filter(types -> types.size() == 1)
      .map(types -> types.refs().iterator().next())
      .orElse(null);
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
  private TypeRef registerRow(FormDynamicListAttribute list, List<String> usedFields, String suffixRu) {
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
    typeRegistry.registerMemberSource(rowRef, () -> columnMembers(list, columns, usedFields), FileType.BSL);
    return rowRef;
  }

  /**
   * Колонки строки списка: объявленные в самом реквизите — впереди, за ними поля
   * его таблицы. Порядок задаёт исход дедупа в {@code getMembers}: объявление
   * формы точнее, чем поле таблицы под тем же именем.
   * <p>
   * Полей у таблицы больше, чем в строке: платформа читает только те, которые
   * форма где-то называет, — поэтому поля таблицы отбираются по этому набору.
   * Стандартная картинка строки — такое же поле: она есть, только если форма
   * на неё ссылается.
   * <p>
   * Поле, которое форма называет, а источник не знает, колонкой всё равно
   * становится — но без типа: взять его неоткуда. Так выглядит битое свойство.
   * <p>
   * Поля считаются лениво: на регистрации формы обращение к членам сбивало бы
   * epoch кэша членов.
   */
  private List<MemberDescriptor> columnMembers(FormDynamicListAttribute list,
                                               List<FormAttribute> columns,
                                               List<String> usedFields) {
    var members = new ArrayList<MemberDescriptor>();
    if (!columns.isEmpty()) {
      members.addAll(formDataTypes.buildAttributeMembers(columns, formDataTypes.declaredAttributeTypes(columns)));
    }
    var tableName = list.isCustomQuery() ? "" : list.getMainTable();
    var tableFields = queryTableResolver.fields(tableName, list);
    tableFields.stream()
      .filter(field -> isUsed(field, usedFields))
      .forEach(members::add);
    var picture = MdoMemberFactory.property(DEFAULT_PICTURE, TypeSet.EMPTY).withStandardLibrary(true);
    if (isUsed(picture, usedFields)) {
      members.add(picture);
    }
    usedFields.stream()
      .filter(name -> members.stream().noneMatch(member -> member.matches(name)))
      .map(name -> MdoMemberFactory.property(BilingualString.of(name), chainTypes(name, tableFields)))
      .forEach(members::add);
    return List.copyOf(members);
  }

  /**
   * Типы колонки, которой поля таблицы не нашлось. У разыменованного поля имя
   * составное ({@code Организация.ИНН}), и тип берётся у последнего звена;
   * у остальных типа нет — так выглядит битое свойство.
   */
  private TypeSet chainTypes(String name, Collection<MemberDescriptor> tableFields) {
    var segments = List.of(name.split("\\.", -1));
    return segments.size() < 2 ? TypeSet.EMPTY : QueryFieldChain.types(typeRegistry, tableFields, segments);
  }

  /**
   * Называет ли форма это поле. Имя стандартного реквизита форма пишет
   * по-английски, а код — по-русски, поэтому сверяются оба написания члена.
   */
  private static boolean isUsed(MemberDescriptor member, List<String> usedFields) {
    return usedFields.stream().anyMatch(member::matches);
  }
}
