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
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Параметры обычной формы: платформа объявляет их через generic-плейсхолдер
 * ({@code ПараметрТекущаяСтрока} формы списка справочника — это
 * {@code СправочникСсылка.<Имя справочника>}), а конкретный тип зависит от объекта,
 * которому форма подчинена.
 * <p>
 * Отдельная конфигурация-фикстура: в общей все формы управляемые, а вид формы
 * задаётся в её описании и на лету не подменяется.
 */
@CleanupContextBeforeClassAndAfterClass
class OrdinaryFormParametersTest extends AbstractServerContextAwareTest {

  private static final String PATH_TO_ORDINARY_FORMS = "src/test/resources/metadata/ordinaryForms";

  private static final String CATALOG_LIST_FORM = "Форма.Справочник.Справочник1.Форма.ФормаСписка";
  private static final String CATALOG_CHOICE_FORM = "Форма.Справочник.Справочник1.Форма.ФормаВыбора";
  private static final String CATALOG_ITEM_FORM = "Форма.Справочник.Справочник1.Форма.ФормаЭлемента";
  private static final String REGISTER_LIST_FORM =
    "Форма.РегистрСведений.РегистрСведений1.Форма.ФормаСписка";
  private static final String JOURNAL_LIST_FORM =
    "Форма.ЖурналДокументов.ЖурналДокументов1.Форма.ФормаСписка";
  private static final String EMPTY_JOURNAL_LIST_FORM =
    "Форма.ЖурналДокументов.ЖурналДокументов2.Форма.ФормаСписка";

  private static final String CURRENT_ROW = "ПараметрТекущаяСтрока";

  @Autowired
  private ConfigurationTypesProvider provider;

  @Autowired
  private TypeRegistry typeRegistry;

  @BeforeEach
  void setUp() {
    initServerContextOnce(Absolute.path(PATH_TO_ORDINARY_FORMS));
    context.getConfiguration();
    provider.tryRegister();
  }

  @Test
  void currentRowOfReferenceObjectListIsItsRef() {
    assertThat(typeNames(CATALOG_LIST_FORM, CURRENT_ROW))
      .containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  void choiceFormIsExtendedLikeTheListOne() {
    // Расширения «формы выбора» у платформы нет — форму выбора расширяет списочное,
    // поэтому и текущая строка у неё типизируется так же.
    assertThat(typeNames(CATALOG_CHOICE_FORM, CURRENT_ROW))
      .containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  void currentRowOfRegisterListIsItsRecordKey() {
    // У регистра ссылок нет: строка списка — ключ записи. Никакой таблицы
    // соответствий для этого не нужно, тип берётся из объявления платформы.
    assertThat(typeNames(REGISTER_LIST_FORM, CURRENT_ROW))
      .containsExactly("РегистрСведенийКлючЗаписи.РегистрСведений1");
  }

  @Test
  void copyingObjectParameterIsTypedByTheOwner() {
    assertThat(typeNames(CATALOG_ITEM_FORM, "ПараметрОбъектКопирования"))
      .containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  void currentRowOfDocumentJournalIsUnionOfItsDocuments() {
    // Собственного ссылочного типа у журнала нет: платформа объявляет строку как
    // ДокументСсылка.<Имя документа>, а какие это документы — знает <RegisteredDocuments>.
    assertThat(typeNames(JOURNAL_LIST_FORM, CURRENT_ROW))
      .containsExactly("ДокументСсылка.Документ1");
  }

  @Test
  void currentRowOfEmptyDocumentJournalStaysGeneric() {
    // В журнал не зарегистрировано ни одного документа — подставлять нечего,
    // и параметр остаётся обобщённым, а не получает выдуманный тип.
    assertThat(typeNames(EMPTY_JOURNAL_LIST_FORM, CURRENT_ROW))
      .containsExactly("ДокументСсылка.<Имя документа>");
  }

  @Test
  void currentRowOfCalculationRegisterListIsFixedUpFromForeignFamily() {
    // Ошибка синтакс-помощника: у расширения формы списка записей регистра расчёта
    // ПараметрТекущаяСтрока объявлен как РегистрНакопленияКлючЗаписи.<Имя регистра
    // накопления> — семейство чужое, поэтому имя своего регистра туда не подставлялось.
    assertThat(typeNames("Форма.РегистрРасчета.РегистрРасчета1.Форма.ФормаСписка", CURRENT_ROW))
      .containsExactly("РегистрРасчетаКлючЗаписи.РегистрРасчета1");
  }

  @Test
  void recorderFilterIsTypedByDocumentsWritingToTheRegister() {
    // Обратной связи «регистр → регистраторы» в метаданных нет: состав известен только
    // со стороны документа (<RegisterRecords>), поэтому индекс собирается обходом документов.
    assertThat(typeNames(REGISTER_LIST_FORM, "ПараметрОтборПоРегистратору"))
      .containsExactly("ДокументСсылка.Документ1");
  }

  @Test
  void ownerFilterParametersAreTypedByTheOwningCatalog() {
    // `ПараметрОтборПоВладельцу` объявлен как СправочникСсылка.<Имя справочника>, но
    // речь о владельце подчинённого справочника, а не о нём самом. Владельцы известны
    // из метаданных (`Catalog.getOwners()`), поэтому тип берётся оттуда.
    assertThat(typeNames(CATALOG_LIST_FORM, "ПараметрОтборПоВладельцу"))
      .containsExactly("СправочникСсылка.Справочник2");
    assertThat(typeNames(CATALOG_CHOICE_FORM, "ПараметрВыборПоВладельцу"))
      .containsExactly("СправочникСсылка.Справочник2");
  }

  @Test
  void parameterPointingAtAnotherObjectIsNotBoundToTheFormOwner() {
    // Плейсхолдер у этих параметров такой же, как у «своих», поэтому подстановка имени
    // самой формы дала бы правдоподобную ложь: тип обязан вести на владельца.
    assertThat(typeNames(CATALOG_LIST_FORM, "ПараметрОтборПоВладельцу"))
      .doesNotContain("СправочникСсылка.Справочник1");
  }

  @Test
  void objectContextBelongsToTheModuleAndNotToTheFormType() {
    // Контекст объекта платформа инжектит в модуль обычной формы, а не в саму форму:
    // у значения, полученного через ПолучитьФорму(), реквизитов объекта нет.
    assertThat(memberNames(CATALOG_ITEM_FORM))
      .as("снаружи форма — это форма")
      .doesNotContain("Наименование", "Код");
    assertThat(memberNames(CATALOG_ITEM_FORM + " (модуль)"))
      .as("в модуле реквизиты объекта доступны неквалифицированно")
      .contains("Наименование", "Код");
  }

  @Test
  void thisObjectExistsOnlyInsideTheModuleAndLeadsToTheObject() {
    // У обычной формы `ЭтотОбъект` не существует: он появляется только в её модуле,
    // вместе с инжектом контекста объекта, и ведёт на объект, а не на форму.
    assertThat(memberNames(CATALOG_ITEM_FORM)).doesNotContain("ЭтотОбъект");
    assertThat(memberNames(CATALOG_LIST_FORM + " (модуль)"))
      .as("в форме списка инжектить нечего — `ЭтотОбъект` не появляется и в модуле")
      .doesNotContain("ЭтотОбъект");
    assertThat(typeNames(CATALOG_ITEM_FORM + " (модуль)", "ЭтотОбъект"))
      .containsExactly("СправочникОбъект.Справочник1");
  }

  @Test
  void moduleTypeExistsEvenWithoutObjectContext() {
    // У формы списка инжектить нечего, но тип модуля всё равно нужен: модуль
    // привязывается к нему по имени, и без регистрации остался бы пустым.
    assertThat(memberNames(CATALOG_LIST_FORM + " (модуль)"))
      .contains("ЭлементыФормы", "ЭтаФорма", CURRENT_ROW);
  }

  private List<String> memberNames(String typeName) {
    var ref = typeRegistry.resolve(typeName).orElseThrow();
    return typeRegistry.getMembers(ref, FileType.BSL).stream().map(MemberDescriptor::name).toList();
  }

  private List<String> typeNames(String formTypeName, String parameterName) {
    var formRef = typeRegistry.resolve(formTypeName).orElseThrow();
    var member = typeRegistry.getMembers(formRef, FileType.BSL).stream()
      .filter(m -> m.kind() == MemberKind.PROPERTY && m.matches(parameterName))
      .findFirst();
    assertThat(member).as("параметр %s у %s", parameterName, formTypeName).isPresent();
    return member.map(MemberDescriptor::returnTypes)
      .map(types -> types.refs().stream().map(TypeRef::qualifiedName).toList())
      .orElseThrow();
  }
}
