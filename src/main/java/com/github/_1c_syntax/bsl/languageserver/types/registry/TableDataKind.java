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
   * @return имя типа; {@code null} — тип неизвестен: у динамического списка это
   *   значение ключевого поля (нужен источник данных, mdclasses#671), у списка значений
   *   и отбора описание говорит только «идентификатор строки».
   */
  @Nullable String rowIdTypeName() {
    return switch (this) {
      case GANTT_CHART -> "ИдентификаторЗначенияДиаграммыГанта";
      case TABULAR_SECTION, VALUE_TABLE, VALUE_TREE -> FormPlatformTypes.NUMBER_RU;
      case DYNAMIC_LIST, VALUE_LIST, FILTER -> null;
      default -> "ИдентификаторКомпоновкиДанных";
    };
  }

  /**
   * Тип, который отдают {@code ТекущиеДанные} и метод {@code ДанныеСтроки} у видов
   * данных, где своей строки нет: части компоновщика настроек, отбор и диаграмма
   * Ганта отдают {@code ДанныеФормыСтруктура} со свойствами-колонками.
   * <p>
   * Слову «структура» из описания расширения верить нельзя: тип {@code Структура}
   * таблица формы не отдаёт нигде. Проверено на платформе:
   * {@code ТипЗнч(Элементы.ТабличнаяЧасть1.ТекущиеДанные)} даёт
   * {@code ДанныеФормыЭлементКоллекции}, а у таблиц над настройками компоновки
   * ({@code КомпоновщикНастроек.Настройки} и её {@code Отбор}) —
   * {@code ДанныеФормыСтруктура}. Оно и логично: таблица формы работает с данными
   * формы, а не с обычными коллекциями.
   *
   * @return имя типа; {@code null} — у вида данных строка своя (зеркало табличной
   *   части, таблицы или дерева значений, строка динамического списка), и тип
   *   берётся оттуда: там видны колонки.
   */
  @Nullable String currentDataTypeName() {
    return switch (this) {
      case DYNAMIC_LIST, TABULAR_SECTION, VALUE_TABLE, VALUE_TREE -> null;
      default -> FormPlatformTypes.FORM_DATA_STRUCTURE_RU;
    };
  }

  /**
   * Вид данных по типу реквизита, на который смотрит таблица.
   *
   * @param attributeTypeRu ru-имя типа реквизита в корне {@code ПутьКДанным} таблицы;
   *                        пусто, если реквизит не найден.
   * @param nested          {@code true}, если {@code ПутьКДанным} уходит вглубь
   *                        реквизита ({@code Объект.ТабличнаяЧасть1}).
   * @return вид данных; {@code null}, если тип не опознан и расширения не будет.
   */
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
      if (kind != TABULAR_SECTION && kind.suffix.equalsIgnoreCase(typeRu)) {
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
    // Таблица над частью реквизита — это табличная часть объекта
    // (`Объект.Товары`): собственного типа у неё нет, опознаём по вложенности пути.
    return nested ? TABULAR_SECTION : null;
  }
}
