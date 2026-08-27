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

import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.mdo.storage.FormData;
import com.github._1c_syntax.bsl.mdo.storage.ManagedFormData;
import com.github._1c_syntax.bsl.mdo.storage.form.FormAttribute;
import com.github._1c_syntax.bsl.mdo.storage.form.FormDynamicListAttribute;
import com.github._1c_syntax.bsl.mdo.storage.form.FormDynamicListField;
import com.github._1c_syntax.bsl.mdo.storage.form.FormSimpleAttribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Разбор источника данных динамического списка
 * ({@link DynamicListTypesRegistrar#prepareRows}) — без Spring и синтакс-помощника.
 * Проверяется, за каким списком строка заводится, а за каким нет, и чем платформа
 * адресует его строку.
 */
class DynamicListTypesRegistrarTest {

  private static final String CATALOG_REF = "СправочникСсылка.Справочник1";
  private static final String FORM_SUFFIX = "Справочник.Справочник1.Форма.ФормаСписка";

  private TypeRegistry typeRegistry;
  private DynamicListTypesRegistrar registrar;

  @BeforeEach
  void setUp() {
    typeRegistry = mock(TypeRegistry.class);
    lenient().when(typeRegistry.resolve(anyString())).thenReturn(Optional.empty());
    lenient().when(typeRegistry.resolve("Catalog.Справочник1"))
      .thenReturn(Optional.of(new TypeRef(TypeKind.CONFIGURATION, CATALOG_REF)));
    lenient().when(typeRegistry.registerConfigurationType(anyString()))
      .thenAnswer(invocation -> new TypeRef(TypeKind.CONFIGURATION, invocation.getArgument(0)));
    registrar = new DynamicListTypesRegistrar(typeRegistry, new FormDataTypesRegistrar(typeRegistry),
      mock(QueryTableResolver.class));
  }

  @Test
  void listOverAMainTableGetsARowIdentifiedByThatTable() {
    var rows = registrar.prepareRows(form(dynamicList("Catalog.Справочник1", false)), FORM_SUFFIX);

    assertThat(rows).containsOnlyKeys("список");
    assertThat(rows.get("список").rowRef().qualifiedName())
      .as("строка заводится на конкретный список: колонки у каждого свои")
      .isEqualTo("ДанныеФормыЭлементКоллекции.ДинамическийСписок." + FORM_SUFFIX + ".Список");
    assertThat(rows.get("список").rowIdRef().qualifiedName())
      .as("строку списка над ссылочной таблицей платформа адресует ссылкой")
      .isEqualTo(CATALOG_REF);
    verify(typeRegistry).registerMemberSource(any(), any(), any());
  }

  @Test
  void listWithACustomQueryAndNoQueryTextHasNoRow() {
    // Поля такого списка — поля выборки его запроса, а не поля основной таблицы:
    // та у него задаёт только динамическое чтение. Без текста запроса называть
    // их нечем.
    var rows = registrar.prepareRows(form(dynamicList("Catalog.Справочник1", true)), FORM_SUFFIX);

    assertThat(rows).isEmpty();
  }

  @Test
  void listWithACustomQueryTextGetsARow() {
    // given
    var list = FormDynamicListAttribute.builder()
      .name("Список")
      .mainTable("Catalog.Справочник1")
      .customQuery(true)
      .queryText("ВЫБРАТЬ Спр.Ссылка ИЗ Справочник.Справочник1 КАК Спр")
      .build();

    // when
    var rows = registrar.prepareRows(form(list), FORM_SUFFIX);

    // then
    assertThat(rows).containsOnlyKeys("список");
    assertThat(rows.get("список").rowIdRef())
      .as("строку списка с произвольным запросом основная таблица не адресует")
      .isNull();
  }

  @Test
  void listWithACustomQueryAndAFieldCompositionGetsARow() {
    // given
    var list = FormDynamicListAttribute.builder()
      .name("Список")
      .mainTable("Catalog.Справочник1")
      .customQuery(true)
      .addFields(FormDynamicListField.builder().dataPath("Ссылка").name("Ссылка").build())
      .build();

    // when
    var rows = registrar.prepareRows(form(list), FORM_SUFFIX);

    // then
    assertThat(rows).containsOnlyKeys("список");
    assertThat(rows.get("список").rowIdRef())
      .as("строку списка с произвольным запросом основная таблица не адресует")
      .isNull();
  }

  @Test
  void listWithoutAMainTableHasNoRow() {
    var rows = registrar.prepareRows(form(dynamicList("", false)), FORM_SUFFIX);

    assertThat(rows).isEmpty();
  }

  @Test
  void listOverAVirtualTableGetsARowWithoutARowIdentifier() {
    // given
    // when
    // Виртуальная таблица регистра ссылочного типа не имеет, поэтому строку
    // ей не адресовать — но поля у такой таблицы есть, и строка нужна.
    var rows = registrar.prepareRows(
      form(dynamicList("AccumulationRegister.Остатки.Turnovers", false)), FORM_SUFFIX);

    // then
    assertThat(rows).containsOnlyKeys("список");
    assertThat(rows.get("список").rowIdRef()).isNull();
  }

  @Test
  void plainAttributeIsNotAList() {
    var rows = registrar.prepareRows(form(FormSimpleAttribute.builder().name("Реквизит1").build()),
      FORM_SUFFIX);

    assertThat(rows).isEmpty();
    verifyNoInteractions(typeRegistry);
  }

  /**
   * Форма из одних реквизитов: элементов и оформления у неё нет, поэтому полей
   * своего списка она не читает — проверяется всё, кроме состава строки.
   */
  private static FormData form(FormAttribute attribute) {
    return ManagedFormData.builder().addAttributes(attribute).build();
  }

  private static FormAttribute dynamicList(String mainTable, boolean customQuery) {
    return FormDynamicListAttribute.builder()
      .name("Список")
      .mainTable(mainTable)
      .customQuery(customQuery)
      .build();
  }
}
