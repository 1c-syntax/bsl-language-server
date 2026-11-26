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
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Происхождение типа: из какого дженерика он вырос и что на него подмешано
 * расширением. Спросить об этом реестр — не то же самое, что прочитать код
 * регистрации: подмешаться могло не тем путём, каким думает регистратор.
 */
@CleanupContextBeforeClassAndAfterClass
class TypeProvenanceTest extends AbstractServerContextAwareTest {

  private static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";

  private static final String DOCUMENT_FORM = "ФормаКлиентскогоПриложения.Документ.Документ1.Форма.ФормаДокумента";

  @Autowired
  private ConfigurationTypesProvider provider;

  @Autowired
  private TypeRegistry typeRegistry;

  @BeforeEach
  void setUp() {
    initServerContextOnce(Absolute.path(PATH_TO_METADATA));
    context.getConfiguration();
    provider.tryRegister();
  }

  @Test
  void configurationTypeRemembersItsGeneric() {
    assertThat(genericOf("СправочникСсылка.Справочник1"))
      .isEqualTo("СправочникСсылка.<Имя справочника>");
    assertThat(genericOf("ДокументОбъект.Документ1"))
      .isEqualTo("ДокументОбъект.<Имя документа>");
  }

  @Test
  void genericItselfGrewFromNothing() {
    var generic = typeRegistry.resolve("СправочникСсылка.<Имя справочника>").orElseThrow();
    assertThat(typeRegistry.genericOf(generic)).isEmpty();
  }

  @Test
  void formTypeRemembersWhatIsMixedIntoIt() {
    // Тип формы собирается из платформенного базового типа и расширения по основному
    // реквизиту — именно оно даёт события работы с данными.
    assertThat(extensionsOf(DOCUMENT_FORM))
      .contains("ФормаКлиентскогоПриложения")
      .anySatisfy(name -> assertThat(name).startsWith("Расширение"));
  }

  @Test
  void extensionIsNotMistakenForSpecialization() {
    // Расширение дополняет уже существующий тип, а не порождает его из дженерика:
    // спрашивать у формы дженерик бессмысленно, и реестр это подтверждает.
    var form = typeRegistry.resolve(DOCUMENT_FORM).orElseThrow();
    assertThat(typeRegistry.genericOf(form)).isEmpty();
    assertThat(typeRegistry.extensionsOf(form)).isNotEmpty();
  }

  private String genericOf(String typeName) {
    var ref = typeRegistry.resolve(typeName).orElseThrow();
    return typeRegistry.genericOf(ref).map(TypeRef::qualifiedName).orElse("");
  }

  private List<String> extensionsOf(String typeName) {
    var ref = typeRegistry.resolve(typeName).orElseThrow();
    return typeRegistry.extensionsOf(ref).stream().map(TypeRef::qualifiedName).toList();
  }
}
