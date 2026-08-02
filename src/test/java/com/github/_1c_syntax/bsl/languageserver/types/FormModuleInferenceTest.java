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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Наружный эффект типов форм — вывод типа выражения в модуле формы через
 * {@link TypeService} (тот же API, что видят hover, автодополнение и диагностики).
 * Содержимое модуля подменяется, поэтому workspace пересоздаётся на каждый тест.
 */
@CleanupContextBeforeClassAndAfterClass
class FormModuleInferenceTest extends AbstractServerContextAwareTest {

  private static final String DOCUMENT_FORM_MODULE =
    "Documents/Документ1/Forms/ФормаДокумента/Ext/Form/Module.bsl";

  @Autowired
  private TypeService typeService;

  @Test
  void unqualifiedFormMembersAreInferred() {
    var documentContext = formModuleWith("""
      &НаКлиенте
      Процедура Тест()
        ЭлементыФормы = Элементы;
        ПолеНомер = Элементы.Номер;
        ТаблицаТЧ = Элементы.ТабличнаяЧасть1;
        ДанныеОбъекта = Объект;
        Форма = ЭтотОбъект;
      КонецПроцедуры
      """);

    assertThat(typesAtRhs(documentContext, "ЭлементыФормы"))
      .containsExactly("ВсеЭлементыФормы.Документ.Документ1.Форма.ФормаДокумента");
    assertThat(typesAtRhs(documentContext, "ПолеНомер")).containsExactly("ПолеФормы");
    assertThat(typesAtRhs(documentContext, "ТаблицаТЧ")).containsExactly("ТаблицаФормы");
    assertThat(typesAtRhs(documentContext, "ДанныеОбъекта"))
      .as("на управляемой форме за основным реквизитом стоят данные формы, а не сам объект")
      .containsExactly("ДанныеФормыСтруктура.ДокументОбъект.Документ1");
    assertThat(typesAtRhs(documentContext, "Форма"))
      .containsExactly("ФормаКлиентскогоПриложения.Документ.Документ1.Форма.ФормаДокумента");
  }

  @Test
  void mainAttributeChainReachesObjectAttributes() {
    var documentContext = formModuleWith("""
      &НаСервере
      Процедура Тест()
        Реквизит = Объект.Реквизит1;
        ЧерезЭтотОбъект = ЭтотОбъект.Объект.Реквизит1;
        ЧерезЭтуФорму = ЭтаФорма.Элементы;
      КонецПроцедуры
      """);

    assertThat(typesAtRhs(documentContext, "Реквизит")).isNotEmpty();
    assertThat(typesAtRhs(documentContext, "ЧерезЭтотОбъект")).isNotEmpty();
    assertThat(typesAtRhs(documentContext, "ЧерезЭтуФорму"))
      .containsExactly("ВсеЭлементыФормы.Документ.Документ1.Форма.ФормаДокумента");
  }

  @Test
  void formOpenedByNameGetsItsOwnType() {
    var documentContext = formModuleWith("""
      &НаКлиенте
      Процедура Тест()
        ПоТочномуИмени = ОткрытьФорму("Документ.Документ1.Форма.ФормаДокумента");
        ПоАнглийскомуИмени = ОткрытьФорму("Document.Документ1.Form.ФормаДокумента");
        ПоОсновнойФорме = ОткрытьФорму("Справочник.Справочник1.ФормаОбъекта");
        ПоОсновномуСписку = ПолучитьФорму("Справочник.Справочник1.ФормаСписка");
        ЭлементыЧужойФормы = ОткрытьФорму("Документ.Документ1.Форма.ФормаДокумента").Элементы;
        НесуществующаяФорма = ОткрытьФорму("Документ.Документ1.Форма.НетТакой");
      КонецПроцедуры
      """);

    assertThat(typesAtRhs(documentContext, "ПоТочномуИмени"))
      .containsExactly("ФормаКлиентскогоПриложения.Документ.Документ1.Форма.ФормаДокумента");
    assertThat(typesAtRhs(documentContext, "ПоАнглийскомуИмени"))
      .as("ссылка на форму может быть записана по-английски")
      .containsExactly("ФормаКлиентскогоПриложения.Документ.Документ1.Форма.ФормаДокумента");
    assertThat(typesAtRhs(documentContext, "ПоОсновнойФорме"))
      .as("<Объект>.ФормаОбъекта — синоним основной формы объекта")
      .containsExactly("ФормаКлиентскогоПриложения.Справочник.Справочник1.Форма.ФормаЭлемента");
    assertThat(typesAtRhs(documentContext, "ПоОсновномуСписку"))
      .containsExactly("ФормаКлиентскогоПриложения.Справочник.Справочник1.Форма.ФормаСписка");
    assertThat(typesAtRhs(documentContext, "ЭлементыЧужойФормы"))
      .as("от типа формы дальше разыменовывается её коллекция элементов")
      .containsExactly("ВсеЭлементыФормы.Документ.Документ1.Форма.ФормаДокумента");
    assertThat(typesAtRhs(documentContext, "НесуществующаяФорма"))
      .as("несуществующая форма не должна выдумывать тип")
      .doesNotContain("ФормаКлиентскогоПриложения.Документ.Документ1.Форма.НетТакой");
  }

  @Test
  void defaultFormAliasesResolveForEveryKind() {
    // Три вида основной формы одного объекта — самый частый способ открытия формы
    // в прикладном коде. Вид ищется среди DefaultFormKind по его же имени, а
    // конкретная форма — через FormOwner.getDefaultForm.
    var documentContext = formModuleWith("""
      &НаКлиенте
      Процедура Тест()
        ФормаОбъекта = ОткрытьФорму("Документ.Документ1.ФормаОбъекта");
        ФормаСписка = ОткрытьФорму("Документ.Документ1.ФормаСписка");
        ФормаВыбора = ОткрытьФорму("Документ.Документ1.ФормаВыбора");
        ПоАнглийски = ОткрытьФорму("Document.Документ1.ListForm");
      КонецПроцедуры
      """);

    var prefix = "ФормаКлиентскогоПриложения.Документ.Документ1.Форма.";
    assertThat(typesAtRhs(documentContext, "ФормаОбъекта")).containsExactly(prefix + "ФормаДокумента");
    assertThat(typesAtRhs(documentContext, "ФормаСписка")).containsExactly(prefix + "ФормаСписка");
    assertThat(typesAtRhs(documentContext, "ФормаВыбора")).containsExactly(prefix + "ФормаВыбора");
    assertThat(typesAtRhs(documentContext, "ПоАнглийски"))
      .as("вид основной формы тоже может быть записан по-английски")
      .containsExactly(prefix + "ФормаСписка");
  }

  @Test
  void collectionMethodResultsCarryRowType() {
    var documentContext = formModuleWith("""
      &НаСервере
      Процедура Тест()
        НоваяСтрока = Объект.ТабличнаяЧасть1.Добавить();
        Найденные = Объект.ТабличнаяЧасть1.НайтиСтроки(Новый Структура);
        Для Каждого НайденнаяСтрока Из Найденные Цикл
          ИзМассива = НайденнаяСтрока;
        КонецЦикла;
      КонецПроцедуры
      """);

    var row = "ДанныеФормыЭлементКоллекции.ДокументТабличнаяЧасть.Документ1.ТабличнаяЧасть1";
    assertThat(typesAtRhs(documentContext, "НоваяСтрока")).containsExactly(row);
    assertThat(typesAtRhs(documentContext, "Найденные")).containsExactly("Массив");
    assertThat(typesAtRhs(documentContext, "ИзМассива"))
      .as("НайтиСтроки даёт массив строк этой таблицы, а не нетипизированный")
      .containsExactly(row);
  }

  @Test
  void valueTableAttributeMethodsCarryItsRowToo() {
    // Репорт: `Таблица.НайтиПоИдентификатору(Ид)` отдавал нетипизированный элемент
    // коллекции. Явные типы элементов закрывают только обход, а обращение по методу
    // идёт через его объявление — платформа называет там обобщённую строку. У зеркала
    // табличной части замена была, у реквизита-таблицы значений — нет.
    var documentContext = formModuleWith("""
      &НаСервере
      Процедура Тест()
        ПоИдентификатору = ТаблицаПодбора.НайтиПоИдентификатору(1);
        Добавленная = ТаблицаПодбора.Добавить();
        КолонкаНайденной = ТаблицаПодбора.НайтиПоИдентификатору(1).Номенклатура;
        КолонкаДобавленной = ТаблицаПодбора.Добавить().Количество;
      КонецПроцедуры
      """);

    var row = "ДанныеФормыЭлементКоллекции.Документ.Документ1.Форма.ФормаДокумента.ТаблицаПодбора";
    // Не containsExactly: в синтакс-помощнике возврат объявлен как «строка либо
    // Неопределено», в JSON-фолбэке — одной строкой. Важна сама строка: обобщённой
    // в наборе быть не должно.
    assertThat(typesAtRhs(documentContext, "ПоИдентификатору"))
      .contains(row)
      .doesNotContain("ДанныеФормыЭлементКоллекции");
    assertThat(typesAtRhs(documentContext, "Добавленная")).containsExactly(row);
    assertThat(typesAtRhs(documentContext, "КолонкаНайденной"))
      .as("колонки реквизита-таблицы объявлены в самой форме")
      .containsExactly("СправочникСсылка.Справочник1");
    assertThat(typesAtRhs(documentContext, "КолонкаДобавленной")).containsExactly("Число");
  }

  @Test
  void unloadedValueTableAttributeCarriesItsColumns() {
    var documentContext = formModuleWith("""
      &НаСервере
      Процедура Тест()
        Выгруженная = ТаблицаПодбора.Выгрузить();
        Найденные = ТаблицаПодбора.НайтиСтроки(Новый Структура);
      КонецПроцедуры
      """);

    var unloaded = typeSetAtRhs(documentContext, "Выгруженная");
    assertThat(unloaded.refs()).extracting(TypeRef::qualifiedName).containsExactly("ТаблицаЗначений");
    assertThat(unloaded.getElementTypes().getLocalFields(
      unloaded.getElementTypes().refs().iterator().next()))
      .as("колонки выгруженной таблицы — колонки реквизита")
      .containsKeys("Номенклатура", "Количество");

    assertThat(typeSetAtRhs(documentContext, "Найденные").getElementTypes().refs())
      .extracting(TypeRef::qualifiedName)
      .containsExactly("ДанныеФормыЭлементКоллекции.Документ.Документ1.Форма.ФормаДокумента.ТаблицаПодбора");
  }

  @Test
  void unloadedValueTableCarriesColumns() {
    var documentContext = formModuleWith("""
      &НаСервере
      Процедура Тест()
        Выгруженная = Объект.ТабличнаяЧасть1.Выгрузить();
      КонецПроцедуры
      """);

    var types = typeSetAtRhs(documentContext, "Выгруженная");
    assertThat(types.refs()).extracting(TypeRef::qualifiedName).containsExactly("ТаблицаЗначений");

    var rowTypes = types.getElementTypes();
    assertThat(rowTypes.refs()).extracting(TypeRef::qualifiedName)
      .containsExactly("СтрокаТаблицыЗначений");
    assertThat(rowTypes.getLocalFields(rowTypes.refs().iterator().next()))
      .as("колонки выгруженной таблицы — колонки выгружаемой табличной части")
      .containsKey("Реквизит1");
  }

  @Test
  void formNameFromVariableStaysGeneric() {
    // Имя собрано в переменной — статически неизвестно; тип конкретной формы
    // выводить не из чего, остаётся обобщённый возврат платформенной функции.
    var documentContext = formModuleWith("""
      &НаКлиенте
      Процедура Тест()
        ИмяФормы = "Документ.Документ1.Форма.ФормаДокумента";
        Форма = ОткрытьФорму(ИмяФормы);
      КонецПроцедуры
      """);

    assertThat(typesAtRhs(documentContext, "Форма"))
      .doesNotContain("ФормаКлиентскогоПриложения.Документ.Документ1.Форма.ФормаДокумента");
  }

  @Test
  void attributeConvertedToValueGetsTheTypeItIsDeclaredWith() {
    // На форме за реквизитом стоят данные формы, но обратное преобразование
    // возвращает ровно то, чем реквизит объявлен в Form.xml.
    var documentContext = formModuleWith("""
      &НаСервере
      Процедура Тест()
        ОбъектДокумента = РеквизитФормыВЗначение("Объект");
        Таблица = РеквизитФормыВЗначение("ТаблицаПодбора");
        ПоАнглийски = FormAttributeToValue("Объект");
        ЧерезФорму = ЭтотОбъект.РеквизитФормыВЗначение("Объект");
        НетТакогоРеквизита = РеквизитФормыВЗначение("НетТакого");
      КонецПроцедуры
      """);

    assertThat(typesAtRhs(documentContext, "ОбъектДокумента"))
      .containsExactly("ДокументОбъект.Документ1");
    assertThat(typesAtRhs(documentContext, "Таблица")).containsExactly("ТаблицаЗначений");
    assertThat(typesAtRhs(documentContext, "ПоАнглийски"))
      .containsExactly("ДокументОбъект.Документ1");
    assertThat(typesAtRhs(documentContext, "ЧерезФорму"))
      .as("та же функция через получателя-форму")
      .containsExactly("ДокументОбъект.Документ1");
    assertThat(typesAtRhs(documentContext, "НетТакогоРеквизита"))
      .as("выдумывать тип по несуществующему имени нельзя")
      .doesNotContain("ДокументОбъект.Документ1");
  }

  @Test
  void explicitTypeArgumentWinsOverTheDeclaredOne() {
    // У ДанныеФормыВЗначение второй параметр обязателен, у РеквизитФормыВЗначение —
    // нет, но если он передан, преобразование идёт именно в него.
    var documentContext = formModuleWith("""
      &НаСервере
      Процедура Тест()
        ИзДанных = ДанныеФормыВЗначение(Объект, Тип("ДокументОбъект.Документ1"));
        ИзРеквизита = РеквизитФормыВЗначение("ТаблицаПодбора", Тип("ДокументОбъект.Документ1"));
        ПоАнглийски = FormDataToValue(Объект, Type("ДокументОбъект.Документ1"));
      КонецПроцедуры
      """);

    assertThat(typesAtRhs(documentContext, "ИзДанных"))
      .containsExactly("ДокументОбъект.Документ1");
    assertThat(typesAtRhs(documentContext, "ИзРеквизита"))
      .as("переданный тип выигрывает у объявленного типа реквизита")
      .containsExactly("ДокументОбъект.Документ1");
    assertThat(typesAtRhs(documentContext, "ПоАнглийски"))
      .containsExactly("ДокументОбъект.Документ1");
  }

  @Test
  void conversionArgumentsFromVariablesStayGeneric() {
    // Ни имя реквизита, ни тип статически неизвестны — выводить не из чего.
    var documentContext = formModuleWith("""
      &НаСервере
      Процедура Тест()
        ИмяРеквизита = "Объект";
        ТипЗначения = Тип("ДокументОбъект.Документ1");
        ПоИмениИзПеременной = РеквизитФормыВЗначение(ИмяРеквизита);
        ПоТипуИзПеременной = ДанныеФормыВЗначение(Объект, ТипЗначения);
      КонецПроцедуры
      """);

    assertThat(typesAtRhs(documentContext, "ПоИмениИзПеременной"))
      .doesNotContain("ДокументОбъект.Документ1");
    assertThat(typesAtRhs(documentContext, "ПоТипуИзПеременной"))
      .doesNotContain("ДокументОбъект.Документ1");
  }

  @Test
  void formItemByLiteralNameIsTheSameItemAsByQualifiedName() {
    // `Элементы["Кнопка"]` и `Элементы.Найти("Кнопка")` — тот же элемент, что и
    // `Элементы.Кнопка`, только имя записано строкой.
    var documentContext = formModuleWith("""
      Процедура Тест()
        ЭлементПрямо = Элементы.Номер;
        ЭлементПоИндексу = Элементы["Номер"];
        ЭлементПоПоиску = Элементы.Найти("Номер");
      КонецПроцедуры
      """);

    var direct = typesAtEndOfRhs(documentContext, "ЭлементПрямо");
    assertThat(direct).as("элемент формы по имени члена").isNotEmpty();
    assertThat(typesAtEndOfRhs(documentContext, "ЭлементПоИндексу")).isEqualTo(direct);
    assertThat(typesAtEndOfRhs(documentContext, "ЭлементПоПоиску")).isEqualTo(direct);
  }

  @Test
  void unknownItemNameLeavesTheGeneralPath() {
    var documentContext = formModuleWith("""
      Процедура Тест()
        Элемент = Элементы["НетТакогоЭлемента"];
      КонецПроцедуры
      """);

    assertThat(typesAtEndOfRhs(documentContext, "Элемент"))
      .as("выдумывать тип по несуществующему имени нельзя")
      .isEmpty();
  }

  @Test
  void itemCreatedInCodeIsRefinedByItsKindAssignment() {
    // Элемент, созданный в коде, получает базовый тип из `Тип("...")`, а конкретный
    // вид — отдельной строкой; от вида зависит почти вся специфика элемента.
    var documentContext = formModuleWith("""
      Процедура Тест()
        Группа = Элементы.Добавить("Группа1", Тип("ГруппаФормы"));
        Группа.Вид = ВидГруппыФормы.ОбычнаяГруппа;
        Проверка = Группа;
      КонецПроцедуры
      """);

    assertThat(typesAtRhs(documentContext, "Проверка"))
      .as("вид группы уточняет тип переменной: у обычной группы своё расширение")
      .containsExactly("ГруппаФормы.ОбычнаяГруппа");
  }

  @Test
  void kindRefinementStartsAtItsAssignment() {
    // Уточнение по виду — изменение типа на месте, поэтому считается по потоку: до
    // своей строки элемент ещё просто группа формы, а не обычная группа.
    var documentContext = formModuleWith("""
      Процедура Тест()
        Группа = Элементы.Добавить("Группа1", Тип("ГруппаФормы"));
        ДоВида = Группа;
        Группа.Вид = ВидГруппыФормы.ОбычнаяГруппа;
        ПослеВида = Группа;
      КонецПроцедуры
      """);

    // `Добавить` объявлен возвращающим любой элемент формы, поэтому до уточнения
    // переменная несёт весь этот набор.
    assertThat(typesAtRhs(documentContext, "ДоВида"))
      .as("до присваивания вида — объявленный платформой набор, без вида")
      .contains("ГруппаФормы")
      .doesNotContain("ГруппаФормы.ОбычнаяГруппа");
    assertThat(typesAtRhs(documentContext, "ПослеВида"))
      .as("после присваивания вида — тип вида")
      .containsExactly("ГруппаФормы.ОбычнаяГруппа");
  }

  @Test
  void assignmentToAnotherPropertyDoesNotRefineTheType() {
    var documentContext = formModuleWith("""
      Процедура Тест()
        Группа = Элементы.Добавить("Группа1", Тип("ГруппаФормы"));
        Группа.Заголовок = "Прочее";
        Проверка = Группа;
      КонецПроцедуры
      """);

    assertThat(typesAtRhs(documentContext, "Проверка"))
      .as("присваивание не виду тип не трогает")
      .doesNotContain("ГруппаФормы.ОбычнаяГруппа");
  }

  @Test
  void writeParametersCarryTheKeysPlatformPutsThere() {
    // Тип у параметра объявлен как `Структура` — то есть не сказано ничего, — а состав
    // платформа называет в описании параметра: у формы документа это `РежимЗаписи` и
    // `РежимПроведения`, добавляемые командами «Провести» и «Отмена проведения».
    var documentContext = formModuleWith("""
      &НаКлиенте
      Процедура ПередЗаписью(Отказ, ПараметрыЗаписи)
        Пакет = ПараметрыЗаписи;
      КонецПроцедуры
      """);

    var packet = typeSetAtRhs(documentContext, "Пакет");
    assertThat(packet.refs()).extracting(TypeRef::qualifiedName).containsExactly("Структура");
    assertThat(packet.getAllFieldNames())
      .contains("РежимЗаписи", "РежимПроведения")
      .as("ключ структуры — строка, а не идентификатор: платформа пишет её на языке проекта")
      .contains("WriteMode", "PostingMode");
  }

  @Test
  void predefinedKeyResolvesWhileAnInventedOneStaysUnknown() {
    var documentContext = formModuleWith("""
      &НаКлиенте
      Процедура ПередЗаписью(Отказ, ПараметрыЗаписи)
        Режим = ПараметрыЗаписи.РежимЗаписи;
        ПоАнглийски = ПараметрыЗаписи.WriteMode;
        Опечатка = ПараметрыЗаписи.РежимЗаписей;
      КонецПроцедуры
      """);

    assertThat(unknownMemberAtRhs(documentContext, "Режим"))
      .as("предопределённый ключ — не опечатка")
      .isEmpty();
    assertThat(unknownMemberAtRhs(documentContext, "ПоАнглийски")).isEmpty();
    assertThat(unknownMemberAtRhs(documentContext, "Опечатка"))
      .as("состав не закрывает структуру, но и известные ключи не выдумывает")
      .isPresent();
  }

  @Test
  void everyWriteEventGetsTheSameComposition() {
    // Состав закреплён за параметром, а не за событием: платформа кладёт в них одну и
    // ту же структуру, и её же принимает метод `Записать`.
    var documentContext = formModuleWith("""
      &НаКлиенте
      Процедура ПослеЗаписи(ПараметрыЗаписи)
        ПослеЗаписиПакет = ПараметрыЗаписи;
      КонецПроцедуры

      &НаСервере
      Процедура ПриЗаписиНаСервере(Отказ, ТекущийОбъект, ПараметрыЗаписи)
        НаСервереПакет = ПараметрыЗаписи;
      КонецПроцедуры
      """);

    assertThat(typeSetAtRhs(documentContext, "ПослеЗаписиПакет").getAllFieldNames())
      .contains("РежимЗаписи", "РежимПроведения");
    assertThat(typeSetAtRhs(documentContext, "НаСервереПакет").getAllFieldNames())
      .contains("РежимЗаписи", "РежимПроведения");
  }

  @Test
  void keysInsertedByApplicationCodeLiveAlongsideThePredefinedOnes() {
    // Платформа прямо оговаривает «состав других параметров произвольный», поэтому
    // состав навешивается на обычную Структуру: свои ключи копятся в том же наборе.
    var documentContext = formModuleWith("""
      &НаКлиенте
      Процедура ПередЗаписью(Отказ, ПараметрыЗаписи)
        ПараметрыЗаписи.Вставить("СвойКлюч", ЭтотОбъект);
        Пакет = ПараметрыЗаписи;
      КонецПроцедуры
      """);

    assertThat(typeSetAtRhs(documentContext, "Пакет").getAllFieldNames())
      .contains("РежимЗаписи", "СвойКлюч");
  }

  /** Типы правой части присваивания с кареткой в её конце — на последнем символе выражения. */
  private List<String> typesAtEndOfRhs(DocumentContext documentContext, String assignedVar) {
    var content = documentContext.getContent();
    var marker = assignedVar + " = ";
    var markerIdx = content.indexOf(marker);
    assertThat(markerIdx).as("маркер `%s` не найден", marker).isGreaterThanOrEqualTo(0);
    var caret = content.indexOf(';', markerIdx + marker.length()) - 1;
    var lineStart = content.lastIndexOf('\n', caret) + 1;
    var line = content.substring(0, caret).split("\n").length - 1;
    return typeService.expressionTypesAt(documentContext, new Position(line, caret - lineStart))
      .refs().stream()
      .map(TypeRef::qualifiedName)
      .toList();
  }

  private DocumentContext formModuleWith(String content) {
    initServerContext(PATH_TO_METADATA);
    var uri = Absolute.uri(new File(PATH_TO_METADATA, DOCUMENT_FORM_MODULE));
    var documentContext = context.addDocument(uri);
    context.rebuildDocument(documentContext, content, 1);
    return documentContext;
  }

  /** Типы правой части присваивания {@code <var> = …;} — каретка на последнем сегменте цепочки. */
  private List<String> typesAtRhs(DocumentContext documentContext, String assignedVar) {
    return typeSetAtRhs(documentContext, assignedVar)
      .refs().stream().map(TypeRef::qualifiedName).toList();
  }

  /** То же, но целиком: с типами элементов и полями «открытого» объекта. */
  private TypeSet typeSetAtRhs(DocumentContext documentContext, String assignedVar) {
    return typeService.expressionTypesAt(documentContext, caretAtRhs(documentContext, assignedVar));
  }

  /**
   * Типы ресивера, у которого не нашлось члена под кареткой, — ровно то, на чём
   * срабатывает {@code UnknownMember}. Пусто — член резолвится либо тип ресивера
   * не выведен.
   */
  private Optional<TypeSet> unknownMemberAtRhs(DocumentContext documentContext, String assignedVar) {
    return typeService.unknownMemberReceiverAt(documentContext, caretAtRhs(documentContext, assignedVar));
  }

  /** Каретка на последнем сегменте правой части присваивания {@code <var> = …;}. */
  private static Position caretAtRhs(DocumentContext documentContext, String assignedVar) {
    var content = documentContext.getContent();
    var marker = assignedVar + " = ";
    var markerIdx = content.indexOf(marker);
    assertThat(markerIdx).as("маркер `%s` не найден", marker).isGreaterThanOrEqualTo(0);
    var rhsStart = markerIdx + marker.length();
    var statementEnd = content.indexOf(';', rhsStart);
    var lastDot = lastDotOutsideLiteral(content, rhsStart, statementEnd);
    var caret = lastDot < 0 ? rhsStart : lastDot + 1;
    var lineStart = content.lastIndexOf('\n', caret) + 1;
    var line = content.substring(0, caret).split("\n").length - 1;
    return new Position(line, caret - lineStart);
  }

  /**
   * Последняя точка правой части <b>вне строкового литерала</b>. Точки внутри кавычек
   * пропускаются: в {@code ОткрытьФорму("Документ.Документ1.Форма.X")} каретка на них
   * попала бы внутрь аргумента, и {@code expressionTypesAt} вернул бы тип литерала.
   * Если таких точек нет, каретка ставится в начало правой части.
   */
  private static int lastDotOutsideLiteral(String content, int from, int to) {
    var inLiteral = false;
    var lastDot = -1;
    for (var i = from; i < to; i++) {
      var c = content.charAt(i);
      if (c == '"') {
        inLiteral = !inLiteral;
      } else if (c == '.' && !inLiteral) {
        lastDot = i;
      }
    }
    return lastDot;
  }
}
