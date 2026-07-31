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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Параметр-основание обычной формы. В отличие от прочих параметров, тип у него не
 * объявлен вовсе — ни своим типом, ни generic-плейсхолдером, — поэтому берётся из
 * {@code ВводитсяНаОсновании} владельца формы.
 * <p>
 * Сам параметр приходит из синтакс-помощника (в JSON-фолбэке его нет), поэтому тест
 * требует установленной 1С.
 */
@CleanupContextBeforeClassAndAfterClass
@TestPropertySource(properties = "app.platform-context.enabled=true")
@EnabledIfEnvironmentVariable(named = "BSL_LANGUAGE_SERVER_RUN_HBK_TESTS",
  matches = "true",
  disabledReason = "Требует HBK 1С (`ПараметрОснование` объявлен только в синтакс-помощнике)")
class OrdinaryFormBasisHbkTest extends AbstractServerContextAwareTest {

  private static final String PATH_TO_ORDINARY_FORMS = "src/test/resources/metadata/ordinaryForms";

  private static final String BASED_ON_CATALOG_FORM = "Форма.Справочник.Справочник1.Форма.ФормаЭлемента";
  private static final String PLAIN_CATALOG_FORM = "Форма.Справочник.Справочник2.Форма.ФормаЭлемента";

  private static final String BASIS_PARAMETER = "ПараметрОснование";

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
  void basisIsTypedByTheObjectsItIsEnteredOnBasisOf() {
    assertThat(typeNames(BASED_ON_CATALOG_FORM, BASIS_PARAMETER))
      .containsExactly("ДокументСсылка.Документ1");
  }

  @Test
  void basisOfObjectEnteredOnNoBasisIsUndefined() {
    // Справочник2 ни на чём не вводится — передавать в параметр нечего.
    assertThat(typeNames(PLAIN_CATALOG_FORM, BASIS_PARAMETER))
      .containsExactly("Неопределено");
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
