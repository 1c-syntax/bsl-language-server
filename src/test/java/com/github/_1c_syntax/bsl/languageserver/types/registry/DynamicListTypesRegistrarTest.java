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
import com.github._1c_syntax.bsl.mdo.storage.form.FormAttribute;
import com.github._1c_syntax.bsl.mdo.storage.form.FormDynamicListAttribute;
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
import static org.mockito.Mockito.when;

/**
 * Разбор источника данных динамического списка
 * ({@link DynamicListTypesRegistrar#prepareRows}) — без Spring и синтакс-помощника.
 * Проверяется, за каким списком строка заводится, а за каким нет: колонки строки
 * берутся у основной таблицы, и без неё их взять неоткуда.
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
    registrar = new DynamicListTypesRegistrar(typeRegistry, new FormDataTypesRegistrar(typeRegistry));
  }

  @Test
  void listOverAMainTableGetsARowIdentifiedByThatTable() {
    var rows = registrar.prepareRows(List.of(dynamicList("Catalog.Справочник1", false)), FORM_SUFFIX);

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
  void listWithACustomQueryHasNoRow() {
    // Поля такого списка — поля выборки его запроса, а не поля основной таблицы:
    // та у него задаёт только динамическое чтение.
    var rows = registrar.prepareRows(List.of(dynamicList("Catalog.Справочник1", true)), FORM_SUFFIX);

    assertThat(rows).isEmpty();
  }

  @Test
  void listWithoutAMainTableHasNoRow() {
    var rows = registrar.prepareRows(List.of(dynamicList("", false)), FORM_SUFFIX);

    assertThat(rows).isEmpty();
  }

  @Test
  void listOverAnUnknownTableHasNoRow() {
    // Виртуальные таблицы регистров своего имени в реестре не имеют — колонки
    // такого списка остаются неизвестными, как и до появления источника.
    var rows = registrar.prepareRows(
      List.of(dynamicList("AccumulationRegister.Остатки.Turnovers", false)), FORM_SUFFIX);

    assertThat(rows).isEmpty();
  }

  @Test
  void plainAttributeIsNotAList() {
    var rows = registrar.prepareRows(List.of(FormSimpleAttribute.builder().name("Реквизит1").build()),
      FORM_SUFFIX);

    assertThat(rows).isEmpty();
    verifyNoInteractions(typeRegistry);
  }

  private static FormAttribute dynamicList(String mainTable, boolean customQuery) {
    return FormDynamicListAttribute.builder()
      .name("Список")
      .mainTable(mainTable)
      .customQuery(customQuery)
      .build();
  }
}
