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
 * Счета записи регистра бухгалтерии и вид расчёта записи регистра расчёта: платформа
 * объявляет их через плейсхолдер ({@code ПланСчетовСсылка.<Имя плана счетов>}), а
 * какой именно план имеется в виду, известно только из метаданных самого регистра
 * (mdclasses#670).
 * <p>
 * В JSON-фолбэке этих членов нет — у записи регистра там объявлен один
 * {@code Регистратор}, — поэтому тест требует установленной 1С.
 */
@CleanupContextBeforeClassAndAfterClass
@TestPropertySource(properties = "app.platform-context.enabled=true")
@EnabledIfEnvironmentVariable(named = "BSL_LANGUAGE_SERVER_RUN_HBK_TESTS",
  matches = "true",
  disabledReason = "Требует HBK 1С (счета и вид расчёта записи регистра — из bsl-context)")
class RegisterChartTypesHbkTest extends AbstractServerContextAwareTest {

  private static final String PATH_TO_REGISTERS = "src/test/resources/metadata/registerRecorders";

  @Autowired
  private ConfigurationTypesProvider provider;

  @Autowired
  private TypeRegistry typeRegistry;

  @BeforeEach
  void setUp() {
    initServerContextOnce(Absolute.path(PATH_TO_REGISTERS));
    context.getConfiguration();
    provider.tryRegister();
  }

  @Test
  void accountsOfAccountingRegisterRecordAreTypedByItsChartOfAccounts() {
    var recordType = "РегистрБухгалтерииЗапись.РегистрБухгалтерии1";

    assertThat(memberTypes(recordType, "Счет")).containsExactly("ПланСчетовСсылка.ПланСчетов1");
    assertThat(memberTypes(recordType, "СчетДт")).containsExactly("ПланСчетовСсылка.ПланСчетов1");
    assertThat(memberTypes(recordType, "СчетКт")).containsExactly("ПланСчетовСсылка.ПланСчетов1");
  }

  @Test
  void recorderStaysTypedByItsDocumentsAlongsideTheChart() {
    // План счетов подставляется только в свой плейсхолдер, регистраторы — только в
    // `<Имя документа>`: имена у них могут совпадать (см. RegisterRecorderTypesTest).
    assertThat(memberTypes("РегистрБухгалтерииЗапись.РегистрБухгалтерии1", "Регистратор"))
      .containsExactly("ДокументСсылка.Документ1");
  }

  @Test
  void calculationTypeOfCalculationRegisterRecordIsTypedByItsChart() {
    assertThat(memberTypes("РегистрРасчетаЗапись.РегистрРасчета1", "ВидРасчета"))
      .containsExactly("ПланВидовРасчетаСсылка.ПланВидовРасчета1");
  }

  private List<String> memberTypes(String typeName, String memberName) {
    var ref = typeRegistry.resolve(typeName).orElseThrow();
    var member = typeRegistry.getMembers(ref, FileType.BSL).stream()
      .filter(m -> m.matches(memberName))
      .findFirst();
    assertThat(member).as("член %s у %s", memberName, typeName).isPresent();
    return member.map(MemberDescriptor::returnTypes)
      .map(types -> types.refs().stream().map(TypeRef::qualifiedName).toList())
      .orElseThrow();
  }
}
