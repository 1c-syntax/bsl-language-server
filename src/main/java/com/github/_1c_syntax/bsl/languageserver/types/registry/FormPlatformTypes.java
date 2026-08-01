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

import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.mdo.Form;
import com.github._1c_syntax.bsl.mdo.children.ObjectForm;
import com.github._1c_syntax.bsl.mdo.storage.form.FormElementType;
import com.github._1c_syntax.bsl.mdo.storage.form.FormAttribute;
import com.github._1c_syntax.bsl.mdo.support.DefaultFormKind;
import com.github._1c_syntax.bsl.mdo.support.FormType;
import com.github._1c_syntax.bsl.types.MDOType;
import com.github._1c_syntax.bsl.types.MultiLanguageString;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Соответствие «сущность формы из mdclasses → платформенный тип синтакс-помощника».
 * Чистый словарь без зависимостей от реестра: имена типов резолвятся вызывающей
 * стороной, и ненайденные (другая версия платформы, отсутствующий HBK) просто
 * не дают специализации.
 *
 * <h2>Что сопоставляется, а что нет</h2>
 * Из 189 типов, чьё имя начинается на {@code Расширение}, карта знает 95 — все, что
 * относятся к <b>форме и её элементам</b>. Остальные не сопоставлены не по недосмотру:
 * <ul>
 *   <li>{@code Расширение табличного поля …}, {@code Расширение колонки табличного поля …},
 *       {@code Расширение поля ввода <тип данных>} и прочие расширения контролов
 *       обычных форм ({@code ПолеВвода}, {@code ТабличноеПоле}, {@code ПолеСписка}) —
 *       это отдельная иерархия элементов обычной формы, здесь не моделируемая;</li>
 *   <li>{@code Расширение тестируемого …} — ветка автоматизированного тестирования
 *       интерфейса, в модуле формы недоступна, и все они пусты;</li>
 *   <li>{@code РасширениеКонфигурации} — не расширение формы вовсе, в выборку попадает
 *       только по имени.</li>
 * </ul>
 * Имена типов данных для таблиц брать по имени расширения нельзя (у {@code Выбор} тип
 * называется {@code ВыбранныеПоляКомпоновкиДанных}), зато они названы в описании самого
 * расширения — оттуда же вычитаны правила для {@code ТекущаяСтрока} и {@code ТекущиеДанные},
 * см. {@link TableDataKind#rowIdTypeName} и {@link TableDataKind#currentDataTypeName}.
 * Сверка машинная, актуальна на 2026-07-31 — как её пересобрать, написано в
 * {@code build/form-extensions-audit.md}.
 *
 * <h2>Четыре оси соответствия</h2>
 * <ol>
 *   <li><b>Вид формы</b> ({@link FormType}) → базовый тип формы и тип коллекции
 *       её элементов: управляемая — {@code ФормаКлиентскогоПриложения}/{@code ВсеЭлементыФормы},
 *       обычная — {@code Форма}/{@code ЭлементыФормы} (см. {@link FormKind}).</li>
 *   <li><b>Вид элемента формы</b> ({@link FormElementType}, он же тег в {@code Form.xml}) →
 *       рантайм-тип элемента: {@code ПолеВвода} → {@code ПолеФормы}, {@code Таблица} →
 *       {@code ТаблицаФормы} и т.д. (см. {@link #itemTypeName}). Вид элемента — это
 *       <i>дизайнерская</i> категория, рантайм-типов у неё сильно меньше.</li>
 *   <li><b>Тип основного реквизита формы</b> → тип-расширение формы, дающий события и
 *       свойства работы с данными ({@code ПриЗаписиНаСервере}, {@code ПриЧтенииНаСервере},
 *       {@code АвтоВремя} …). Управляемая форма документа расширяется
 *       {@code Расширение формы клиентского приложения для документа}, обычная —
 *       {@code Расширение формы документа}; у форм списков обычных форм основной реквизит
 *       семейства {@code <Объект>Список} (см. {@link #extensionTypeNames}).</li>
 *   <li><b>Вид элемента</b> → тип-расширение <i>элемента</i>, где живёт вся его специфика
 *       ({@code ЦветФона} у обычной группы, 77 свойств у поля ввода) — см.
 *       {@link #itemExtensionTypeName}. Особняком таблица: её расширение выбирается не по
 *       виду, а по типу отображаемых данных (см. {@link TableDataKind}).</li>
 * </ol>
 */
final class FormPlatformTypes {

  private static final String FORM_FIELD = "ПолеФормы";
  private static final String FORM_GROUP = "ГруппаФормы";
  private static final String FORM_BUTTON = "КнопкаФормы";
  private static final String FORM_DECORATION = "ДекорацияФормы";
  private static final String FORM_TABLE = "ТаблицаФормы";

  /** Общая часть имени расширений таблицы формы — их два десятка. */
  static final String TABLE_EXTENSION_PREFIX = "Расширение таблицы формы для ";
  private static final String FORM_ITEM_ADDITION = "ДополнениеЭлементаФормы";

  private static final String BOOLEAN_RU = "Булево";

  /**
   * Поведение элемента формы при недоступности основного сервера: свойство и его тип
   * названы одинаково — обычное для 1С соглашение (см. {@link #TYPELESS_MEMBER_TYPES}).
   */
  private static final String SERVER_UNAVAILABLE_BEHAVIOR = "ПоведениеПриНедоступностиОсновногоСервера";

  /** Свойство таблицы формы, отдающее текущую строку данных. */
  static final String CURRENT_DATA_RU = "ТекущиеДанные";
  static final String CURRENT_DATA_EN = "CurrentData";

  /**
   * Общая часть имени расширений управляемой формы. В bsl-context 0.9.x имена
   * расширений приведены к единой схеме {@code Расширение формы клиентского приложения
   * для <чего>}; до этого они назывались коротко ({@code Расширение документа}).
   */
  private static final String MANAGED_EXTENSION_PREFIX = "Расширение формы клиентского приложения для ";
  private static final String OBJECTS = MANAGED_EXTENSION_PREFIX + "объектов";
  private static final String RECORD_SET = MANAGED_EXTENSION_PREFIX + "набора записей";

  /**
   * Тип основного реквизита формы (семейство, т.е. часть имени до точки) →
   * тип-расширение формы. Ключ — ru-написание в нижнем регистре.
   * <p>
   * Источник — обход HBK 8.3.27: у управляемых форм расширения названы по
   * данным ({@code Расширение документа}), у обычных — по форме
   * ({@code Расширение формы документа}). У справочника расширение своё
   * ({@code Расширение справочника}) — оно несёт те же события, что общее
   * {@code Расширение объектов}, но добавляет {@code ЭтоГруппа} и типизирует
   * параметр {@code Ключ}; общее остаётся для планов счетов, видов расчёта и обмена.
   * <p>
   * Списочные формы ({@code ДинамическийСписок}) членов уровня формы не получают —
   * их специфика живёт на элементе-таблице; параметры формы у них при этом есть
   * (см. {@link #parameterExtensionTypeName}).
   */
  private static final Map<String, Extension> EXTENSION_BY_ATTRIBUTE_FAMILY = Map.ofEntries(
    Map.entry("справочникобъект",
      Extension.object(managed("справочника"))),
    Map.entry("документобъект",
      Extension.object(managed("документа"))),
    Map.entry("планвидовхарактеристикобъект",
      Extension.object(managed("плана видов характеристик"))),
    Map.entry("плансчетовобъект", Extension.of(OBJECTS)),
    Map.entry("планвидоврасчетаобъект", Extension.of(OBJECTS)),
    Map.entry("планобменаобъект", Extension.of(OBJECTS)),
    Map.entry("бизнеспроцессобъект",
      Extension.object(managed("бизнес-процесса"))),
    Map.entry("задачаобъект", Extension.object(managed("задачи"))),
    // Отчёт, обработка, константы, наборы записей: у них свой набор событий, ничего
    // общего с объектным, поэтому запасного варианта нет.
    Map.entry("отчетобъект", Extension.of(managed("отчета"))),
    Map.entry("внешнийотчетобъект", Extension.of(managed("отчета"))),
    Map.entry("обработкаобъект", Extension.of(managed("обработки"))),
    Map.entry("внешняяобработкаобъект",
      Extension.of(managed("обработки"))),
    Map.entry("константаменеджерзначения",
      Extension.of(managed("констант"))),
    Map.entry("константынабор", Extension.of(managed("констант"))),
    Map.entry("регистрсведенийзапись",
      Extension.of(managed("записи регистра сведений"))),
    Map.entry("регистрсведенийнаборзаписей",
      Extension.of(RECORD_SET)),
    Map.entry("регистрнакоплениянаборзаписей",
      Extension.of(RECORD_SET)),
    Map.entry("регистрбухгалтериинаборзаписей",
      Extension.of(RECORD_SET)),
    Map.entry("регистррасчетанаборзаписей",
      Extension.of(RECORD_SET)),
    Map.entry("перерасчетнаборзаписей", Extension.of(RECORD_SET)),
    Map.entry("последовательностьнаборзаписей", Extension.of(RECORD_SET)),
    Map.entry("внешнийисточникданныхтаблицаобъект",
      Extension.of(managed("объекта таблицы внешнего источника данных"))),
    Map.entry("внешнийисточникданныхтаблицазапись",
      Extension.of(managed("записи таблицы внешнего источника данных"))),
    // Форма настроек компоновки данных: основной реквизит — сам компоновщик.
    Map.entry("компоновщикнастроеккомпоновкиданных",
      Extension.of(managed("компоновщика настроек")))
  );

  /**
   * Параметры обычной формы, тип которых задаёт <b>не</b> объект-владелец формы, а
   * другой: владелец подчинённого справочника, регистратор подчинённого регистра.
   * Плейсхолдер в их типе выглядит так же, как у остальных параметров
   * ({@code СправочникСсылка.<Имя справочника>}), поэтому подстановка имени владельца
   * формы дала бы правдоподобный, но неверный тип.
   */
  private static final Set<String> PARAMETERS_OF_ANOTHER_OBJECT = Set.of(
    "ПараметрВыборПоВладельцу", "ПараметрОтборПоВладельцу");

  /** Параметр формы списка записей регистра, отбирающий записи по регистратору. */
  private static final String RECORDER_FILTER_PARAMETER = "ПараметрОтборПоРегистратору";

  /** Параметр формы списка: строка, на которую список встаёт при открытии. */
  private static final String CURRENT_ROW_PARAMETER = "ПараметрТекущаяСтрока";

  /**
   * Параметр-основание: у управляемой формы он лежит в структуре параметров
   * ({@code Параметры.Основание}), у обычной — свойством расширения
   * ({@code ПараметрОснование}). Тип у обоих не объявлен: платформе он не известен,
   * пока не посмотреть в метаданные владельца.
   */
  private static final Set<String> BASIS_PARAMETERS = Set.of("Основание", "ПараметрОснование");

  /** Тип реквизита списочной формы. */
  private static final String DYNAMIC_LIST = "ДинамическийСписок";

  /** Тип структуры, стоящей за свойством {@code Форма.Параметры}. */
  static final String FORM_DATA_STRUCTURE_RU = "ДанныеФормыСтруктура";
  static final String FORM_DATA_STRUCTURE_EN = "FormDataStructure";

  /** Тип, в который на форме превращается табличная часть объекта и таблица значений. */
  static final String FORM_DATA_COLLECTION_RU = "ДанныеФормыКоллекция";
  static final String FORM_DATA_COLLECTION_EN = "FormDataCollection";

  /** Идентификатор строки данных формы (см. {@link TableDataKind#rowIdTypeName}). */
  static final String NUMBER_RU = "Число";

  /** Строка коллекции данных формы. */
  static final String FORM_DATA_COLLECTION_ITEM_RU = "ДанныеФормыЭлементКоллекции";
  static final String FORM_DATA_COLLECTION_ITEM_EN = "FormDataCollectionItem";

  /**
   * Семейства типов реквизита, которые на форме становятся {@code ДанныеФормыСтруктура}.
   * Это «объектные» типы в широком смысле: сам объект, менеджер значения константы,
   * менеджер записи регистра сведений и запись таблицы внешнего источника данных.
   */
  private static final Set<String> FORM_DATA_STRUCTURE_FAMILIES = Set.of(
    "справочникобъект", "документобъект", "задачаобъект", "бизнеспроцессобъект",
    "планвидовхарактеристикобъект", "плансчетовобъект", "планвидоврасчетаобъект",
    "планобменаобъект", "отчетобъект", "обработкаобъект", "внешнийотчетобъект",
    "внешняяобработкаобъект", "внешнийисточникданныхтаблицаобъект",
    "константаменеджерзначения", "константынабор",
    "регистрсведенийменеджерзаписи", "регистрсведенийзапись",
    "внешнийисточникданныхтаблицазапись");

  /** Суффикс семейства наборов записей — общий у всех видов регистров. */
  private static final String RECORD_SET_FAMILY_SUFFIX = "наборзаписей";
  private static final String VALUE_TABLE = "таблицазначений";
  private static final String VALUE_TREE = "деревозначений";

  /**
   * Свойства прикладного объекта, которые в данные формы не переносятся: инфраструктура
   * объекта, живущая только на сервере. Данные формы несут стандартные реквизиты,
   * реквизиты (в том числе общие) и табличные части — и больше ничего.
   * <p>
   * Список именно перечислением: отличить эти свойства от переносимых по типу или по
   * признаку «член платформы» нельзя. Стандартные реквизиты ({@code Ссылка}, {@code Код},
   * {@code Наименование}) тоже приходят от платформы, а реквизит конфигурации бывает
   * платформенного типа ({@code ХранилищеЗначения}) — оба признака дают и ложные
   * срабатывания, и пропуски. Источник — обход членов объектных типов в HBK 8.3.27.
   * <p>
   * Имена достаточно ru: сравнение идёт двуязычным {@code MemberDescriptor#matches}.
   */
  private static final Set<String> PROPERTIES_OUTSIDE_FORM_DATA = Set.of(
    // Общие для объектов и наборов записей.
    "ЭтотОбъект", "ДополнительныеСвойства", "ОбменДанными", "ЗаписьИсторииДанных",
    // Документ.
    "Движения", "ПринадлежностьПоследовательностям",
    // Набор записей.
    "Отбор", "Записывать", "РасширенныеРежимыЗамещения");

  /**
   * Расширения обычной формы по объекту-владельцу: у списочной роли формы одно,
   * у формы одного объекта — другое (см. {@link #ordinaryExtensionTypeName}).
   * У журнала документов, критерия отбора, отчёта, обработки и константы форма ровно
   * одна, и обе роли ведут на одно расширение: в mdclasses она приходит не формой
   * списка, а основной (designer-свойство {@code <DefaultForm>}).
   *
   * @param listForm расширение формы списка; {@code null} — у вида её не бывает
   * @param itemForm расширение формы одного объекта; {@code null} — её не бывает
   */
  private record OrdinaryExtension(@Nullable String listForm, @Nullable String itemForm) {

    /** Вид, у которого форма одна на все роли. */
    static OrdinaryExtension of(String single) {
      return new OrdinaryExtension(single, single);
    }
  }

  private static final Map<MDOType, OrdinaryExtension> ORDINARY_EXTENSION_BY_OWNER = Map.ofEntries(
    Map.entry(MDOType.CATALOG,
      new OrdinaryExtension("Расширение формы списка справочника", "Расширение формы элемента справочника")),
    Map.entry(MDOType.DOCUMENT,
      new OrdinaryExtension("Расширение формы списка документов", "Расширение формы документа")),
    Map.entry(MDOType.DOCUMENT_JOURNAL, OrdinaryExtension.of("Расширение формы журнала документов")),
    Map.entry(MDOType.TASK,
      new OrdinaryExtension("Расширение формы списка задач", "Расширение формы задачи")),
    Map.entry(MDOType.BUSINESS_PROCESS,
      new OrdinaryExtension("Расширение формы списка бизнес-процессов",
        "Расширение формы объекта бизнес-процесс")),
    Map.entry(MDOType.CHART_OF_CHARACTERISTIC_TYPES,
      new OrdinaryExtension("Расширение формы списка видов характеристик",
        "Расширение формы элемента вида характеристик")),
    Map.entry(MDOType.CHART_OF_ACCOUNTS,
      new OrdinaryExtension("Расширение формы списка плана счетов",
        "Расширение формы элемента плана счетов")),
    Map.entry(MDOType.CHART_OF_CALCULATION_TYPES,
      new OrdinaryExtension("Расширение формы списка видов расчета", "Расширение формы вида расчета")),
    Map.entry(MDOType.EXCHANGE_PLAN,
      new OrdinaryExtension("Расширение формы списка узлов", "Расширение формы узла")),
    Map.entry(MDOType.ENUM, new OrdinaryExtension("Расширение формы списка перечисления", null)),
    Map.entry(MDOType.FILTER_CRITERION, OrdinaryExtension.of("Расширение формы критерия отбора")),
    Map.entry(MDOType.REPORT, OrdinaryExtension.of("Расширение формы отчета")),
    Map.entry(MDOType.DATA_PROCESSOR, OrdinaryExtension.of("Расширение формы обработки")),
    Map.entry(MDOType.CONSTANT, OrdinaryExtension.of("Расширение формы констант")),
    Map.entry(MDOType.INFORMATION_REGISTER,
      new OrdinaryExtension("Расширение формы списка записей регистра сведений",
        "Расширение формы записи регистра сведений")),
    Map.entry(MDOType.ACCUMULATION_REGISTER,
      new OrdinaryExtension("Расширение формы списка записей регистра накопления", null)),
    Map.entry(MDOType.ACCOUNTING_REGISTER,
      new OrdinaryExtension("Расширение формы списка записей регистра бухгалтерии", null)),
    Map.entry(MDOType.CALCULATION_REGISTER,
      new OrdinaryExtension("Расширение формы списка записей регистра расчета", null)));

  /** Имя свойства формы, отдающего структуру параметров. */
  static final String PARAMETERS_PROPERTY_RU = "Параметры";
  static final String PARAMETERS_PROPERTY_EN = "Parameters";

  /** Коллекция команд управляемой формы: у обычной команд нет вовсе. */
  static final String FORM_COMMANDS_RU = "КомандыФормы";
  static final String FORM_COMMANDS_EN = "FormCommands";

  /** Одна команда формы — то, что лежит в коллекции. */
  static final String FORM_COMMAND_RU = "КомандаФормы";
  static final String FORM_COMMAND_EN = "FormCommand";

  /** Имя свойства формы, отдающего коллекцию команд. */
  static final String COMMANDS_PROPERTY_RU = "Команды";
  static final String COMMANDS_PROPERTY_EN = "Commands";

  /**
   * Свойства элементов формы, у которых платформа не объявила тип, вместе с типом,
   * который она же и называет — в описании свойства либо в объявлении одноимённого
   * свойства другого типа.
   * <p>
   * В синтакс-помощнике блок «Тип» есть не у всякого свойства: по всей платформе таких
   * свойств под две сотни, и элементы формы — лишь их часть. Без типа обрывается вся
   * цепочка: {@code Элементы.Таблица.АвтоОтметкаНезаполненного} не показывает даже того,
   * что это {@code Булево}. Восемь из перечисленных ниже свойств типизируются одноимённым
   * перечислением платформы ({@code СпециальныйРежимВводаТекста}) — это соглашение 1С об
   * именовании, — а {@code АвтоОтметкаНезаполненного} у таблицы получает ровно тот тип,
   * который платформа объявила у одноимённого свойства поля ввода. Обоснование каждой
   * записи — в описании соответствующего свойства синтакс-помощника.
   * <p>
   * Ключ — ru-имя типа-владельца (сам элемент либо его расширение по виду), значение —
   * {@code имя свойства → ru-имя типа значения}. Обе стороны проверяются по реестру:
   * не нашлось — правило молча не применяется.
   */
  static final Map<String, Map<String, String>> TYPELESS_MEMBER_TYPES = Map.of(
    FORM_FIELD, Map.of(
      SERVER_UNAVAILABLE_BEHAVIOR, SERVER_UNAVAILABLE_BEHAVIOR),
    FORM_BUTTON, Map.of(
      SERVER_UNAVAILABLE_BEHAVIOR, SERVER_UNAVAILABLE_BEHAVIOR,
      "Пометка", BOOLEAN_RU),
    FORM_DECORATION, Map.of(
      SERVER_UNAVAILABLE_BEHAVIOR, SERVER_UNAVAILABLE_BEHAVIOR),
    FORM_TABLE, Map.of(
      SERVER_UNAVAILABLE_BEHAVIOR, SERVER_UNAVAILABLE_BEHAVIOR,
      "АвтоОтметкаНезаполненного", BOOLEAN_RU),
    "Расширение поля формы для поля ввода", Map.of(
      "ПроверкаПравописанияПриВводеТекста", "ПроверкаПравописанияПриВводеТекста",
      "СпециальныйРежимВводаТекста", "СпециальныйРежимВводаТекста",
      "ТекстКнопкиВводаЭкраннойКлавиатуры", "ТекстКнопкиВводаЭкраннойКлавиатуры"),
    "Расширение поля формы для поля PDF-документа", Map.of(
      "ПоложениеСостоянияПросмотра", "ПоложениеСостоянияПросмотра"),
    "Расширение поля формы для поля картинки", Map.of(
      "НомерТекущегоКадра", NUMBER_RU),
    TABLE_EXTENSION_PREFIX + "динамического списка", Map.of(
      "ВосстанавливатьТекущуюСтроку", BOOLEAN_RU));

  private FormPlatformTypes() {
  }

  /**
   * Свойство платформенного вида: имя и описание двуязычны, сам член помечен
   * библиотечным — так его показывают hover и автодополнение.
   *
   * @param name        двуязычное имя свойства.
   * @param type        тип значения.
   * @param description двуязычное описание; пустое допустимо.
   * @return дескриптор свойства.
   */
  static MemberDescriptor platformProperty(BilingualString name, TypeRef type,
                                           BilingualString description) {
    return MemberDescriptor.property(name.primary(), type)
      .withBilingualName(name)
      .withBilingualDescription(description)
      .withStandardLibrary(true);
  }

  /**
   * Имя, одинаковое в обеих локалях: имена реквизитов, элементов и обработчиков
   * задаёт конфигурация, перевода у них нет — но в автодополнении они должны
   * появляться независимо от языка интерфейса.
   *
   * @param name имя из метаданных.
   * @return двуязычное имя с одинаковыми написаниями.
   */
  static BilingualString neutral(String name) {
    return BilingualString.of(name, name);
  }

  /**
   * Многоязычная строка метаданных как двуязычная строка модели.
   *
   * @param value значение из метаданных (синоним, заголовок).
   * @return двуязычная строка; пустая, если значения нет.
   */
  static BilingualString bilingual(MultiLanguageString value) {
    if (value.isEmpty()) {
      return BilingualString.EMPTY;
    }
    return BilingualString.of(value.get("ru"), value.get("en"));
  }

  /**
   * Рантайм-тип элемента формы по его виду в {@code Form.xml}. {@code null} —
   * вид не опознан ({@link FormElementType#UNKNOWN}), тип элемента остаётся
   * невыведенным.
   * <p>
   * Все разновидности полей ({@code ПолеВвода}, {@code ПолеФлажка}, {@code ПолеКалендаря} …)
   * на рантайме — один {@code ПолеФормы}: вид поля хранится в его свойстве
   * {@code Вид}, а не в типе. Свойства, специфичные для вида, живут не здесь, а в
   * типе-расширении элемента — см. {@link #itemExtensionTypeName}.
   */
  static @Nullable String itemTypeName(FormElementType elementType) {
    return switch (elementType) {
      case SEARCH_STRING_ADDITION, SEARCH_CONTROL_ADDITION, VIEW_STATUS_ADDITION ->
        FORM_ITEM_ADDITION;
      case BUTTON_GROUP, COLUMN_GROUP, COMMAND_BAR, PAGE, PAGES, POPUP, USUAL_GROUP -> FORM_GROUP;
      case COMMAND_BAR_BUTTON, COMMAND_BAR_HYPERLINK, HYPERLINK, USUAL_BUTTON -> FORM_BUTTON;
      case LABEL_DECORATION, PICTURE_DECORATION -> FORM_DECORATION;
      case TABLE -> FORM_TABLE;
      case UNKNOWN -> null;
      default -> FORM_FIELD;
    };
  }

  /**
   * Тип-расширение элемента формы по его виду. Рантайм-тип элемента ({@code ГруппаФормы},
   * {@code ПолеФормы}) несёт только общее для всех элементов своей категории, а
   * специфика вида вынесена в отдельный тип: {@code ЦветФона} есть у
   * {@code Расширение группы формы для обычной группы}, но не у {@code ГруппаФормы}.
   * Расширения заметно богаче базы — у поля ввода 77 свойств против двух десятков.
   * <p>
   * Имена приведены к регулярной схеме в bsl-context 0.9.x
   * ({@code Расширение <категория> формы для <вид>}); до этого встречались омонимы —
   * «Расширение надписи» было и у поля, и у декорации.
   * <p>
   * У кнопок расширения по виду нет: всё описано в самой {@code КнопкаФормы}.
   * У таблицы оно есть, но зависит не от вида элемента, а от типа отображаемых
   * данных (динамический список / табличная часть / таблица значений), поэтому
   * здесь не резолвится.
   *
   * @param elementType вид элемента из {@code Form.xml}.
   * @return qualifiedName типа-расширения; {@code null}, если у вида его нет.
   */
  static @Nullable String itemExtensionTypeName(FormElementType elementType) {
    return ITEM_EXTENSION_BY_KIND.get(elementType);
  }

  /**
   * Тип-расширение по виду элемента формы — картой, а не разбором по видам: имя
   * расширения ни от чего, кроме самого вида, не зависит (см.
   * {@link #itemExtensionTypeName}). Виды, которых здесь нет, расширения не имеют.
   */
  private static final Map<FormElementType, String> ITEM_EXTENSION_BY_KIND = Map.ofEntries(
    // Группы.
    Map.entry(FormElementType.USUAL_GROUP, "Расширение группы формы для обычной группы"),
    Map.entry(FormElementType.PAGE, "Расширение группы формы для страницы"),
    Map.entry(FormElementType.PAGES, "Расширение группы формы для страниц"),
    Map.entry(FormElementType.POPUP, "Расширение группы формы для подменю"),
    Map.entry(FormElementType.COMMAND_BAR, "Расширение группы формы для командной панели"),
    Map.entry(FormElementType.BUTTON_GROUP, "Расширение группы формы для группы кнопок"),
    Map.entry(FormElementType.COLUMN_GROUP, "Расширение группы формы для группы колонок"),
    // Поля.
    Map.entry(FormElementType.INPUT_FIELD, "Расширение поля формы для поля ввода"),
    Map.entry(FormElementType.LABEL_FIELD, "Расширение поля формы для поля надписи"),
    Map.entry(FormElementType.CHECK_BOX_FIELD, "Расширение поля формы для поля флажка"),
    Map.entry(FormElementType.RADIO_BUTTON_FIELD, "Расширение поля формы для поля переключателя"),
    Map.entry(FormElementType.PICTURE_FIELD, "Расширение поля формы для поля картинки"),
    Map.entry(FormElementType.CALENDAR_FIELD, "Расширение поля формы для поля календаря"),
    Map.entry(FormElementType.PERIOD_FIELD, "Расширение поля формы для поля периода"),
    Map.entry(FormElementType.PROGRESS_BAR_FIELD, "Расширение поля формы для поля индикатора"),
    Map.entry(FormElementType.TRACK_BAR_FIELD, "Расширение поля формы для поля полосы регулирования"),
    Map.entry(FormElementType.TEXT_DOCUMENT_FIELD, "Расширение поля формы для поля текстового документа"),
    Map.entry(FormElementType.SPREAD_SHEET_DOCUMENT_FIELD,
      "Расширение поля формы для поля табличного документа"),
    Map.entry(FormElementType.HTML_DOCUMENT_FIELD, "Расширение поля формы для поля HTML-документа"),
    Map.entry(FormElementType.FORMATTED_DOCUMENT_FIELD,
      "Расширение поля формы для поля форматированного документа"),
    Map.entry(FormElementType.PDF_DOCUMENT_FIELD, "Расширение поля формы для поля PDF-документа"),
    Map.entry(FormElementType.GRAPHICAL_SCHEMA_FIELD, "Расширение поля формы для поля графической схемы"),
    Map.entry(FormElementType.GEOGRAPHICAL_SCHEMA_FIELD,
      "Расширение поля формы для поля географической схемы"),
    Map.entry(FormElementType.CHART_FIELD, "Расширение поля формы для поля диаграммы"),
    Map.entry(FormElementType.GANTT_CHART_FIELD, "Расширение поля формы для поля диаграммы Ганта"),
    Map.entry(FormElementType.PLANNER_FIELD, "Расширение поля формы для поля планировщика"),
    Map.entry(FormElementType.DENDROGRAM_FIELD, "Расширение поля формы для поля дендрограммы"),
    // Декорации.
    Map.entry(FormElementType.LABEL_DECORATION, "Расширение декорации формы для надписи"),
    Map.entry(FormElementType.PICTURE_DECORATION, "Расширение декорации формы для картинки"),
    // Дополнения элемента.
    Map.entry(FormElementType.SEARCH_STRING_ADDITION,
      "Расширение дополнения элемента формы для отображения строки поиска"),
    Map.entry(FormElementType.VIEW_STATUS_ADDITION,
      "Расширение дополнения элемента формы для отображения состояния просмотра"),
    Map.entry(FormElementType.SEARCH_CONTROL_ADDITION,
      "Расширение дополнения элемента формы для отображения управления поиском"));


  /**
   * Суффикс синтетического типа элемента — имя его вида ({@code ОбычнаяГруппа},
   * {@code ПолеВвода}). Уникален в пределах базового типа, поэтому годится как ключ
   * специализации «база + расширение вида».
   */
  static String itemKindSuffix(FormElementType elementType) {
    return elementType.fullName().getRu();
  }

  

  /** Базовый рантайм-тип таблицы формы — общий для всех видов её данных. */
  static String tableTypeName() {
    return FORM_TABLE;
  }

  /**
   * Расширение <b>строки данных</b> таблицы формы для этого вида данных.
   * <p>
   * У большинства видов строка ничем не выделяется, и расширения у неё нет. Исключение —
   * динамический список: платформа объявляет «Расширение данных строки для динамического
   * списка» (дополнительные свойства строки, когда в списке задано условное оформление).
   * Динамический список бывает только на управляемой форме, поэтому к иерархии контролов
   * обычных форм это расширение отношения не имеет.
   *
   * @param dataKind вид данных таблицы.
   * @return qualifiedName расширения строки; {@code null}, если его нет.
   */
  static @Nullable String rowDataExtensionName(TableDataKind dataKind) {
    return dataKind == TableDataKind.DYNAMIC_LIST
      ? "Расширение данных строки для динамического списка"
      : null;
  }

  /**
   * Имена типов-расширений формы в порядке приоритета.
   * <p>
   * Только для управляемых форм: у обычных состав реквизитов из mdclasses не
   * приходит (он лежит в {@code Ext/Form.bin} недокументированным двоичным
   * форматом), поэтому основной реквизит у них не определить и расширение не
   * выбрать. Сопоставления обычных форм сохранены в ветке
   * {@code spike/ordinary-forms-extensions} — вернуть, когда их состав появится
   * в модели метаданных.
   *
   * @param names имена расширений; пусто — расширения нет
   */
  private record Extension(List<String> names) {

    /** Расширение без запасного варианта. */
    static Extension of(String managed) {
      return new Extension(List.of(managed));
    }

    /**
     * Объектная форма: своё расширение и общее {@code Расширение объектов} как
     * запасной вариант. Набор событий у них один, различаются свойствами и
     * типизацией параметров, поэтому подмена общим — корректная деградация.
     */
    static Extension object(String managed) {
      return new Extension(List.of(managed, OBJECTS));
    }
  }

  private static String managed(String what) {
    return MANAGED_EXTENSION_PREFIX + what;
  }

  /**
   * Имена типов-расширений формы по ru-имени типа её основного реквизита. Кандидатов
   * бывает несколько — у объектных форм вторым идёт общее «Расширение объектов»,
   * — и порядок значим: он и задаёт приоритет.
   *
   * @param attributeTypeRu ru-имя типа реквизита ({@code "ДокументОбъект.Документ1"},
   *                        {@code "КонстантыНабор"}).
   * @param formKind        вид формы — управляемая или обычная.
   * @return qualifiedName'ы типов-расширений в порядке приоритета; пустой список, если
   *   реквизит такого типа расширения формы не даёт либо форма обычная (у неё
   *   расширение выбирается иначе, см. {@link #ordinaryExtensionTypeName}).
   */
  static List<String> extensionTypeNames(String attributeTypeRu, FormKind formKind) {
    if (formKind == FormKind.ORDINARY) {
      // У обычной формы основной реквизит не определить — её состав mdclasses не
      // отдаёт. Расширение выбирается по другому признаку, см.
      // {@link #ordinaryExtensionTypeName}.
      return List.of();
    }
    var extension = EXTENSION_BY_ATTRIBUTE_FAMILY.get(familyOf(attributeTypeRu));
    return extension == null ? List.of() : extension.names();
  }

  /**
   * Тип-расширение <b>обычной</b> формы по объекту-владельцу и роли формы у него.
   * <p>
   * У управляемой формы расширение выбирается по типу основного реквизита, но у
   * обычной состав реквизитов из mdclasses не приходит (он лежит в {@code Ext/Form.bin}
   * недокументированным двоичным форматом). Зато известно, какой основной формой
   * объекта форма назначена, а это ровно тот же признак: форма, назначенная основной
   * формой элемента справочника, и есть форма элемента справочника.
   * <p>
   * Эвристика, а не точное знание: расширение получают только формы, назначенные
   * основными. Произвольная обычная форма, не назначенная ничем, остаётся без
   * расширения — это честнее, чем угадывать по имени.
   *
   * @param ownerType вид объекта-владельца формы.
   * @param formKind  роль формы у владельца.
   * @return qualifiedName расширения; {@code null}, если для такой пары расширения нет.
   */
  static @Nullable String ordinaryExtensionTypeName(MDOType ownerType, DefaultFormKind formKind) {
    var extension = ORDINARY_EXTENSION_BY_OWNER.get(ownerType);
    if (extension == null) {
      return null;
    }
    return isListForm(formKind) ? extension.listForm() : extension.itemForm();
  }

  /**
   * Параметры расширения, которых у этой формы не существует вовсе. Платформа
   * объявляет их одинаково для всего вида объектов, а есть они не у всех: отбор по
   * владельцу — только у подчинённого справочника, отбор по регистратору — только у
   * регистра, подчинённого регистратору, текущая строка журнала — только когда в него
   * зарегистрирован хоть один документ.
   * <p>
   * Такие члены нужно именно <b>убирать</b>: тип у них объявлен через плейсхолдер, и
   * оставленный «обобщённым» параметр и в автодополнении выглядит существующим, и
   * тянет за собой невалидное имя типа ({@code ДокументСсылка.<Имя документа>}).
   *
   * @param rowNames      имена для строки списка (у журнала — его документы).
   * @param ownerNames    имена владельцев объекта, которому подчинена форма.
   * @param recorderNames имена документов, пишущих движения в этот регистр.
   * @return имена параметров, которых у формы нет; подавлять их можно и на форме,
   *   где они и не объявлены — подавление сверяется по имени.
   */
  static List<String> absentParameters(List<String> rowNames, List<String> ownerNames,
                                       List<String> recorderNames) {
    var absent = new ArrayList<String>();
    if (rowNames.isEmpty()) {
      absent.add(CURRENT_ROW_PARAMETER);
    }
    if (ownerNames.isEmpty()) {
      absent.addAll(PARAMETERS_OF_ANOTHER_OBJECT);
    }
    if (recorderNames.isEmpty()) {
      absent.add(RECORDER_FILTER_PARAMETER);
    }
    return absent;
  }

  /**
   * Указывает ли параметр обычной формы на посторонний объект, а не на владельца формы.
   *
   * @param member член типа-расширения обычной формы.
   * @return {@code true}, если подставлять в его тип имя владельца формы нельзя.
   */
  static boolean parameterOfAnotherObject(MemberDescriptor member) {
    return PARAMETERS_OF_ANOTHER_OBJECT.stream().anyMatch(member::matches);
  }

  /**
   * Передаётся ли параметром объект-основание. Допустимые типы ограничены списком
   * {@code ВводитсяНаОсновании} владельца формы, а платформа объявляет параметр без типа.
   *
   * @param member параметр формы (член структуры параметров либо расширения обычной формы).
   * @return {@code true}, если это параметр-основание.
   */
  static boolean parameterOfBasis(MemberDescriptor member) {
    return BASIS_PARAMETERS.stream().anyMatch(member::matches);
  }

  /**
   * Отбирает ли параметр записи регистра по регистратору. Тип у него объявлен как
   * {@code ДокументСсылка.<Имя документа>}, и подставлять туда надо не владельца формы,
   * а документы, которые пишут движения в этот регистр.
   *
   * @param member член типа-расширения обычной формы.
   * @return {@code true}, если это отбор по регистратору.
   */
  static boolean parameterOfRecorder(MemberDescriptor member) {
    return member.matches(RECORDER_FILTER_PARAMETER);
  }

  /**
   * Суффиксы прикладного типа, контекст которого инжектится в модуль обычной формы,
   * в порядке проверки. Форма записи регистра работает с записью, форма набора — с
   * набором; какой суффикс применим к конкретному владельцу, решает наличие типа в
   * реестре, а не эта таблица.
   *
   * @param formKind роль формы у владельца.
   * @return суффиксы имени типа; пусто — контекст не инжектится (форма списка).
   */
  static List<String> injectedObjectSuffixes(DefaultFormKind formKind) {
    if (isListForm(formKind)) {
      return List.of();
    }
    return switch (formKind) {
      case RECORD_FORM, AUX_RECORD_FORM -> List.of("Запись", "НаборЗаписей");
      case OBJECT_FORM, FOLDER_FORM, AUX_OBJECT_FORM, AUX_FOLDER_FORM ->
        List.of("Объект", "НаборЗаписей", "МенеджерЗначения");
      default -> List.of();
    };
  }

  /**
   * Показывает ли форма список записей, а не одну. Формы выбора и выбора группы —
   * тоже списочные: платформа расширяет их так же, как форму списка. Форма группы
   * ({@code FOLDER_FORM}) — наоборот, форма одного элемента.
   */
  private static boolean isListForm(DefaultFormKind formKind) {
    return switch (formKind) {
      case LIST_FORM, CHOICE_FORM, FOLDER_CHOICE_FORM,
           AUX_LIST_FORM, AUX_CHOICE_FORM, AUX_FOLDER_CHOICE_FORM -> true;
      default -> false;
    };
  }

  /**
   * Тип, у которого берутся <b>параметры</b> формы. Совпадает с
   * {@link #extensionTypeNames} везде, кроме списочных форм: у динамического списка
   * параметры формы есть ({@code Отбор}, {@code ТекущаяСтрока}, {@code РежимВыбора} …),
   * а членов уровня формы он не даёт — специфика списка живёт на элементе-таблице,
   * и подмешивать её в тип формы нельзя.
   *
   * @param attributeTypeRu ru-имя типа основного реквизита.
   * @param formKind        вид формы.
   * @return qualifiedName'ы типов-источников параметров в порядке приоритета.
   */
  static List<String> parameterExtensionTypeNames(String attributeTypeRu, FormKind formKind) {
    if (DYNAMIC_LIST.equalsIgnoreCase(attributeTypeRu)) {
      return List.of(managed("динамического списка"));
    }
    return extensionTypeNames(attributeTypeRu, formKind);
  }

  /**
   * Семейство типа — часть qualifiedName до первой точки, в нижнем регистре.
   * Для неквалифицированных имён ({@code ТаблицаЗначений}) — само имя.
   */
  private static String familyOf(String typeNameRu) {
    var dot = typeNameRu.indexOf('.');
    return (dot < 0 ? typeNameRu : typeNameRu.substring(0, dot)).toLowerCase(Locale.ROOT);
  }


  /**
   * Переносится ли свойство прикладного типа в данные формы.
   *
   * @param property свойство прикладного типа.
   * @return {@code false}, если свойство остаётся у объекта и на форме недоступно.
   */
  static boolean isTransferredToFormData(MemberDescriptor property) {
    return PROPERTIES_OUTSIDE_FORM_DATA.stream().noneMatch(property::matches);
  }

  /**
   * Вид данных формы по объявленному типу реквизита.
   *
   * @param attributeTypeRu ru-имя типа реквизита из {@code Form.xml}
   *                        ({@code "ДокументОбъект.Документ1"}, {@code "ТаблицаЗначений"}).
   * @return вид данных формы; {@code null}, если тип переносится на форму как есть.
   */
  static @Nullable FormDataKind formDataKindOf(String attributeTypeRu) {
    var family = familyOf(attributeTypeRu);
    if (FORM_DATA_STRUCTURE_FAMILIES.contains(family)) {
      return FormDataKind.STRUCTURE;
    }
    if (family.endsWith(RECORD_SET_FAMILY_SUFFIX)) {
      return FormDataKind.STRUCTURE_WITH_COLLECTION;
    }
    if (VALUE_TABLE.equals(family)) {
      return FormDataKind.COLLECTION;
    }
    return VALUE_TREE.equals(family) ? FormDataKind.TREE : null;
  }

  /**
   * Часть qualifiedName синтетического типа формы после имени базового типа —
   * ссылка на объект метаданных формы ({@code Документ.Документ1.Форма.ФормаДокумента},
   * {@code ОбщаяФорма.Форма}). Даёт уникальность и читаемость в hover'е.
   */
  static String mdoSuffixRu(Form form) {
    return form.getMdoReference().getMdoRefRu();
  }

  static String mdoSuffixEn(Form form) {
    return form.getMdoReference().getMdoRef();
  }


  static List<MemberDescriptor> concat(List<MemberDescriptor> first, List<MemberDescriptor> second) {
    if (second.isEmpty()) {
      return first;
    }
    var result = new ArrayList<MemberDescriptor>(first.size() + second.size());
    result.addAll(first);
    result.addAll(second);
    return result;
  }

  /** Имя MD-объекта, которому подчинена форма; для общей формы — пусто. */
  static String ownerName(Form form) {
    return form instanceof ObjectForm objectForm ? shortName(objectForm.getOwner().getMdoRefRu()) : "";
  }

  /** {@code Документ.Документ1} → {@code Документ1}. */
  static String shortName(String mdoRef) {
    var dot = mdoRef.lastIndexOf('.');
    return dot < 0 ? mdoRef : mdoRef.substring(dot + 1);
  }

  /**
   * Имена платформенных типов, которые сопоставлены типам основного реквизита формы.
   * Реквизит объявлен составным типом, поэтому имён может быть несколько; порядок
   * сохраняется — он и задаёт приоритет.
   *
   * @param attributes реквизиты формы.
   * @param namesOf    что искать по ru-имени типа значения.
   * @return имена типов-кандидатов; пусто — основного реквизита у формы нет.
   */
  static Stream<String> mainAttributeTypeNames(List<FormAttribute> attributes,
                                                       Function<String, List<String>> namesOf) {
    return attributes.stream()
      .filter(FormAttribute::isMainAttribute)
      .flatMap(attribute -> attribute.getValueType().getTypes().stream())
      .flatMap(valueType -> namesOf.apply(valueType.fullName().getRu()).stream());
  }


  /** Имя типа из mdoRef и суффикса вида: {@code Справочник.Х} + {@code Ссылка}. */
  static String typeNameWithSuffix(String mdoRef, String suffix) {
    var dot = mdoRef.indexOf('.');
    return dot < 0 ? mdoRef : mdoRef.substring(0, dot) + suffix + mdoRef.substring(dot);
  }

}
