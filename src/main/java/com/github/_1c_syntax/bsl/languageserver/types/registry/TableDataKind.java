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

import org.jspecify.annotations.Nullable;

/**
 * Вид данных, отображаемых таблицей формы. Расширение таблицы — единственное, что
 * определяется не видом элемента, а типом данных за ним: одна и та же
 * {@code ТаблицаФормы} над динамическим списком и над табличной частью получает
 * разные наборы свойств и событий.
 * <p>
 * У каждого вида — суффикс синтетического типа ({@code ТаблицаФормы.ДинамическийСписок})
 * и qualifiedName расширения таблицы в синтакс-помощнике.
 */
enum TableDataKind {

  DYNAMIC_LIST("ДинамическийСписок", FormPlatformTypes.TABLE_EXTENSION_PREFIX + "динамического списка"),
  TABULAR_SECTION("ТабличнаяЧасть", FormPlatformTypes.TABLE_EXTENSION_PREFIX + "табличных частей"),
  VALUE_TABLE("ТаблицаЗначений", FormPlatformTypes.TABLE_EXTENSION_PREFIX + "таблицы значений"),
  VALUE_TREE("ДеревоЗначений", FormPlatformTypes.TABLE_EXTENSION_PREFIX + "дерева значений"),
  VALUE_LIST("СписокЗначений", FormPlatformTypes.TABLE_EXTENSION_PREFIX + "списка значений"),
  // Своего расширения у набора записей синтакс-помощник не объявляет. Таблица над ним в
  // выгрузке формы несёт `<RowFilter>` (`ОтборСтрок`) — единственное свойство расширений
  // табличных частей и таблицы значений, у которых состав членов совпадает, — поэтому
  // взято расширение табличных частей: набор записей на форме — та же коллекция строк.
  RECORD_SET("НаборЗаписей", FormPlatformTypes.TABLE_EXTENSION_PREFIX + "табличных частей"),
  // Части компоновщика настроек. Таблица над ними смотрит не на реквизит, а вглубь:
  // `Отчет.КомпоновщикНастроек.Настройки.Выбор`. Имена типов сверены по
  // синтакс-помощнику (`НастройкиКомпоновкиДанных`), а не выведены из имён расширений:
  // у `Выбор` тип называется `ВыбранныеПоляКомпоновкиДанных`, а не `ВыборКомпоновкиДанных`.
  DCS_SELECTED_FIELDS("ВыбранныеПоляКомпоновкиДанных",
    FormPlatformTypes.TABLE_EXTENSION_PREFIX + "выбранных полей компоновки данных"),
  DCS_FILTER("ОтборКомпоновкиДанных",
    FormPlatformTypes.TABLE_EXTENSION_PREFIX + "отбора компоновки данных"),
  DCS_ORDER("ПорядокКомпоновкиДанных",
    FormPlatformTypes.TABLE_EXTENSION_PREFIX + "порядка компоновки данных"),
  DCS_CONDITIONAL_APPEARANCE("УсловноеОформлениеКомпоновкиДанных",
    FormPlatformTypes.TABLE_EXTENSION_PREFIX + "условного оформления компоновки данных"),
  DCS_DATA_PARAMETERS("ЗначенияПараметровДанныхКомпоновкиДанных",
    FormPlatformTypes.TABLE_EXTENSION_PREFIX + "значений параметров компоновки данных"),
  DCS_USER_FIELDS("ПользовательскиеПоляКомпоновкиДанных",
    FormPlatformTypes.TABLE_EXTENSION_PREFIX + "пользовательских полей компоновки данных"),
  DCS_AVAILABLE_FIELDS("ДоступныеПоляКомпоновкиДанных",
    FormPlatformTypes.TABLE_EXTENSION_PREFIX + "доступных полей компоновки данных"),
  DCS_SETTINGS_STRUCTURE("КоллекцияЭлементовСтруктурыНастроекКомпоновкиДанных",
    FormPlatformTypes.TABLE_EXTENSION_PREFIX + "структуры настроек компоновки данных"),
  DCS_SETTINGS_STRUCTURE_ITEM("СтруктураНастроекКомпоновкиДанных",
    FormPlatformTypes.TABLE_EXTENSION_PREFIX + "объекта структура настроек компоновки данных"),
  DCS_GROUP_FIELDS("ПоляГруппировкиКомпоновкиДанных",
    FormPlatformTypes.TABLE_EXTENSION_PREFIX + "полей группировки компоновки данных"),
  DCS_APPEARANCE_FIELDS("ОформляемыеПоляКомпоновкиДанных",
    FormPlatformTypes.TABLE_EXTENSION_PREFIX + "оформляемых полей компоновки данных"),
  DCS_USER_SETTINGS("ПользовательскиеНастройкиКомпоновкиДанных",
    FormPlatformTypes.TABLE_EXTENSION_PREFIX + "пользовательских настроек компоновки данных"),
  DCS_USER_FIELD_CASE_VARIANTS("ВариантыПользовательскогоПоляВыборКомпоновкиДанных",
    FormPlatformTypes.TABLE_EXTENSION_PREFIX + "вариантов пользовательского поля выбора компоновки данных"),
  // Не компоновка: таблица над самой диаграммой Ганта и над отбором динамического списка.
  GANTT_CHART("ДиаграммаГанта",
    FormPlatformTypes.TABLE_EXTENSION_PREFIX + "диаграммы Ганта"),
  FILTER("Отбор",
    FormPlatformTypes.TABLE_EXTENSION_PREFIX + "отбора");

  private final String suffix;
  private final String extensionName;

  TableDataKind(String suffix, String extensionName) {
    this.suffix = suffix;
    this.extensionName = extensionName;
  }

  String suffix() {
    return suffix;
  }

  String extensionName() {
    return extensionName;
  }

  /**
   * Тип идентификатора строки — того, что лежит в {@code ТекущаяСтрока},
   * {@code ТекущийРодитель} и в элементах {@code ВыделенныеСтроки}. Синтакс-помощник
   * называет его в описании расширения: у частей компоновщика настроек это
   * {@code ИдентификаторКомпоновкиДанных}, у диаграммы Ганта — свой идентификатор.
   * Там, где таблица смотрит на данные формы, описание типа не называет, но он известен
   * из самих данных: строку адресует числовой идентификатор
   * ({@code ДанныеФормыЭлементКоллекции.ПолучитьИдентификатор}, обратно —
   * {@code ДанныеФормыКоллекция.НайтиПоИдентификатору}).
   *
   * @return имя типа; {@code null} — от вида данных тип не зависит: у динамического
   *   списка это значение ключевого поля его основной таблицы, и он проставляется
   *   на типе конкретной таблицы ({@link DynamicListTypesRegistrar}), а у списка
   *   значений и отбора описание говорит только «идентификатор строки».
   */
  @Nullable String rowIdTypeName() {
    return switch (this) {
      case GANTT_CHART -> "ИдентификаторЗначенияДиаграммыГанта";
      case TABULAR_SECTION, VALUE_TABLE, VALUE_TREE, RECORD_SET -> FormPlatformTypes.NUMBER_RU;
      case DYNAMIC_LIST, VALUE_LIST, FILTER -> null;
      default -> "ИдентификаторКомпоновкиДанных";
    };
  }

  /**
   * Платформенный тип строки у видов данных, где строка своя — с колонками, взятыми из
   * самих данных. Его отдают {@code ТекущиеДанные} и метод {@code ДанныеСтроки}.
   * <p>
   * Тип задаёт вид данных, а не то, есть ли у строки колонки. Правило называет
   * синтакс-помощник в описании {@code ТаблицаФормы.ДанныеСтроки}: для динамического
   * списка — {@code ДанныеФормыСтруктура}, для дерева значений —
   * {@code ДанныеФормыЭлементДерева}, для остальных (таблица значений, табличные
   * части и др.) — {@code ДанныеФормыЭлементКоллекции}. Описание расширения
   * динамического списка повторяет его и для {@code ТекущиеДанные}.
   * <p>
   * Слову «структура» из описаний прочих расширений верить нельзя: тип {@code Структура}
   * таблица формы не отдаёт нигде — «структура» там про устройство значения. Проверено
   * на платформе: {@code ТипЗнч(Элементы.ТабличнаяЧасть1.ТекущиеДанные)} даёт
   * {@code ДанныеФормыЭлементКоллекции}, у динамического списка —
   * {@code ДанныеФормыСтруктура}.
   *
   * @return имя типа; {@code null} — своей строки у вида нет (см.
   *   {@link #currentDataTypeName}).
   */
  @Nullable String rowTypeName() {
    return switch (this) {
      case DYNAMIC_LIST -> FormPlatformTypes.FORM_DATA_STRUCTURE_RU;
      case VALUE_TREE -> FormPlatformTypes.FORM_DATA_TREE_ITEM_RU;
      case TABULAR_SECTION, VALUE_TABLE, RECORD_SET -> FormPlatformTypes.FORM_DATA_COLLECTION_ITEM_RU;
      default -> null;
    };
  }

  /**
   * Тип, который отдают {@code ТекущиеДанные} и метод {@code ДанныеСтроки} у видов
   * данных, где своей строки нет: части компоновщика настроек, отбор и диаграмма
   * Ганта отдают {@code ДанныеФормыСтруктура} со свойствами-колонками. Проверено на
   * платформе у таблиц над настройками компоновки ({@code КомпоновщикНастроек.Настройки}
   * и её {@code Отбор}).
   *
   * @return имя типа; {@code null} — у вида данных строка своя (см. {@link #rowTypeName}),
   *   и тип берётся с неё: там видны колонки.
   */
  @Nullable String currentDataTypeName() {
    return rowTypeName() == null ? FormPlatformTypes.FORM_DATA_STRUCTURE_RU : null;
  }

  /**
   * Вид данных по точному имени типа — без догадок про вложенность пути. Так
   * опознаются части компоновщика настроек, до которых доходит проход
   * {@code ПутьКДанным} через реестр.
   *
   * @param typeRu ru-имя типа данных, на которые смотрит таблица.
   * @return вид данных; {@code null}, если тип не опознан.
   */
  static @Nullable TableDataKind byTypeName(String typeRu) {
    for (var kind : values()) {
      if (kind != TABULAR_SECTION && kind != RECORD_SET && kind.suffix.equalsIgnoreCase(typeRu)) {
        return kind;
      }
    }
    return null;
  }

  static @Nullable TableDataKind of(String attributeTypeRu, boolean nested) {
    var byType = byTypeName(attributeTypeRu);
    if (byType != null) {
      return byType;
    }
    if (nested) {
      // Таблица над частью реквизита — это табличная часть объекта
      // (`Объект.Товары`): собственного типа у неё нет, опознаём по вложенности пути.
      return TABULAR_SECTION;
    }
    // Набор записей опознаётся по семейству: имя типа несёт вид регистра
    // (`РегистрСведенийНаборЗаписей.Х`), а суффикс вида у всех регистров общий.
    return FormPlatformTypes.formDataKindOf(attributeTypeRu) == FormDataKind.STRUCTURE_WITH_COLLECTION
      ? RECORD_SET
      : null;
  }
}
