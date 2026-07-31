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
    assertThat(dynamicList.qualifiedName()).isEqualTo("ТаблицаФормы.ДинамическийСписок");
    assertThat(names(typeRegistry.getMembers(dynamicList, FileType.BSL)))
      .as("собственные члены ТаблицаФормы")
      .contains("ТекущиеДанные", "Обновить")
      .as("члены расширения динамического списка")
      .contains("АвтоОбновление", "ПериодАвтоОбновления", "ВосстанавливатьТекущуюСтроку")
      .as("события расширения динамического списка")
      .contains("ПриПолученииДанныхНаСервере");

    var tabularSection = itemMemberType("Документ.Документ1.Форма.ФормаДокумента", "ТабличнаяЧасть1");
    assertThat(tabularSection.qualifiedName())
      .as("таблица над табличной частью получает своё расширение, а не списочное")
      .isEqualTo("ТаблицаФормы.ТабличнаяЧасть");
    assertThat(names(typeRegistry.getMembers(tabularSection, FileType.BSL)))
      .contains("ОтборСтрок");
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
