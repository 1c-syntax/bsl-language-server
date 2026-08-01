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
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.EventMethodSymbol;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.index.EventContractsIndex;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Модуль формы связан с типом своей формы так же, как модуль объекта — с
 * {@code СправочникОбъект.<Имя>}: неквалифицированные имена резолвятся как self-члены,
 * а объявленные в {@code Form.xml} обработчики распознаются как события.
 */
@CleanupContextBeforeClassAndAfterClass
class FormModuleSelfTypeTest extends AbstractServerContextAwareTest {

  private static final String DOCUMENT_FORM_MODULE =
    "Documents/Документ1/Forms/ФормаДокумента/Ext/Form/Module.bsl";
  private static final String CATALOG_FORM_MODULE =
    "Catalogs/Справочник1/Forms/ФормаЭлемента/Ext/Form/Module.bsl";

  @Autowired
  private TypeService typeService;

  @Autowired
  private GlobalScopeProvider globalScopeProvider;

  @Autowired
  private EventContractsIndex eventContractsIndex;

  @Autowired
  private FormHandlerRoleIndex formHandlerRoleIndex;

  @BeforeEach
  void setUp() {
    initServerContextOnce(Absolute.path(PATH_TO_METADATA));
  }

  @Test
  void formModuleUriIsIndexedWithFormType() {
    var documentContext = documentContext(DOCUMENT_FORM_MODULE);

    assertThat(globalScopeProvider.moduleTypeRefByUri(documentContext.getUri()))
      .isPresent()
      .get()
      .extracting(ref -> ref.qualifiedName())
      .isEqualTo("ФормаКлиентскогоПриложения.Документ.Документ1.Форма.ФормаДокумента");
  }

  @Test
  void formAttributesAndItemsAreSelfMembers() {
    var documentContext = documentContext(DOCUMENT_FORM_MODULE);

    // Реквизит формы из Form.xml.
    assertThat(typeService.findSelfMember(documentContext, "Объект", MemberKind.PROPERTY)).isPresent();
    // Коллекция элементов и платформенное свойство базового типа формы.
    assertThat(typeService.findSelfMember(documentContext, "Элементы", MemberKind.PROPERTY)).isPresent();
    assertThat(typeService.findSelfMember(documentContext, "Модифицированность", MemberKind.PROPERTY))
      .isPresent();
    // Английские написания платформенных членов тоже резолвятся.
    assertThat(typeService.findSelfMember(documentContext, "Items", MemberKind.PROPERTY)).isPresent();
    // Платформенный метод формы.
    assertThat(typeService.findSelfMember(documentContext, "РеквизитФормыВЗначение", MemberKind.METHOD))
      .isPresent();
  }

  @Test
  void selfMemberLookupWorksBeforeModuleTypeCacheIsFilled() {
    // Резолв self-типа не должен зависеть от кэша moduleTypeRefByUri: дерево символов
    // строится раньше, чем публикуется DocumentContextContentChangedEvent.
    var documentContext = documentContext(CATALOG_FORM_MODULE);
    globalScopeProvider.removeModuleType(documentContext.getUri());

    assertThat(typeService.findSelfMember(documentContext, "Объект", MemberKind.PROPERTY))
      .as("self-тип модуля формы резолвится из метаданных напрямую")
      .isPresent();
  }

  @Test
  void declaredHandlerIsClassifiedAsEventMethod() {
    // Form.xml документа объявляет <Event name="OnWriteAtServer">ПриЗаписиНаСервере</Event>,
    // и такая процедура в модуле есть.
    var documentContext = documentContext(DOCUMENT_FORM_MODULE);

    var contract = eventContractsIndex.getContract(documentContext, "ПриЗаписиНаСервере");
    assertThat(contract).isPresent();
    assertThat(contract.get().kind()).isEqualTo(MemberKind.EVENT);

    var method = documentContext.getSymbolTree().getMethodSymbol("ПриЗаписиНаСервере").orElseThrow();
    assertThat(method).isInstanceOf(EventMethodSymbol.class);
  }

  @Test
  void handlerRolesAreTakenFromTheFormItself() {
    // Стандартная область у обработчика зависит от того, кем он объявлен, а у событий
    // элементов таблицы имя области — шаблон: суффиксом идёт имя самой таблицы.
    var documentContext = documentContext(DOCUMENT_FORM_MODULE);

    assertThat(formHandlerRoleIndex.roleOf(documentContext, "ПриЗаписиНаСервере"))
      .contains(new FormHandlerRoleIndex.Handler(FormHandlerRoleIndex.Role.FORM_EVENT, ""));
    assertThat(formHandlerRoleIndex.roleOf(documentContext, "Реквизит1ПриИзменении"))
      .contains(new FormHandlerRoleIndex.Handler(FormHandlerRoleIndex.Role.HEADER_ITEM_EVENT, "Реквизит1"));
    assertThat(formHandlerRoleIndex.roleOf(documentContext, "ТабличнаяЧасть1Реквизит1ПриИзменении"))
      .as("владелец — таблица, в которой лежит колонка, а не сама колонка")
      .contains(new FormHandlerRoleIndex.Handler(
        FormHandlerRoleIndex.Role.TABLE_ITEM_EVENT, "ТабличнаяЧасть1"));
    assertThat(formHandlerRoleIndex.roleOf(documentContext, "ЗаполнитьПоОснованиюКоманда"))
      .contains(new FormHandlerRoleIndex.Handler(FormHandlerRoleIndex.Role.COMMAND, "ЗаполнитьПоОснованию"));
    assertThat(formHandlerRoleIndex.roleOf(documentContext, "ТестОписанийОповещения"))
      .as("процедура без объявления в Form.xml роли не имеет")
      .isEmpty();
  }

  @Test
  void methodWithoutDeclarationInFormXmlIsNotEventHandler() {
    var documentContext = documentContext(DOCUMENT_FORM_MODULE);

    assertThat(eventContractsIndex.getContract(documentContext, "ТестОписанийОповещения"))
      .as("процедура без объявления в Form.xml обработчиком не считается")
      .isEmpty();
  }

  private DocumentContext documentContext(String relativePath) {
    var uri = Absolute.uri(new File(PATH_TO_METADATA, relativePath));
    var documentContext = context.addDocument(uri);
    context.rebuildDocument(documentContext);
    return documentContext;
  }
}
