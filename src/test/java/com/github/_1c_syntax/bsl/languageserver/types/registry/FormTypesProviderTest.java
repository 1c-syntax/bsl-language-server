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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.mdo.storage.form.FormElementType;
import com.github._1c_syntax.bsl.mdo.support.DefaultFormKind;
import com.github._1c_syntax.bsl.types.MDOType;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Типы форм собираются из двух источников: платформенная часть — из
 * синтакс-помощника (в тестах — JSON-fallback, HBK выключен), конфигурационная —
 * из {@code Form.xml} через mdclasses.
 */
@CleanupContextBeforeClassAndAfterClass
class FormTypesProviderTest extends AbstractServerContextAwareTest {

  private static final String DOCUMENT_FORM = "ФормаКлиентскогоПриложения.Документ.Документ1.Форма.ФормаДокумента";
  private static final String CATALOG_ITEM_FORM =
    "ФормаКлиентскогоПриложения.Справочник.Справочник1.Форма.ФормаЭлемента";
  private static final String DOCUMENT_LIST_FORM = "ФормаКлиентскогоПриложения.Документ.Документ1.Форма.ФормаСписка";

  @Autowired
  private ConfigurationTypesProvider provider;

  @Autowired
  private TypeRegistry typeRegistry;

  @Autowired
  private TypeService typeService;

  @BeforeEach
  void setUp() {
    // Тесты read-only: только resolve/getMembers, состояние реестра не меняют.
    initServerContextOnce(Absolute.path(PATH_TO_METADATA));
    context.getConfiguration();
    provider.tryRegister();
  }

  @Test
  void registersTypePerFormWithRuAndEnAliases() {
    var ru = typeRegistry.resolve(DOCUMENT_FORM);
    var en = typeRegistry.resolve("ClientApplicationForm.Document.Документ1.Form.ФормаДокумента");

    assertThat(ru).isPresent();
    assertThat(en).isPresent();
    assertThat(en).contains(ru.orElseThrow());
    assertThat(typeService.displayName(ru.get(), Language.RU)).isEqualTo(DOCUMENT_FORM);
    assertThat(typeService.displayName(ru.get(), Language.EN))
      .isEqualTo("ClientApplicationForm.Document.Документ1.Form.ФормаДокумента");
  }

  @Test
  void formTypeInheritsPlatformMembers() {
    var members = membersOf(DOCUMENT_FORM);

    assertThat(names(members))
      .as("тип формы наследует члены ФормаКлиентскогоПриложения")
      .contains("Модифицированность", "ИмяФормы", "ЗначениеВРеквизитФормы");
  }

  @Test
  void formAttributesBecomeTypedProperties() {
    var objectAttribute = member(DOCUMENT_FORM, MemberKind.PROPERTY, "Объект");

    assertThat(objectAttribute).isNotNull();
    assertThat(qualifiedNames(objectAttribute))
      .as("объектный реквизит на управляемой форме становится данными формы")
      .containsExactly("ДанныеФормыСтруктура.ДокументОбъект.Документ1");

    // Реквизит формы списка объявлен динамическим списком.
    var listAttribute = member(DOCUMENT_LIST_FORM, MemberKind.PROPERTY, "Список");
    assertThat(listAttribute).isNotNull();
    assertThat(qualifiedNames(listAttribute))
      .as("динамический список на форму переносится как есть")
      .containsExactly("ДинамическийСписок");
  }

  @Test
  void formDataTypeIsDisplayedAsPlatformType() {
    var objectAttribute = member(DOCUMENT_FORM, MemberKind.PROPERTY, "Объект");
    assertThat(objectAttribute).isNotNull();

    var objectType = objectAttribute.returnTypes().refs().iterator().next();
    assertThat(typeService.displayName(objectType, Language.RU))
      .as("синтетическое имя наружу не течёт — показывается реальный тип значения")
      .isEqualTo("ДанныеФормыСтруктура");
  }

  @Test
  void attributeTypeChainReachesOwnerObjectAttributes() {
    var objectAttribute = member(DOCUMENT_FORM, MemberKind.PROPERTY, "Объект");
    assertThat(objectAttribute).isNotNull();

    var objectType = objectAttribute.returnTypes().refs().iterator().next();
    assertThat(names(typeRegistry.getMembers(objectType, FileType.BSL)))
      .as("через реквизит основных данных доступны реквизиты и табличные части объекта")
      .contains("Реквизит1", "ТабличнаяЧасть1");
  }

  @Test
  void formDataKeepsPropertiesAndDropsObjectMethods() {
    var objectAttribute = member(DOCUMENT_FORM, MemberKind.PROPERTY, "Объект");
    assertThat(objectAttribute).isNotNull();
    var objectType = objectAttribute.returnTypes().refs().iterator().next();

    var declaredRef = typeRegistry.resolve("ДокументОбъект.Документ1").orElseThrow();
    var objectMethods = typeRegistry.getMembers(declaredRef, FileType.BSL).stream()
      .filter(member -> member.kind() == MemberKind.METHOD)
      .map(MemberDescriptor::name)
      .toList();

    var dataMembers = names(typeRegistry.getMembers(objectType, FileType.BSL));
    assertThat(dataMembers)
      .as("методы прикладного объекта на клиенте недоступны: за реквизитом только данные")
      .doesNotContainAnyElementsOf(objectMethods);
    assertThat(dataMembers)
      .as("платформенные методы самих данных формы остаются")
      .contains("Свойство");
  }

  @Test
  void objectInfrastructurePropertiesStayOutsideFormData() {
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
  void tabularSectionBecomesFormDataCollection() {
    var objectAttribute = member(DOCUMENT_FORM, MemberKind.PROPERTY, "Объект");
    assertThat(objectAttribute).isNotNull();
    var objectType = objectAttribute.returnTypes().refs().iterator().next();

    var tabularSection = typeRegistry.getMembers(objectType, FileType.BSL).stream()
      .filter(member -> member.matches("ТабличнаяЧасть1"))
      .findFirst()
      .orElseThrow();
    var tabularSectionType = tabularSection.returnTypes().refs().iterator().next();

    assertThat(tabularSectionType.qualifiedName())
      .as("на форме за табличной частью стоит коллекция данных формы")
      .isEqualTo("ДанныеФормыКоллекция.ДокументТабличнаяЧасть.Документ1.ТабличнаяЧасть1");
    assertThat(typeService.displayName(tabularSectionType, Language.RU))
      .isEqualTo("ДанныеФормыКоллекция");
    assertThat(names(typeRegistry.getMembers(tabularSectionType, FileType.BSL)))
      .as("методы коллекции данных формы")
      .contains("Добавить", "Количество")
      .as("колонки — у строки, а не у коллекции: Объект.Товары.Цена на форме не работает")
      .doesNotContain("Реквизит1");
  }

  @Test
  void formDataCollectionIteratesOverRowsWithColumns() {
    var objectAttribute = member(DOCUMENT_FORM, MemberKind.PROPERTY, "Объект");
    assertThat(objectAttribute).isNotNull();
    var objectType = objectAttribute.returnTypes().refs().iterator().next();
    var tabularSectionType = typeRegistry.getMembers(objectType, FileType.BSL).stream()
      .filter(member -> member.matches("ТабличнаяЧасть1"))
      .findFirst()
      .orElseThrow()
      .returnTypes().refs().iterator().next();

    assertThat(typeRegistry.supportsForEach(tabularSectionType, FileType.BSL))
      .as("коллекция данных формы обходится Для Каждого")
      .isTrue();

    var elementTypes = typeRegistry.getDefaultElementTypes(tabularSectionType);
    assertThat(elementTypes.refs()).extracting(TypeRef::qualifiedName)
      .as("элемент — строка этой же табличной части, а не обобщённая строка коллекции")
      .containsExactly("ДанныеФормыЭлементКоллекции.ДокументТабличнаяЧасть.Документ1.ТабличнаяЧасть1");
    assertThat(names(typeRegistry.getMembers(elementTypes.refs().iterator().next(), FileType.BSL)))
      .as("у строки видны колонки табличной части")
      .contains("Реквизит1");
  }

  @Test
  void collectionMethodsReturnItsOwnRow() {
    var objectAttribute = member(DOCUMENT_FORM, MemberKind.PROPERTY, "Объект");
    assertThat(objectAttribute).isNotNull();
    var objectType = objectAttribute.returnTypes().refs().iterator().next();
    var tabularSectionType = typeRegistry.getMembers(objectType, FileType.BSL).stream()
      .filter(member -> member.matches("ТабличнаяЧасть1"))
      .findFirst()
      .orElseThrow()
      .returnTypes().refs().iterator().next();

    var add = typeRegistry.getMembers(tabularSectionType, FileType.BSL).stream()
      .filter(member -> member.matches("Добавить"))
      .findFirst()
      .orElseThrow();

    assertThat(add.returnTypes().refs()).extracting(TypeRef::qualifiedName)
      .as("Объект.Товары.Добавить() должен давать строку этой таблицы, а не обобщённую")
      .containsExactly("ДанныеФормыЭлементКоллекции.ДокументТабличнаяЧасть.Документ1.ТабличнаяЧасть1");
    assertThat(names(typeRegistry.getMembers(add.returnTypes().refs().iterator().next(), FileType.BSL)))
      .contains("Реквизит1");

    var findRows = typeRegistry.getMembers(tabularSectionType, FileType.BSL).stream()
      .filter(member -> member.matches("НайтиСтроки"))
      .findFirst()
      .orElseThrow();
    assertThat(findRows.returnTypes().refs()).extracting(TypeRef::qualifiedName)
      .containsExactly("Массив");
    assertThat(findRows.returnTypes().getElementTypes().refs()).extracting(TypeRef::qualifiedName)
      .as("НайтиСтроки возвращает массив строк этой таблицы, а не нетипизированный")
      .containsExactly("ДанныеФормыЭлементКоллекции.ДокументТабличнаяЧасть.Документ1.ТабличнаяЧасть1");

    var unload = typeRegistry.getMembers(tabularSectionType, FileType.BSL).stream()
      .filter(member -> member.matches("Выгрузить"))
      .findFirst()
      .orElseThrow();
    var unloadedRow = unload.returnTypes().getElementTypes();
    assertThat(unloadedRow.refs()).extracting(TypeRef::qualifiedName)
      .containsExactly("СтрокаТаблицыЗначений");
    assertThat(unloadedRow.getLocalFields(unloadedRow.refs().iterator().next()))
      .as("у выгруженной таблицы значений колонки выгружаемой коллекции")
      .containsKey("Реквизит1");
  }

  @Test
  void objectFamiliesMapToFormDataKinds() {
    assertThat(FormPlatformTypes.formDataKindOf("ДокументОбъект.Документ1"))
      .isEqualTo(FormDataKind.STRUCTURE);
    assertThat(FormPlatformTypes.formDataKindOf("КонстантаМенеджерЗначения.Константа1"))
      .isEqualTo(FormDataKind.STRUCTURE);
    assertThat(FormPlatformTypes.formDataKindOf("РегистрСведенийНаборЗаписей.Регистр1"))
      .isEqualTo(FormDataKind.STRUCTURE_WITH_COLLECTION);
    assertThat(FormPlatformTypes.formDataKindOf("РегистрБухгалтерииНаборЗаписей.Регистр1"))
      .isEqualTo(FormDataKind.STRUCTURE_WITH_COLLECTION);
    assertThat(FormPlatformTypes.formDataKindOf("ТаблицаЗначений"))
      .isEqualTo(FormDataKind.COLLECTION);
    assertThat(FormPlatformTypes.formDataKindOf("ДеревоЗначений"))
      .isEqualTo(FormDataKind.TREE);
  }

  @Test
  void transferableTypesAreNotConverted() {
    assertThat(FormPlatformTypes.formDataKindOf("ДокументСсылка.Документ1"))
      .as("ссылки переносятся на форму как есть")
      .isNull();
    assertThat(FormPlatformTypes.formDataKindOf("СписокЗначений")).isNull();
    assertThat(FormPlatformTypes.formDataKindOf("ДинамическийСписок")).isNull();
    assertThat(FormPlatformTypes.formDataKindOf("Строка")).isNull();
  }

  @Test
  void itemsPropertyPointsToPerFormCollection() {
    var items = member(DOCUMENT_FORM, MemberKind.PROPERTY, "Элементы");

    assertThat(items).isNotNull();
    assertThat(qualifiedNames(items))
      .as("Элементы специализированы по форме, а не обобщённый ВсеЭлементыФормы")
      .containsExactly("ВсеЭлементыФормы.Документ.Документ1.Форма.ФормаДокумента");
  }

  @Test
  void formItemsAreTypedByTheirElementKind() {
    var itemsType = typeRegistry
      .resolve("ВсеЭлементыФормы.Документ.Документ1.Форма.ФормаДокумента")
      .orElseThrow();
    var items = typeRegistry.getMembers(itemsType, FileType.BSL);

    assertThat(names(items))
      .as("в коллекцию попадают все элементы формы, включая вложенные в таблицу")
      .contains("Номер", "Дата", "ТабличнаяЧасть1", "ТабличнаяЧасть1Реквизит1");

    assertThat(qualifiedNames(find(items, MemberKind.PROPERTY, "Номер")))
      .as("ПолеВвода на рантайме — ПолеФормы")
      .containsExactly("ПолеФормы");
    assertThat(qualifiedNames(find(items, MemberKind.PROPERTY, "ТабличнаяЧасть1")))
      .as("Таблица на рантайме — ТаблицаФормы")
      .containsExactly("ТаблицаФормы");

    assertThat(names(items))
      .as("платформенные методы коллекции наследуются от ВсеЭлементыФормы")
      .contains("Найти", "Количество");
    assertThat(typeRegistry.supportsForEach(itemsType, FileType.BSL))
      .as("специализация коллекции остаётся обходимой Для Каждого")
      .isTrue();
  }

  @Test
  void serviceItemsAreTypedLikeAnyOtherItem() {
    // Автоматическая командная панель и дополнения таблицы объявлены в Form.xml не
    // внутри <ChildItems>, а соседними тегами. На рантайме это такие же элементы
    // коллекции, доступные по имени, поэтому и типизируются как остальные.
    var itemsType = typeRegistry
      .resolve("ВсеЭлементыФормы.Документ.Документ1.Форма.ФормаДокумента")
      .orElseThrow();
    var items = typeRegistry.getMembers(itemsType, FileType.BSL);

    assertThat(qualifiedNames(find(items, MemberKind.PROPERTY, "ТабличнаяЧасть1КоманднаяПанель")))
      .as("командная панель на рантайме — группа формы")
      .containsExactly("ГруппаФормы");
    assertThat(qualifiedNames(find(items, MemberKind.PROPERTY, "ТабличнаяЧасть1СтрокаПоиска")))
      .containsExactly("ДополнениеЭлементаФормы");
    assertThat(qualifiedNames(find(items, MemberKind.PROPERTY, "ТабличнаяЧасть1СостояниеПросмотра")))
      .containsExactly("ДополнениеЭлементаФормы");
    assertThat(qualifiedNames(find(items, MemberKind.PROPERTY, "ТабличнаяЧасть1УправлениеПоиском")))
      .containsExactly("ДополнениеЭлементаФормы");
  }

  @Test
  void selfPropertiesPointToTheFormItself() {
    var formRef = typeRegistry.resolve(DOCUMENT_FORM).orElseThrow();

    for (var name : List.of("ЭтотОбъект", "ЭтаФорма")) {
      var self = member(DOCUMENT_FORM, MemberKind.PROPERTY, name);
      assertThat(self).as(name).isNotNull();
      assertThat(self.returnTypes().refs())
        .as("%s ведёт на тип конкретной формы, а не на ФормаКлиентскогоПриложения", name)
        .containsExactly(formRef);
    }
  }

  @Test
  void declaredHandlersBecomeEventsNamedAfterTheHandler() {
    // В Form.xml событие OnWriteAtServer объявлено обработчиком ПриЗаписиНаСервере.
    // контракт события берётся из расширения формы документа.
    var handler = member(DOCUMENT_FORM, MemberKind.EVENT, "ПриЗаписиНаСервере");

    assertThat(handler).isNotNull();
    assertThat(handler.signatures()).isNotEmpty();
    assertThat(handler.signatures().get(0).parameters())
      .extracting(p -> p.bilingualName().ru())
      .containsExactly("Отказ", "ТекущийОбъект", "ПараметрыЗаписи");
  }

  @Test
  void handlersDeclaredOnElementsBecomeEventsOfTheForm() {
    // <InputField name="Реквизит1"><Events><Event name="OnChange">Реквизит1ПриИзменении</Event>.
    // Обработчик объявлен у элемента, а живёт в модуле формы — значит и событием он
    // должен стать у формы. Контракт события приходит из синтакс-помощника
    // (см. FormParametersHbkTest), но сам факт «это обработчик» от него не зависит.
    assertThat(member(DOCUMENT_FORM, MemberKind.EVENT, "Реквизит1ПриИзменении")).isNotNull();
  }

  @Test
  void handlerOfBaseFormEventTakesContractFromTheFormType() {
    // <Event name="OnCreateAtServer">ПриСозданииНаСервере</Event> — событие
    // самой ФормаКлиентскогоПриложения, расширение тут ни при чём.
    var handler = member(CATALOG_ITEM_FORM, MemberKind.EVENT, "ПриСозданииНаСервере");

    assertThat(handler).isNotNull();
    assertThat(handler.signatures().get(0).parameters())
      .extracting(p -> p.bilingualName().ru())
      .containsExactly("Отказ", "СтандартнаяОбработка");
  }

  @Test
  void catalogFormPrefersCatalogExtensionAndFallsBackToGenericObjectOne() {
    // Первым кандидатом идёт «Расширение справочника» (типизирует параметр Ключ и
    // добавляет ЭтоГруппа), запасным — общее «Расширение объектов»: набор событий
    // у них один, и в JSON-фолбэке описано только общее.
    assertThat(FormPlatformTypes.extensionTypeNames("СправочникОбъект.Справочник1",
      FormKind.MANAGED))
      .containsExactly("Расширение формы клиентского приложения для справочника",
        "Расширение формы клиентского приложения для объектов");

    // У отчёта события свои — подмена объектным расширением была бы враньём.
    assertThat(FormPlatformTypes.extensionTypeNames("ОтчетОбъект.Отчет1",
      FormKind.MANAGED))
      .containsExactly("Расширение формы клиентского приложения для отчета");

    // Списочная форма членов уровня формы не получает, но параметры у неё есть.
    assertThat(FormPlatformTypes.extensionTypeNames("ДинамическийСписок",
      FormKind.MANAGED)).isEmpty();
    assertThat(FormPlatformTypes.parameterExtensionTypeNames("ДинамическийСписок",
      FormKind.MANAGED))
      .containsExactly("Расширение формы клиентского приложения для динамического списка");
  }

  @Test
  void ordinaryFormExtensionIsChosenByItsRoleAtTheOwner() {
    // У обычной формы основной реквизит не достать — mdclasses не отдаёт её состав.
    // Зато известно, какой основной формой объекта она назначена, а это тот же признак.
    assertThat(FormPlatformTypes.extensionTypeNames("СправочникСписок.Справочник1",
      FormKind.ORDINARY))
      .as("по реквизиту расширение обычной формы больше не выбирается")
      .isEmpty();

    assertThat(FormPlatformTypes.ordinaryExtensionTypeName(MDOType.CATALOG, DefaultFormKind.OBJECT_FORM))
      .isEqualTo("Расширение формы элемента справочника");
    assertThat(FormPlatformTypes.ordinaryExtensionTypeName(MDOType.CATALOG, DefaultFormKind.LIST_FORM))
      .isEqualTo("Расширение формы списка справочника");
    assertThat(FormPlatformTypes.ordinaryExtensionTypeName(MDOType.DOCUMENT, DefaultFormKind.OBJECT_FORM))
      .isEqualTo("Расширение формы документа");
    // У журнала и критерия отбора форма ровно одна, и в mdclasses она приходит не как
    // форма списка, а как основная — designer-свойство у них называется <DefaultForm>.
    assertThat(FormPlatformTypes.ordinaryExtensionTypeName(
      MDOType.DOCUMENT_JOURNAL, DefaultFormKind.DEFAULT_FORM))
      .isEqualTo("Расширение формы журнала документов");
    assertThat(FormPlatformTypes.ordinaryExtensionTypeName(
      MDOType.FILTER_CRITERION, DefaultFormKind.DEFAULT_FORM))
      .isEqualTo("Расширение формы критерия отбора");
    assertThat(FormPlatformTypes.ordinaryExtensionTypeName(
      MDOType.INFORMATION_REGISTER, DefaultFormKind.RECORD_FORM))
      .isEqualTo("Расширение формы записи регистра сведений");
  }

  @Test
  void choiceFormIsExtendedAsListAndFolderFormAsItem() {
    // Форму выбора и выбора группы платформа расширяет так же, как форму списка,
    // а форму группы — как форму одного элемента.
    assertThat(FormPlatformTypes.ordinaryExtensionTypeName(MDOType.CATALOG, DefaultFormKind.CHOICE_FORM))
      .isEqualTo("Расширение формы списка справочника");
    assertThat(FormPlatformTypes.ordinaryExtensionTypeName(
      MDOType.CATALOG, DefaultFormKind.FOLDER_CHOICE_FORM))
      .isEqualTo("Расширение формы списка справочника");
    assertThat(FormPlatformTypes.ordinaryExtensionTypeName(MDOType.CATALOG, DefaultFormKind.FOLDER_FORM))
      .isEqualTo("Расширение формы элемента справочника");
  }

  @Test
  void tableExtensionIsChosenByTheEndOfDataPathNotItsRoot() {
    // `Компоновщик.Настройки.Выбор`: значимый тип стоит в конце пути, а не в корне.
    // Раньше решение принималось по корню плюс признаку «путь вложенный», и такая
    // таблица получала расширение табличных частей — то есть неверное.
    var table = itemMemberType("Документ.Документ1.Форма.ФормаДокумента", "ВыбранныеПоля");

    assertThat(table.qualifiedName()).isEqualTo("ТаблицаФормы.ВыбранныеПоляКомпоновкиДанных");
  }

  @Test
  void dynamicListTableHasItsOwnRowType() {
    // Строка динамического списка — единственная, у которой платформа даёт расширение
    // («Расширение данных строки для динамического списка»), поэтому у неё свой тип,
    // а не обобщённый ДанныеФормыЭлементКоллекции.
    var currentData = member("ТаблицаФормы.ДинамическийСписок", MemberKind.PROPERTY, "ТекущиеДанные");

    assertThat(currentData).isNotNull();
    assertThat(qualifiedNames(currentData))
      .containsExactly("ДанныеФормыЭлементКоллекции.ДинамическийСписок");
    assertThat(typeService.displayName(
      currentData.returnTypes().refs().iterator().next(), Language.RU))
      .as("синтетическое имя наружу не течёт")
      .isEqualTo("ДанныеФормыЭлементКоллекции");
  }

  @Test
  void rowTypeIsRegisteredOnlyForDataKindsThatHaveARowExtension() {
    // У прочих видов данных своей специфики у строки нет — расширения тоже, поэтому
    // свой тип строки им не заводится.
    assertThat(typeRegistry.resolve("ДанныеФормыЭлементКоллекции.ДинамическийСписок")).isPresent();
    assertThat(typeRegistry.resolve("ДанныеФормыЭлементКоллекции.ТаблицаЗначений")).isEmpty();
  }

  @Test
  void itemKindTypeIsNamedAfterTheEnumValueAssignedToKind() {
    // На этом инвариантe держится уточнение по `Элемент.Вид = ВидГруппыФормы.ОбычнаяГруппа`:
    // суффикс типа вида совпадает с именем значения перечисления, поэтому имя
    // специализированного типа собирается напрямую, без таблицы соответствий.
    assertThat(FormPlatformTypes.itemKindSuffix(FormElementType.USUAL_GROUP))
      .isEqualTo("ОбычнаяГруппа");
    assertThat(typeRegistry.resolve("ГруппаФормы.ОбычнаяГруппа"))
      .as("тип вида зарегистрирован под этим именем")
      .isPresent();
  }

  @Test
  void everyElementKindOfTheModelHasItsExtension() {
    // Пропуск вида означает молча потерянные свойства элемента: у поля дендрограммы
    // расширение было бы, но константы в mdclasses не существовало (mdclasses#665).
    var withoutExtension = java.util.Arrays.stream(FormElementType.values())
      .filter(kind -> kind != FormElementType.UNKNOWN && kind != FormElementType.TABLE)
      .filter(kind -> FormPlatformTypes.itemTypeName(kind) != null)
      .filter(kind -> FormPlatformTypes.itemExtensionTypeName(kind) == null)
      .map(FormPlatformTypes::itemKindSuffix)
      .toList();

    assertThat(withoutExtension)
      .as("у кнопок расширения по виду нет — вся специфика в самой КнопкаФормы; "
        + "у контекстного меню и расширенной подсказки — потому что это не вид, а часть "
        + "чужого элемента: обычные группа и декорация")
      .containsExactlyInAnyOrder("ОбычнаяКнопка", "Гиперссылка",
        "КнопкаКоманднойПанели", "ГиперссылкаКоманднойПанели",
        "КонтекстноеМеню", "РасширеннаяПодсказка");
  }

  @Test
  void unknownOwnerKindHasNoOrdinaryExtension() {
    assertThat(FormPlatformTypes.ordinaryExtensionTypeName(MDOType.SUBSYSTEM, DefaultFormKind.LIST_FORM))
      .as("у подсистемы форм нет — расширения быть не может")
      .isNull();
    assertThat(FormPlatformTypes.ordinaryExtensionTypeName(MDOType.ENUM, DefaultFormKind.OBJECT_FORM))
      .as("у перечисления форма элемента не бывает")
      .isNull();
  }

  @Test
  void settingsComposerFormIsMappedByItsMainAttribute() {
    // Форма настроек компоновки данных: основной реквизит — сам компоновщик,
    // расширение есть только у управляемого варианта.
    assertThat(FormPlatformTypes.extensionTypeNames("КомпоновщикНастроекКомпоновкиДанных",
      FormKind.MANAGED))
      .containsExactly("Расширение формы клиентского приложения для компоновщика настроек");
    assertThat(FormPlatformTypes.extensionTypeNames("КомпоновщикНастроекКомпоновкиДанных",
      FormKind.ORDINARY)).isEmpty();
  }

  @Test
  void catalogFormStillGetsWriteEventsWithoutSyntaxHelper() {
    // Регресс на запасной вариант: без HBK «Расширение справочника» не резолвится,
    // и обработчик записи обязан подхватиться из общего объектного расширения.
    var handler = member(CATALOG_ITEM_FORM, MemberKind.EVENT, "ПриЗаписиНаСервере");
    assertThat(handler)
      .as("контракт ПриЗаписиНаСервере доступен и на JSON-фолбэке")
      .isNotNull();
  }

  @Test
  void listFormHasNoObjectExtensionEvents() {
    // Основной реквизит формы списка — ДинамическийСписок: событий работы с
    // объектом у такой формы нет, а событие самой формы (OnReopen) есть.
    assertThat(member(DOCUMENT_LIST_FORM, MemberKind.EVENT, "ПриПовторномОткрытии")).isNotNull();
    assertThat(names(membersOf(DOCUMENT_LIST_FORM)))
      .doesNotContain("ПриЗаписиНаСервере");
  }

  @Test
  void commandsOfTheFormAreInItsOwnCommandsCollection() {
    var commands = member(DOCUMENT_FORM, MemberKind.PROPERTY, "Команды");
    assertThat(commands).isNotNull();
    var commandsType = commands.returnTypes().refs().iterator().next();
    assertThat(commandsType.qualifiedName())
      .as("коллекция команд — своя у каждой формы, а не обобщённая КомандыФормы")
      .isEqualTo("КомандыФормы.Документ.Документ1.Форма.ФормаДокумента");

    var command = find(typeRegistry.getMembers(commandsType, FileType.BSL),
      MemberKind.PROPERTY, "ЗаполнитьПоОснованию");
    assertThat(command).isNotNull();
    assertThat(command.returnTypes().refs().iterator().next().qualifiedName()).isEqualTo("КомандаФормы");
  }

  @Test
  void commandActionIsAHandlerOfTheForm() {
    // <Command name="ЗаполнитьПоОснованию"><Action>ЗаполнитьПоОснованиюКоманда</Action>.
    // Обработчик команды объявлен действием, а не событием, но это та же процедура
    // модуля формы, которую зовёт платформа.
    assertThat(member(DOCUMENT_FORM, MemberKind.EVENT, "ЗаполнитьПоОснованиюКоманда")).isNotNull();
  }

  @Test
  void parametersDeclaredInTheFormAreInTheParametersStructure() {
    // Собственные параметры формы приходят из mdclasses и не зависят от синтакс-помощника:
    // структура `Параметры` появляется даже там, где стандартных параметров нет.
    var parameters = member(DOCUMENT_FORM, MemberKind.PROPERTY, "Параметры");
    assertThat(parameters).isNotNull();
    var parametersType = parameters.returnTypes().refs().iterator().next();
    var members = typeRegistry.getMembers(parametersType, FileType.BSL);

    assertThat(names(members)).contains("ПодобранныйТовар", "РежимПодбора");
    assertThat(find(members, MemberKind.PROPERTY, "ПодобранныйТовар")
      .returnTypes().refs().iterator().next().qualifiedName())
      .isEqualTo("СправочникСсылка.Справочник1");
  }

  @Test
  void valueTableAttributeCarriesItsColumns() {
    // Колонки реквизита-таблицы объявлены в самой форме, а не в прикладном типе:
    // это данные формы, которых в конфигурации нет вовсе.
    var table = member(DOCUMENT_FORM, MemberKind.PROPERTY, "ТаблицаПодбора");
    assertThat(table).isNotNull();
    var tableType = table.returnTypes().refs().iterator().next();
    assertThat(typeRegistry.displayName(tableType, Language.RU))
      .as("наружу показывается платформенное имя, а не синтетический суффикс")
      .isEqualTo("ДанныеФормыКоллекция");

    var rowType = typeRegistry.getDefaultElementTypes(tableType).refs().iterator().next();
    assertThat(names(typeRegistry.getMembers(rowType, FileType.BSL)))
      .as("колонки лежат в строке коллекции — как у зеркала табличной части")
      .contains("Номенклатура", "Количество");
    assertThat(find(typeRegistry.getMembers(rowType, FileType.BSL), MemberKind.PROPERTY, "Номенклатура")
      .returnTypes().refs().iterator().next().qualifiedName())
      .isEqualTo("СправочникСсылка.Справочник1");
  }

  /** Тип элемента формы по его имени в коллекции элементов. */
  private TypeRef itemMemberType(String formMdoRef, String itemName) {
    var itemsType = typeRegistry.resolve("ВсеЭлементыФормы." + formMdoRef).orElseThrow();
    return typeRegistry.getMembers(itemsType, FileType.BSL).stream()
      .filter(member -> member.matches(itemName))
      .findFirst()
      .orElseThrow()
      .returnTypes().refs().iterator().next();
  }

  private Collection<MemberDescriptor> membersOf(String typeName) {
    return typeRegistry.getMembers(typeRegistry.resolve(typeName).orElseThrow(), FileType.BSL);
  }

  private MemberDescriptor member(String typeName, MemberKind kind, String memberName) {
    return find(membersOf(typeName), kind, memberName);
  }

  private static MemberDescriptor find(Collection<MemberDescriptor> members, MemberKind kind, String name) {
    return members.stream()
      .filter(m -> m.kind() == kind && m.matches(name))
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
