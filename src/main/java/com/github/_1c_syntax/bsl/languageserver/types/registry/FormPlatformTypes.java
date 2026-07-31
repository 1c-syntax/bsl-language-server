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

import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.mdo.Form;
import com.github._1c_syntax.bsl.mdo.storage.form.FormElementType;
import com.github._1c_syntax.bsl.mdo.support.DefaultFormKind;
import com.github._1c_syntax.bsl.mdo.support.FormType;
import com.github._1c_syntax.bsl.types.MDOType;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Соответствие «сущность формы из mdclasses → платформенный тип синтакс-помощника».
 * Чистый словарь без зависимостей от реестра: имена типов резолвятся вызывающей
 * стороной, и ненайденные (другая версия платформы, отсутствующий HBK) просто
 * не дают специализации.
 *
 * <h2>Что сопоставляется, а что нет</h2>
 * Из 188 типов {@code Расширение*} синтакс-помощника сопоставлены 82 — все, что
 * относятся к <b>форме и её элементам</b>. Остальные не сопоставлены не по недосмотру:
 * <ul>
 *   <li>{@code Расширение табличного поля …}, {@code Расширение колонки табличного поля …},
 *       {@code Расширение поля ввода <тип данных>} и прочие расширения контролов
 *       обычных форм ({@code ПолеВвода}, {@code ТабличноеПоле}, {@code ПолеСписка}) —
 *       это отдельная иерархия элементов обычной формы, здесь не моделируемая;</li>
 *   <li>{@code Расширение тестируемого …} — ветка автоматизированного тестирования
 *       интерфейса, в модуле формы недоступна, и все они пусты;</li>
 *   <li>расширения таблицы под части компоновщика настроек — данные лежат внутри
 *       компоновщика, а не в отдельном реквизите формы, поэтому по
 *       {@code ПутьКДанным} не резолвятся;</li>
 *   <li>{@code Расширение поля формы для поля дендрограммы} — в {@link FormElementType}
 *       mdclasses нет константы для дендрограммы, сопоставлять не с чем.</li>
 * </ul>
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

  private FormPlatformTypes() {
  }

  /** Базовый тип формы и тип коллекции её элементов — по виду формы. */
  enum FormKind {
    /** Управляемая форма (8.2+). */
    MANAGED("ФормаКлиентскогоПриложения", "ClientApplicationForm",
      "ВсеЭлементыФормы", "FormAllItems", "Элементы", "Items"),
    /**
     * Обычная форма (8.1 и режим совместимости). Имя коллекции элементов в HBK занято
     * дважды: {@code ЭлементыФормы}/{@code Controls} — коллекция обычной формы,
     * {@code ЭлементыФормы}/{@code FormItems} — подчинённые элементы группы управляемой
     * формы. Здесь имеется в виду первая (её отдаёт свойство {@code Форма.ЭлементыФормы}).
     */
    ORDINARY("Форма", "Form",
      "ЭлементыФормы", "Controls", "ЭлементыФормы", "Controls");

    private final String baseTypeRu;
    private final String baseTypeEn;
    private final String itemsTypeRu;
    private final String itemsTypeEn;
    private final String itemsPropertyRu;
    private final String itemsPropertyEn;

    FormKind(String baseTypeRu, String baseTypeEn, String itemsTypeRu, String itemsTypeEn,
             String itemsPropertyRu, String itemsPropertyEn) {
      this.baseTypeRu = baseTypeRu;
      this.baseTypeEn = baseTypeEn;
      this.itemsTypeRu = itemsTypeRu;
      this.itemsTypeEn = itemsTypeEn;
      this.itemsPropertyRu = itemsPropertyRu;
      this.itemsPropertyEn = itemsPropertyEn;
    }

    String baseTypeRu() {
      return baseTypeRu;
    }

    String baseTypeEn() {
      return baseTypeEn;
    }

    String itemsTypeRu() {
      return itemsTypeRu;
    }

    String itemsTypeEn() {
      return itemsTypeEn;
    }

    /** Имя свойства формы, отдающего коллекцию элементов. */
    String itemsPropertyRu() {
      return itemsPropertyRu;
    }

    String itemsPropertyEn() {
      return itemsPropertyEn;
    }

    /**
     * Вид формы по значению из mdclasses. {@link FormType#UNKNOWN} трактуется
     * как управляемая: неопознанные формы встречаются только в битых выгрузках,
     * а управляемые составляют подавляющее большинство.
     */
    static FormKind of(FormType formType) {
      return formType == FormType.ORDINARY ? ORDINARY : MANAGED;
    }
  }

  private static final String FORM_FIELD = "ПолеФормы";
  private static final String FORM_GROUP = "ГруппаФормы";
  private static final String FORM_BUTTON = "КнопкаФормы";
  private static final String FORM_DECORATION = "ДекорацияФормы";
  private static final String FORM_TABLE = "ТаблицаФормы";
  private static final String FORM_ITEM_ADDITION = "ДополнениеЭлементаФормы";
  private static final String FORM_ATTRIBUTE = "РеквизитФормы";

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
    return switch (elementType) {
      // Группы.
      case USUAL_GROUP -> "Расширение группы формы для обычной группы";
      case PAGE -> "Расширение группы формы для страницы";
      case PAGES -> "Расширение группы формы для страниц";
      case POPUP -> "Расширение группы формы для подменю";
      case COMMAND_BAR -> "Расширение группы формы для командной панели";
      case BUTTON_GROUP -> "Расширение группы формы для группы кнопок";
      case COLUMN_GROUP -> "Расширение группы формы для группы колонок";
      // Поля.
      case INPUT_FIELD -> "Расширение поля формы для поля ввода";
      case LABEL_FIELD -> "Расширение поля формы для поля надписи";
      case CHECK_BOX_FIELD -> "Расширение поля формы для поля флажка";
      case RADIO_BUTTON_FIELD -> "Расширение поля формы для поля переключателя";
      case PICTURE_FIELD -> "Расширение поля формы для поля картинки";
      case CALENDAR_FIELD -> "Расширение поля формы для поля календаря";
      case PERIOD_FIELD -> "Расширение поля формы для поля периода";
      case PROGRESS_BAR_FIELD -> "Расширение поля формы для поля индикатора";
      case TRACK_BAR_FIELD -> "Расширение поля формы для поля полосы регулирования";
      case TEXT_DOCUMENT_FIELD -> "Расширение поля формы для поля текстового документа";
      case SPREAD_SHEET_DOCUMENT_FIELD -> "Расширение поля формы для поля табличного документа";
      case HTML_DOCUMENT_FIELD -> "Расширение поля формы для поля HTML-документа";
      case FORMATTED_DOCUMENT_FIELD -> "Расширение поля формы для поля форматированного документа";
      case PDF_DOCUMENT_FIELD -> "Расширение поля формы для поля PDF-документа";
      case GRAPHICAL_SCHEMA_FIELD -> "Расширение поля формы для поля графической схемы";
      case GEOGRAPHICAL_SCHEMA_FIELD -> "Расширение поля формы для поля географической схемы";
      case CHART_FIELD -> "Расширение поля формы для поля диаграммы";
      case GANTT_CHART_FIELD -> "Расширение поля формы для поля диаграммы Ганта";
      case PLANNER_FIELD -> "Расширение поля формы для поля планировщика";
      // Декорации.
      case LABEL_DECORATION -> "Расширение декорации формы для надписи";
      case PICTURE_DECORATION -> "Расширение декорации формы для картинки";
      // Дополнения элемента.
      case SEARCH_STRING_ADDITION -> "Расширение дополнения элемента формы для отображения строки поиска";
      case VIEW_STATUS_ADDITION -> "Расширение дополнения элемента формы для отображения состояния просмотра";
      case SEARCH_CONTROL_ADDITION -> "Расширение дополнения элемента формы для отображения управления поиском";
      default -> null;
    };
  }

  /**
   * Суффикс синтетического типа элемента — имя его вида ({@code ОбычнаяГруппа},
   * {@code ПолеВвода}). Уникален в пределах базового типа, поэтому годится как ключ
   * специализации «база + расширение вида».
   */
  static String itemKindSuffix(FormElementType elementType) {
    return elementType.fullName().getRu();
  }

  
  /**
   * Вид данных, отображаемых таблицей формы. Расширение таблицы — единственное, что
   * определяется не видом элемента, а типом данных за ним: одна и та же
   * {@code ТаблицаФормы} над динамическим списком и над табличной частью получает
   * разные наборы свойств и событий.
   *
   * @param suffix        суффикс синтетического типа ({@code ТаблицаФормы.ДинамическийСписок})
   * @param extensionName qualifiedName расширения таблицы в синтакс-помощнике
   */
  enum TableDataKind {
    DYNAMIC_LIST("ДинамическийСписок", "Расширение таблицы формы для динамического списка"),
    TABULAR_SECTION("ТабличнаяЧасть", "Расширение таблицы формы для табличных частей"),
    VALUE_TABLE("ТаблицаЗначений", "Расширение таблицы формы для таблицы значений"),
    VALUE_TREE("ДеревоЗначений", "Расширение таблицы формы для дерева значений"),
    VALUE_LIST("СписокЗначений", "Расширение таблицы формы для списка значений"),
    // Части компоновщика настроек. Таблица над ними смотрит не на реквизит, а вглубь:
    // `Отчет.КомпоновщикНастроек.Настройки.Выбор`. Имена типов сверены по
    // синтакс-помощнику (`НастройкиКомпоновкиДанных`), а не выведены из имён расширений:
    // у `Выбор` тип называется `ВыбранныеПоляКомпоновкиДанных`, а не `ВыборКомпоновкиДанных`.
    DCS_SELECTED_FIELDS("ВыбранныеПоляКомпоновкиДанных",
      "Расширение таблицы формы для выбранных полей компоновки данных"),
    DCS_FILTER("ОтборКомпоновкиДанных",
      "Расширение таблицы формы для отбора компоновки данных"),
    DCS_ORDER("ПорядокКомпоновкиДанных",
      "Расширение таблицы формы для порядка компоновки данных"),
    DCS_CONDITIONAL_APPEARANCE("УсловноеОформлениеКомпоновкиДанных",
      "Расширение таблицы формы для условного оформления компоновки данных"),
    DCS_DATA_PARAMETERS("ЗначенияПараметровДанныхКомпоновкиДанных",
      "Расширение таблицы формы для значений параметров компоновки данных"),
    DCS_USER_FIELDS("ПользовательскиеПоляКомпоновкиДанных",
      "Расширение таблицы формы для пользовательских полей компоновки данных"),
    DCS_AVAILABLE_FIELDS("ДоступныеПоляКомпоновкиДанных",
      "Расширение таблицы формы для доступных полей компоновки данных"),
    DCS_SETTINGS_STRUCTURE("КоллекцияЭлементовСтруктурыНастроекКомпоновкиДанных",
      "Расширение таблицы формы для структуры настроек компоновки данных");

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

  /** Базовый рантайм-тип таблицы формы — общий для всех видов её данных. */
  static String tableTypeName() {
    return FORM_TABLE;
  }

  /** Свойство таблицы формы, отдающее текущую строку данных. */
  static final String CURRENT_DATA_RU = "ТекущиеДанные";

  static final String CURRENT_DATA_EN = "CurrentData";

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

  /**
   * Общая часть имени расширений управляемой формы. В bsl-context 0.9.x имена
   * расширений приведены к единой схеме {@code Расширение формы клиентского приложения
   * для <чего>}; до этого они назывались коротко ({@code Расширение документа}).
   */
  private static final String MANAGED_EXTENSION_PREFIX = "Расширение формы клиентского приложения для ";

  private static final String OBJECTS = MANAGED_EXTENSION_PREFIX + "объектов";
  private static final String RECORD_SET = MANAGED_EXTENSION_PREFIX + "набора записей";
  private static String managed(String what) {
    return MANAGED_EXTENSION_PREFIX + what;
  }

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
   * Имя типа-расширения формы по ru-имени типа её основного реквизита.
   *
   * @param attributeTypeRu ru-имя типа реквизита ({@code "ДокументОбъект.Документ1"},
   *                        {@code "КонстантыНабор"}).
   * @param formKind        вид формы — управляемая или обычная.
   * @return qualifiedName типа-расширения; {@code null}, если реквизит такого
   *   типа расширения формы не даёт.
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
    var list = isListForm(formKind);
    return switch (ownerType) {
      case CATALOG -> list ? "Расширение формы списка справочника" : "Расширение формы элемента справочника";
      case DOCUMENT -> list ? "Расширение формы списка документов" : "Расширение формы документа";
      // У журнала и критерия отбора форма ровно одна, и в mdclasses она приходит
      // не как форма списка, а как основная (designer-свойство <DefaultForm>).
      case DOCUMENT_JOURNAL -> "Расширение формы журнала документов";
      case TASK -> list ? "Расширение формы списка задач" : "Расширение формы задачи";
      case BUSINESS_PROCESS ->
        list ? "Расширение формы списка бизнес-процессов" : "Расширение формы объекта бизнес-процесс";
      case CHART_OF_CHARACTERISTIC_TYPES ->
        list ? "Расширение формы списка видов характеристик" : "Расширение формы элемента вида характеристик";
      case CHART_OF_ACCOUNTS ->
        list ? "Расширение формы списка плана счетов" : "Расширение формы элемента плана счетов";
      case CHART_OF_CALCULATION_TYPES ->
        list ? "Расширение формы списка видов расчета" : "Расширение формы вида расчета";
      case EXCHANGE_PLAN -> list ? "Расширение формы списка узлов" : "Расширение формы узла";
      case ENUM -> list ? "Расширение формы списка перечисления" : null;
      case FILTER_CRITERION -> "Расширение формы критерия отбора";
      case REPORT -> "Расширение формы отчета";
      case DATA_PROCESSOR -> "Расширение формы обработки";
      case CONSTANT -> "Расширение формы констант";
      case INFORMATION_REGISTER ->
        list ? "Расширение формы списка записей регистра сведений" : "Расширение формы записи регистра сведений";
      case ACCUMULATION_REGISTER -> list ? "Расширение формы списка записей регистра накопления" : null;
      case ACCOUNTING_REGISTER -> list ? "Расширение формы списка записей регистра бухгалтерии" : null;
      case CALCULATION_REGISTER -> list ? "Расширение формы списка записей регистра расчета" : null;
      default -> null;
    };
  }

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

  /**
   * Параметр-основание: у управляемой формы он лежит в структуре параметров
   * ({@code Параметры.Основание}), у обычной — свойством расширения
   * ({@code ПараметрОснование}). Тип у обоих не объявлен: платформе он не известен,
   * пока не посмотреть в метаданные владельца.
   */
  private static final Set<String> BASIS_PARAMETERS = Set.of("Основание", "ПараметрОснование");

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

  /** Тип реквизита списочной формы. */
  private static final String DYNAMIC_LIST = "ДинамическийСписок";

  /** Тип структуры, стоящей за свойством {@code Форма.Параметры}. */
  static final String FORM_DATA_STRUCTURE_RU = "ДанныеФормыСтруктура";

  static final String FORM_DATA_STRUCTURE_EN = "FormDataStructure";

  /** Тип, в который на форме превращается табличная часть объекта и таблица значений. */
  static final String FORM_DATA_COLLECTION_RU = "ДанныеФормыКоллекция";

  static final String FORM_DATA_COLLECTION_EN = "FormDataCollection";

  /** Строка коллекции данных формы. */
  static final String FORM_DATA_COLLECTION_ITEM_RU = "ДанныеФормыЭлементКоллекции";

  static final String FORM_DATA_COLLECTION_ITEM_EN = "FormDataCollectionItem";

  /**
   * Вид данных формы — тип, в который платформа превращает реквизит управляемой формы.
   * Прикладные типы на форму не переносятся: клиент видит не объект, а его данные.
   * <p>
   * Соответствие задано платформой и от конфигурации не зависит: объект (в том числе
   * менеджер значения константы и менеджер записи регистра) становится структурой,
   * набор записей — структурой с коллекцией записей, таблица значений — коллекцией,
   * дерево значений — деревом. Ссылки, примитивы, {@code СписокЗначений} и прочие
   * переносимые типы остаются собой и в этот перечень не входят.
   *
   * @param baseTypeRu    ru-имя платформенного типа данных формы
   * @param baseTypeEn    en-имя того же типа
   * @param specializable {@code true}, если состав свойств известен и под реквизит
   *                      имеет смысл заводить специализацию по прикладному типу
   * @param itemTypeRu    ru-имя типа строки; {@code null} — вид не коллекция
   * @param itemTypeEn    en-имя типа строки
   */
  enum FormDataKind {
    STRUCTURE(FORM_DATA_STRUCTURE_RU, FORM_DATA_STRUCTURE_EN, true, null, null),
    STRUCTURE_WITH_COLLECTION("ДанныеФормыСтруктураСКоллекцией", "FormDataStructureWithCollection",
      true, null, null),
    COLLECTION(FORM_DATA_COLLECTION_RU, FORM_DATA_COLLECTION_EN, false,
      FORM_DATA_COLLECTION_ITEM_RU, FORM_DATA_COLLECTION_ITEM_EN),
    TREE("ДанныеФормыДерево", "FormDataTree", false, "ДанныеФормыЭлементДерева", "FormDataTreeItem");

    private final String baseTypeRu;
    private final String baseTypeEn;
    private final boolean specializable;
    private final @Nullable String itemTypeRu;
    private final @Nullable String itemTypeEn;

    FormDataKind(String baseTypeRu, String baseTypeEn, boolean specializable,
                 @Nullable String itemTypeRu, @Nullable String itemTypeEn) {
      this.baseTypeRu = baseTypeRu;
      this.baseTypeEn = baseTypeEn;
      this.specializable = specializable;
      this.itemTypeRu = itemTypeRu;
      this.itemTypeEn = itemTypeEn;
    }

    String baseTypeRu() {
      return baseTypeRu;
    }

    String baseTypeEn() {
      return baseTypeEn;
    }

    /**
     * {@code true}, если состав свойств данных формы повторяет прикладной тип реквизита
     * и под него стоит завести специализацию. У таблиц и деревьев значений прикладного
     * типа с нужным составом нет — их колонки объявлены в самой форме, поэтому
     * специализируются они не отсюда, а от {@link #itemTypeRu()}.
     */
    boolean specializable() {
      return specializable;
    }

    /** Тип строки коллекции; {@code null} — вид не коллекция, строк у него нет. */
    @Nullable String itemTypeRu() {
      return itemTypeRu;
    }

    /** En-имя типа строки; {@code null} — вид не коллекция. */
    @Nullable String itemTypeEn() {
      return itemTypeEn;
    }
  }

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
}
