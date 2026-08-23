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

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.hover.PlatformMemberHoverBuilder;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.Collection;
import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Стандартные параметры формы приходят только из синтакс-помощника
 * ({@code ContextType.formParameters()}, bsl-context 0.8.0+) — в JSON-фолбэке их нет,
 * поэтому тест требует установленной 1С.
 */
@CleanupContextBeforeClassAndAfterClass
@TestPropertySource(properties = "app.platform-context.enabled=true")
@EnabledIfEnvironmentVariable(named = "BSL_LANGUAGE_SERVER_RUN_HBK_TESTS",
  matches = "true",
  disabledReason = "Требует HBK 1С (параметры формы берутся из bsl-context)")
class FormParametersHbkTest extends AbstractServerContextAwareTest {

  private static final String DOCUMENT_FORM = "ФормаКлиентскогоПриложения.Документ.Документ1.Форма.ФормаДокумента";
  private static final String CATALOG_ITEM_FORM =
    "ФормаКлиентскогоПриложения.Справочник.Справочник1.Форма.ФормаЭлемента";

  @Autowired
  private ConfigurationTypesProvider provider;

  @Autowired
  private TypeRegistry typeRegistry;

  @Autowired
  private PlatformMemberHoverBuilder platformMemberHoverBuilder;

  @BeforeEach
  void setUp() {
    initServerContextOnce(Absolute.path(PATH_TO_METADATA));
    context.getConfiguration();
    provider.tryRegister();
  }

  @Test
  void parametersPropertyPointsToPerFormStructure() {
    var parameters = member(DOCUMENT_FORM, MemberKind.PROPERTY, "Параметры");

    assertThat(parameters).isNotNull();
    assertThat(parameters.returnTypes().refs()).extracting(TypeRef::qualifiedName)
      .containsExactly("ДанныеФормыСтруктура.Документ.Документ1.Форма.ФормаДокумента");
  }

  @Test
  void standardParametersComeFromTheFormTypeAndItsExtension() {
    var members = parametersOf(DOCUMENT_FORM);

    assertThat(names(members))
      .as("общие для любой формы — с ФормаКлиентскогоПриложения")
      .contains("ТолькоПросмотр", "КлючНазначенияИспользования", "ВыборДобавлением",
        "ЗакрыватьПриВыборе", "ПараметрыФункциональныхОпций")
      .as("специфичные для документа — с Расширение документа")
      .contains("Ключ", "Основание", "ЗначенияЗаполнения", "ЗначениеКопирования");
  }

  @Test
  void keyParameterTypeIsSpecializedByFormOwner() {
    // В HBK параметр объявлен как ДокументСсылка.<Имя документа> — плейсхолдер
    // подставляется именем объекта, которому подчинена форма.
    assertThat(qualifiedNames(parameter(DOCUMENT_FORM, "Ключ")))
      .containsExactly("ДокументСсылка.Документ1");
    assertThat(qualifiedNames(parameter(CATALOG_ITEM_FORM, "Ключ")))
      .containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  void copyingValueIsTypedLikeTheKey() {
    // В синтакс-помощнике у ЗначениеКопирования тип не объявлен вовсе, хотя это
    // ссылка на копируемый объект — тот же тип, что у Ключ.
    assertThat(qualifiedNames(parameter(DOCUMENT_FORM, "ЗначениеКопирования")))
      .containsExactly("ДокументСсылка.Документ1");
    assertThat(qualifiedNames(parameter(CATALOG_ITEM_FORM, "ЗначениеКопирования")))
      .containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  void basisIsTypedByTheObjectsItIsEnteredOnBasisOf() {
    // Тип у параметра не объявлен: платформа не знает, что допустимо передать
    // основанием, пока не посмотреть `ВводитсяНаОсновании` владельца формы.
    assertThat(qualifiedNames(parameter(DOCUMENT_FORM, "Основание")))
      .containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  void basisOfObjectEnteredOnNoBasisIsUndefined() {
    // Справочник ни на чём не вводится — передавать в параметр нечего.
    assertThat(qualifiedNames(parameter(CATALOG_ITEM_FORM, "Основание")))
      .containsExactly("Неопределено");
  }

  @Test
  void handlerOfElementEventTakesContractFromTheElementType() {
    // Контракт `ПриИзменении` объявлен у ПолеФормы, а не у формы: без типа элемента
    // обработчику досталась бы заглушка «обработчик события формы OnChange».
    var handler = member(DOCUMENT_FORM, MemberKind.EVENT, "Реквизит1ПриИзменении");
    assertThat(handler).isNotNull();

    assertThat(handler.description())
      .as("описание — платформенное, а не заглушка по имени события")
      .isNotEmpty()
      .doesNotContain("OnChange");
    assertThat(handler.signatures()).isNotEmpty();
    assertThat(handler.signatures().get(0).parameters())
      .as("платформа передаёт в обработчик сам элемент, но в синтакс-помощнике "
        + "этого параметра нет — он дописывается первым")
      .extracting(p -> p.bilingualName().ru())
      .containsExactly("Элемент");
    assertThat(handler.signatures().get(0).parameters().get(0).types().refs())
      .extracting(TypeRef::qualifiedName)
      .containsExactly("ПолеФормы.ПолеВвода");
  }

  @Test
  void eventContractIsSpecializedByFormOwner() {
    // `ТекущийОбъект` у ПриЗаписиНаСервере объявлен обобщённо —
    // `ДокументОбъект.<Имя документа>`, — а на конкретной форме известно, какой
    // именно объект туда придёт.
    var handler = member(DOCUMENT_FORM, MemberKind.EVENT, "ПриЗаписиНаСервере");
    assertThat(handler).isNotNull();

    var currentObject = handler.signatures().get(0).parameters().stream()
      .filter(parameter -> "ТекущийОбъект".equals(parameter.bilingualName().ru()))
      .findFirst()
      .orElseThrow();
    assertThat(currentObject.types().refs()).extracting(TypeRef::qualifiedName)
      .containsExactly("ДокументОбъект.Документ1");
  }

  @Test
  void objectInfrastructureIsFilteredOutOfFormData() {
    // Полный набор инфраструктурных свойств объекта есть только в синтакс-помощнике:
    // в JSON-фолбэке из них объявлен один ЭтотОбъект.
    var objectAttribute = member(DOCUMENT_FORM, MemberKind.PROPERTY, "Объект");
    assertThat(objectAttribute).isNotNull();
    var objectType = objectAttribute.returnTypes().refs().iterator().next();

    assertThat(names(typeRegistry.getMembers(objectType, FileType.BSL)))
      .as("инфраструктура объекта живёт только на сервере и в данные формы не переносится")
      .doesNotContain("ЭтотОбъект", "Движения", "ОбменДанными", "ДополнительныеСвойства",
        "ЗаписьИсторииДанных", "ПринадлежностьПоследовательностям")
      .as("стандартные реквизиты, реквизиты и общие реквизиты — переносятся")
      .contains("Ссылка", "Номер", "Дата", "Проведен", "ПометкаУдаления", "ВерсияДанных",
        "Реквизит1", "ОбщийРеквизит1");
  }

  @Test
  void parametersHoverListsParametersAsFields() {
    var parameters = member(DOCUMENT_FORM, MemberKind.PROPERTY, "Параметры");
    assertThat(parameters).isNotNull();
    var formRef = typeRegistry.resolve(DOCUMENT_FORM).orElseThrow();

    var markup = platformMemberHoverBuilder.build(formRef, parameters, -1).getValue();

    assertThat(markup)
      .as("состав параметров формы виден прямо в hover'е — как поля Структуры")
      .contains("* **ТолькоПросмотр**")
      .as("ключевой параметр выделен курсивом, отдельной строкой «Ключевой параметр» не идёт")
      .contains("* ***Ключ***: ДокументСсылка.Документ1")
      .doesNotContain("Ключевой параметр.")
      .as("имя типа — платформенное, синтетический суффикс наружу не течёт")
      .contains("Параметры: ДанныеФормыСтруктура\n")
      .as("члены базового ДанныеФормыСтруктура полями не считаются")
      .doesNotContain("ИсходныйКлючЗаписи");

    // У ВыборДобавлением описание в синтакс-помощнике многоабзацное. Абзацы должны
    // остаться внутри своего пункта: жёсткий перенос (два пробела в конце строки)
    // плюс отступ по ширине маркера, без пустой строки — иначе пункт обрывается.
    assertThat(markup)
      .as("абзацы описания остаются внутри пункта списка")
      .contains("множественных значений.  \n  Параметр принимает значение Истина");
  }

  @Test
  void formItemsCarryKindSpecificExtensionMembers() {
    // ЦветФона объявлен не в ГруппаФормы, а в «Расширение группы формы для обычной
    // группы»; без подмешивания расширения по виду элемента свойство не резолвится.
    var itemsType = typeRegistry
      .resolve("ВсеЭлементыФормы.Документ.Документ1.Форма.ФормаДокумента")
      .orElseThrow();
    var field = typeRegistry.getMembers(itemsType, FileType.BSL).stream()
      .filter(m -> m.matches("Номер"))
      .findFirst()
      .orElseThrow();

    var fieldType = field.returnTypes().refs().iterator().next();
    var memberNames = names(typeRegistry.getMembers(fieldType, FileType.BSL));
    assertThat(memberNames)
      .as("собственные члены ПолеФормы")
      .contains("Имя", "Заголовок", "Видимость")
      .as("члены расширения поля ввода")
      .contains("КнопкаВыбора", "Формат", "АвтоОтметкаНезаполненного");
  }

  @Test
  void everyTableDataKindResolvesBothItsTypeAndItsExtension() {
    // Вид данных таблицы связывает два имени: тип, который лежит за `ПутьКДанным`,
    // и расширение таблицы под него. Имена не выводятся друг из друга — у «выбора»
    // тип называется `ВыбранныеПоляКомпоновкиДанных`, — поэтому каждое проверяется
    // по синтакс-помощнику, иначе опечатка молча оставит таблицу без свойств.
    for (var dataKind : TableDataKind.values()) {
      assertThat(typeRegistry.resolve(dataKind.extensionName()))
        .as("расширение %s", dataKind.extensionName())
        .isPresent();
      if (dataKind == TableDataKind.TABULAR_SECTION) {
        // Единственный вид, чей суффикс — не имя типа, а семейство: у табличной части
        // тип свой на каждый объект (`ДокументТабличнаяЧасть.Заказ`).
        continue;
      }
      assertThat(typeRegistry.resolve(dataKind.suffix()))
        .as("тип данных %s", dataKind.suffix())
        .isPresent();
    }
  }

  @Test
  void tableOverDataCompositionSpeaksInCompositionIdentifiers() {
    // Синтакс-помощник описывает это текстом в описании расширения, а не типами:
    // сами свойства объявлены как «Произвольный», и без подстановки цепочка от
    // `ТекущаяСтрока` обрывалась.
    var selectedFields = "ТаблицаФормы.ВыбранныеПоляКомпоновкиДанных";
    assertThat(qualifiedNames(member(selectedFields, MemberKind.PROPERTY, "ТекущаяСтрока")))
      .containsExactly("ИдентификаторКомпоновкиДанных");
    assertThat(qualifiedNames(member(selectedFields, MemberKind.PROPERTY, "ТекущийРодитель")))
      .containsExactly("ИдентификаторКомпоновкиДанных");
    // Описание говорит «структуру, заполненную копией данных», но на платформе
    // ТипЗнч(Элементы.КомпоновщикНастроекНастройкиОтбор.ТекущиеДанные) даёт
    // ДанныеФормыСтруктура: обычную Структуру таблица формы не отдаёт нигде.
    assertThat(qualifiedNames(member(selectedFields, MemberKind.PROPERTY, "ТекущиеДанные")))
      .containsExactly("ДанныеФормыСтруктура");
    assertThat(qualifiedNames(member(selectedFields, MemberKind.METHOD, "ДанныеСтроки")))
      .containsExactly("ДанныеФормыСтруктура");
  }

  @Test
  void tableOverGanttChartHasItsOwnIdentifier() {
    var gantt = "ТаблицаФормы.ДиаграммаГанта";

    assertThat(qualifiedNames(member(gantt, MemberKind.PROPERTY, "ТекущаяСтрока")))
      .containsExactly("ИдентификаторЗначенияДиаграммыГанта");
    assertThat(qualifiedNames(member(gantt, MemberKind.PROPERTY, "ТекущиеДанные")))
      .as("у диаграммы Ганта это данные формы с колонками, а не копия-структура")
      .containsExactly("ДанныеФормыСтруктура");
  }

  @Test
  void currentDataOfTableIsTheRowOfItsCollection() {
    // Описание расширения говорит «структуру, заполненную копией данных», но на
    // платформе ТипЗнч(Элементы.ТабличнаяЧасть1.ТекущиеДанные) даёт
    // ДанныеФормыЭлементКоллекции: «структура» там про устройство значения, а не про тип.
    // Тип заводится на конкретную таблицу: колонки у каждой свои.
    var table = "ТаблицаФормы.Документ.Документ1.Форма.ФормаДокумента.ТабличнаяЧасть1";
    var currentData = member(table, MemberKind.PROPERTY, "ТекущиеДанные");
    assertThat(currentData).isNotNull();

    var rowRef = currentData.returnTypes().refs().iterator().next();
    assertThat(rowRef.qualifiedName())
      .as("строка коллекции данных формы, а не копия-структура")
      .startsWith("ДанныеФормыЭлементКоллекции.");
    assertThat(typeRegistry.displayName(rowRef, com.github._1c_syntax.bsl.languageserver.configuration.Language.RU))
      .isEqualTo("ДанныеФормыЭлементКоллекции");
    assertThat(names(typeRegistry.getMembers(rowRef, FileType.BSL)))
      .as("свойства строки — колонки табличной части")
      .contains("Реквизит1")
      .as("методы строки данных формы на месте")
      .contains("ПолучитьИдентификатор");

    assertThat(qualifiedNames(member(table, MemberKind.METHOD, "ДанныеСтроки")))
      .containsExactly(rowRef.qualifiedName());
  }

  @Test
  void rowOfFormDataIsAddressedByNumber() {
    // Репорт: `Элементы.Товары.ТекущаяСтрока` оставался Произвольным. Описание
    // расширения тип идентификатора не называет, но его называют сами данные формы:
    // ПолучитьИдентификатор() строки отдаёт Число, НайтиПоИдентификатору() его принимает.
    var rowMembers = typeRegistry.getMembers(
      typeRegistry.resolve("ДанныеФормыЭлементКоллекции").orElseThrow(), FileType.BSL);
    var identifier = rowMembers.stream()
      .filter(m -> m.matches("ПолучитьИдентификатор"))
      .findFirst()
      .orElseThrow();
    assertThat(identifier.signatures().get(0).returnTypes().refs())
      .extracting(TypeRef::qualifiedName)
      .containsExactly("Число");

    var table = "ТаблицаФормы.ТабличнаяЧасть";
    assertThat(qualifiedNames(member(table, MemberKind.PROPERTY, "ТекущаяСтрока")))
      .containsExactly("Число");
    var selectedRows = member(table, MemberKind.PROPERTY, "ВыделенныеСтроки");
    assertThat(qualifiedNames(selectedRows))
      .as("массив идентификаторов — сам массив остаётся Массивом")
      .containsExactly("Массив.Число");
    assertThat(typeRegistry.getDefaultElementTypes(selectedRows.returnTypes().refs().iterator().next()).refs())
      .as("а вот его элементы — числа")
      .extracting(TypeRef::qualifiedName)
      .containsExactly("Число");
  }

  @Test
  void sourceRecordKeyStaysOnlyOnRecordFormOfInformationRegister() {
    // `ИсходныйКлючЗаписи` объявлен у самой ДанныеФормыСтруктура и потому достаётся
    // по наследству любой форме. Смысл он имеет только у формы записи регистра
    // сведений: платформа кладёт туда ключ записи, открытой на изменение.
    assertThat(names(parametersOf(DOCUMENT_FORM)))
      .doesNotContain("ИсходныйКлючЗаписи");
    assertThat(names(parametersOf(
      "ФормаКлиентскогоПриложения.РегистрСведений.РегистрСведений1.Форма.ФормаЗаписи")))
      .contains("ИсходныйКлючЗаписи");
  }

  @Test
  void formGroupSeesBackColorFromItsExtension() {
    // Репорт: Элементы.<группа>.ЦветФона не резолвился. ЦветФона объявлен в
    // «Расширение группы формы для обычной группы», а не в самой ГруппаФормы.
    var itemsType = typeRegistry
      .resolve("ВсеЭлементыФормы.Документ.Документ1.Форма.ФормаСписка")
      .orElseThrow();
    var group = typeRegistry.getMembers(itemsType, FileType.BSL).stream()
      .filter(m -> m.matches("СписокКомпоновщикНастроекПользовательскиеНастройки"))
      .findFirst()
      .orElseThrow();

    var groupType = group.returnTypes().refs().iterator().next();
    assertThat(names(typeRegistry.getMembers(groupType, FileType.BSL)))
      .contains("ЦветФона", "Отображение", "Группировка")
      .contains("Имя", "Заголовок");
  }

  @Test
  void tableExtensionIsChosenByDisplayedData() {
    // У таблицы расширение определяется не видом элемента, а ПутьКДанным:
    // «Список» смотрит на реквизит-динамический список, «ТабличнаяЧасть1» —
    // на табличную часть объекта (путь Объект.ТабличнаяЧасть1).
    var dynamicList = itemMemberType("Документ.Документ1.Форма.ФормаСписка", "Список");
    assertThat(typeRegistry.extensionsOf(dynamicList))
      .as("таблица над динамическим списком получает списочное расширение")
      .extracting(TypeRef::qualifiedName)
      .contains("ТаблицаФормы.ДинамическийСписок");
    assertThat(names(typeRegistry.getMembers(dynamicList, FileType.BSL)))
      .as("собственные члены ТаблицаФормы")
      .contains("ТекущиеДанные", "Обновить")
      .as("члены расширения динамического списка")
      .contains("АвтоОбновление", "ПериодАвтоОбновления", "ВосстанавливатьТекущуюСтроку")
      .as("события расширения динамического списка")
      .contains("ПриПолученииДанныхНаСервере");

    var tabularSection = itemMemberType("Документ.Документ1.Форма.ФормаДокумента", "ТабличнаяЧасть1");
    assertThat(typeRegistry.extensionsOf(tabularSection))
      .as("таблица над табличной частью получает своё расширение, а не списочное")
      .extracting(TypeRef::qualifiedName)
      .contains("ТаблицаФормы.ТабличнаяЧасть");
    assertThat(names(typeRegistry.getMembers(tabularSection, FileType.BSL)))
      .contains("ОтборСтрок");
  }

  @Test
  void memberDeclaredWithoutTypeGetsTheTypePlatformNamesElsewhere() {
    // Репорт: Элементы.<таблица>.АвтоОтметкаНезаполненного показывалось без типа.
    // Блок «Тип» в синтакс-помощнике есть не у всякого свойства; у одноимённого
    // свойства поля ввода платформа объявляет Булево — его и проставляем.
    var table = itemMemberType("Документ.Документ1.Форма.ФормаДокумента", "ТабличнаяЧасть1");
    var members = typeRegistry.getMembers(table, FileType.BSL);

    var autoMark = members.stream().filter(m -> m.matches("АвтоОтметкаНезаполненного"))
      .findFirst().orElseThrow();
    assertThat(qualifiedNames(autoMark)).containsExactly("Булево");
    assertThat(autoMark.description())
      .as("платформенное описание доопределение типа не затирает")
      .contains("ОтметкаНезаполненного");

    // Второй случай того же дефекта: свойство и его тип названы одинаково.
    var behavior = members.stream()
      .filter(m -> m.matches("ПоведениеПриНедоступностиОсновногоСервера"))
      .findFirst().orElseThrow();
    assertThat(qualifiedNames(behavior))
      .containsExactly("ПоведениеПриНедоступностиОсновногоСервера");
  }

  @Test
  void everyTypelessMemberOfTheDictionaryIsStillDeclaredThatWay() {
    // Словарь доопределений сверяется с платформой целиком: и владелец, и тип
    // значения должны резолвиться, а само свойство — существовать и оставаться
    // без типа. Иначе запись либо промахнулась именем, либо платформа тип
    // объявила и доопределять больше нечего.
    FormPlatformTypes.TYPELESS_MEMBER_TYPES.forEach((ownerName, typeByMember) -> {
      var ownerRef = typeRegistry.resolve(ownerName).orElseThrow(
        () -> new AssertionError("не резолвится тип-владелец " + ownerName));
      var declared = typeRegistry.getMembers(ownerRef, FileType.BSL);
      typeByMember.forEach((memberName, typeName) -> {
        assertThat(typeRegistry.resolve(typeName))
          .as("тип значения %s (%s.%s)", typeName, ownerName, memberName)
          .isPresent();
        assertThat(declared).as("свойство %s.%s", ownerName, memberName)
          .anyMatch(m -> m.matches(memberName));
      });
    });
  }

  @Test
  void writeParametersOfDocumentFormAreTypedByThePlatformEnums() {
    // Тип у параметра объявлен как `Структура`, а состав назван прозой в его описании.
    // Типы значений платформа называет сама: у обычной формы те же два параметра
    // объявлены не структурой, а отдельными аргументами `ПередЗаписью` с этими типами.
    var writeParameters = parameterOf(DOCUMENT_FORM, MemberKind.EVENT, "ПередЗаписью", "ПараметрыЗаписи");

    assertThat(writeParameters.types().refs()).extracting(TypeRef::qualifiedName)
      .containsExactly("Структура");
    assertThat(fieldTypeNames(writeParameters, "РежимЗаписи"))
      .containsExactly("РежимЗаписиДокумента");
    assertThat(fieldTypeNames(writeParameters, "РежимПроведения"))
      .containsExactly("РежимПроведенияДокумента");
    assertThat(writeParameters.types().getAllFieldNames())
      .as("написание одно и по языку проекта: конфигурация фикстуры русская")
      .doesNotContain("WriteMode", "PostingMode");
  }

  @Test
  void writeParametersWithoutDeclaredTypeBecomeStructure() {
    // У форм задачи и бизнес-процесса блок «Тип» у параметра отсутствует вовсе — тот же
    // дефект синтакс-помощника, что у бестиповых свойств элементов, только на параметре.
    // Состав при этом назван: «система устанавливает его значение в Истина».
    var taskParameters = parameterOf("Расширение формы клиентского приложения для задачи",
      MemberKind.EVENT, "ПередЗаписью", "ПараметрыЗаписи");
    assertThat(taskParameters.types().refs()).extracting(TypeRef::qualifiedName)
      .containsExactly("Структура");
    assertThat(fieldTypeNames(taskParameters, "ВыполнитьЗадачу")).containsExactly("Булево");

    var processParameters = parameterOf("Расширение формы клиентского приложения для бизнес-процесса",
      MemberKind.EVENT, "ПередЗаписью", "ПараметрыЗаписи");
    assertThat(fieldTypeNames(processParameters, "Старт")).containsExactly("Булево");
  }

  @Test
  void compositionIsAttachedToTheParameterRatherThanToTheEvent() {
    // Платформа кладёт одну и ту же структуру во все события записи и принимает её же
    // методом `Записать` — состав закреплён за параметром, а не за отдельным событием.
    for (var memberName : List.of("ПередЗаписьюНаСервере", "ПриЗаписиНаСервере",
      "ПослеЗаписиНаСервере", "ПослеЗаписи")) {
      assertThat(parameterOf(DOCUMENT_FORM, MemberKind.EVENT, memberName, "ПараметрыЗаписи")
        .types().getAllFieldNames())
        .as("состав у %s", memberName)
        .contains("РежимЗаписи", "РежимПроведения");
    }
    assertThat(parameterOf(DOCUMENT_FORM, MemberKind.METHOD, "Записать", "ПараметрыЗаписи")
      .types().getAllFieldNames())
      .contains("РежимЗаписи", "РежимПроведения");
  }

  @Test
  void choiceDataParametersOfInputFieldCarryTheirDocumentedKeys() {
    var parameters = parameterOf("Расширение поля формы для поля ввода",
      MemberKind.EVENT, "АвтоПодбор", "ПараметрыПолученияДанных");

    assertThat(fieldTypeNames(parameters, "СтрокаПоиска"))
      .as("быстрый выбор приходит Неопределено")
      .containsExactly("Строка", "Неопределено");
    assertThat(fieldTypeNames(parameters, "СпособПоискаСтроки"))
      .containsExactly("СпособПоискаСтрокиПриВводеПоСтроке");
    assertThat(parameters.types().getAllFieldNames())
      .contains("Отбор", "ВыборГруппИЭлементов", "ПолнотекстовыйПоиск", "РежимПолученияДанныхВыбора");
  }

  @Test
  void everyPredefinedStructureParameterIsStillDeclaredThatWay() {
    // Словарь состава сверяется с платформой целиком: тип-владелец должен резолвиться,
    // параметр с таким именем — существовать, а типы значений ключей — быть известными
    // типами. Иначе запись либо промахнулась именем, либо описывает уже несуществующий API.
    for (var parameter : FormPlatformTypes.PREDEFINED_STRUCTURE_PARAMETERS) {
      var ownerRef = typeRegistry.resolve(parameter.ownerTypeName()).orElseThrow(
        () -> new AssertionError("не резолвится тип-владелец " + parameter.ownerTypeName()));
      assertThat(typeRegistry.getMembers(ownerRef, FileType.BSL))
        .as("параметр %s у членов %s", parameter.parameterName(), parameter.ownerTypeName())
        .anyMatch(member -> member.signatures().stream()
          .flatMap(signature -> signature.parameters().stream())
          .anyMatch(declared -> declared.matches(parameter.parameterName())));
      for (var field : parameter.fields()) {
        for (var typeName : field.typeNames()) {
          assertThat(typeRegistry.resolve(typeName))
            .as("тип значения %s (%s.%s)", typeName, parameter.parameterName(), field.name().ru())
            .isPresent();
        }
      }
    }
  }

  /** Параметр члена платформенного типа по имени; падает, если такого нет. */
  private ParameterDescriptor parameterOf(String typeName, MemberKind kind, String memberName,
                                          String parameterName) {
    var found = member(typeName, kind, memberName);
    assertThat(found).as("член %s.%s", typeName, memberName).isNotNull();
    return found.signatures().stream()
      .flatMap(signature -> signature.parameters().stream())
      .filter(declared -> declared.matches(parameterName))
      .findFirst()
      .orElseThrow(() -> new AssertionError(
        "нет параметра " + parameterName + " у " + typeName + "." + memberName));
  }

  /** Имена типов значения ключа структуры, прикреплённой к параметру. */
  private static List<String> fieldTypeNames(ParameterDescriptor parameter, String fieldName) {
    return parameter.types().getFieldTypes(fieldName).refs().stream()
      .map(TypeRef::qualifiedName)
      .toList();
  }

  /** Тип элемента формы по его имени в коллекции элементов. */
  private TypeRef itemMemberType(String formMdoRef, String itemName) {
    var itemsType = typeRegistry.resolve("ВсеЭлементыФормы." + formMdoRef).orElseThrow();
    return typeRegistry.getMembers(itemsType, FileType.BSL).stream()
      .filter(m -> m.matches(itemName))
      .findFirst()
      .orElseThrow()
      .returnTypes().refs().iterator().next();
  }

  @Test
  void catalogFormGetsCatalogSpecificParameters() {
    // ЭтоГруппа есть только у «Расширение справочника» — если бы форма справочника
    // цеплялась к общему «Расширение объектов», параметр бы потерялся.
    assertThat(names(parametersOf(CATALOG_ITEM_FORM))).contains("ЭтоГруппа");
  }

  private Collection<MemberDescriptor> parametersOf(String formTypeName) {
    var parameters = member(formTypeName, MemberKind.PROPERTY, "Параметры");
    assertThat(parameters).as("свойство Параметры у %s", formTypeName).isNotNull();
    var structureRef = parameters.returnTypes().refs().iterator().next();
    return typeRegistry.getMembers(structureRef, FileType.BSL);
  }

  private MemberDescriptor parameter(String formTypeName, String parameterName) {
    var found = parametersOf(formTypeName).stream()
      .filter(m -> m.matches(parameterName))
      .findFirst()
      .orElse(null);
    assertThat(found).as("параметр %s у %s", parameterName, formTypeName).isNotNull();
    return found;
  }

  private MemberDescriptor member(String typeName, MemberKind kind, String memberName) {
    return typeRegistry.getMembers(typeRegistry.resolve(typeName).orElseThrow(), FileType.BSL).stream()
      .filter(m -> m.kind() == kind && m.matches(memberName))
      .findFirst()
      .orElse(null);
  }

  private static List<String> names(Collection<MemberDescriptor> members) {
    return members.stream().map(MemberDescriptor::name).toList();
  }

  private static List<String> qualifiedNames(MemberDescriptor member) {
    return member.returnTypes().refs().stream().map(TypeRef::qualifiedName).toList();
  }
}
