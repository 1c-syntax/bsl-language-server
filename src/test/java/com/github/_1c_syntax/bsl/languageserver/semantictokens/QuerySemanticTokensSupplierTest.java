/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.semantictokens;

import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class QuerySemanticTokensSupplierTest {

  @Autowired
  private QuerySemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensLegend legend;

  @Test
  void testSimpleSelect() {
    // given
    String bsl = """
      Функция Тест()
        Запрос = "Выбрать * из Справочник.Контрагенты";
      КонецФункции
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int keywordTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Keyword);
    int namespaceTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Namespace);
    int classTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Class);

    var keywordTokens = tokens.stream().filter(t -> t.type() == keywordTypeIdx).toList();
    var namespaceTokens = tokens.stream().filter(t -> t.type() == namespaceTypeIdx).toList();
    var classTokens = tokens.stream().filter(t -> t.type() == classTypeIdx).toList();

    // Выбрать, из
    assertThat(keywordTokens).hasSizeGreaterThanOrEqualTo(2);
    // Справочник
    assertThat(namespaceTokens).hasSize(1);
    // Контрагенты
    assertThat(classTokens).hasSize(1);
  }

  @Test
  void testQueryWithParameter() {
    // given
    String bsl = """
      Функция Тест()
        Запрос = "Выбрать * где Код = &Параметр";
      КонецФункции
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int parameterTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Parameter);
    var parameterTokens = tokens.stream().filter(t -> t.type() == parameterTypeIdx).toList();

    // &Параметр
    assertThat(parameterTokens).hasSize(1);
    assertThat(parameterTokens.get(0).line()).isEqualTo(1);
  }

  @Test
  void testQueryWithVirtualTable() {
    // given
    String bsl = """
      Функция Тест()
        Запрос = "Выбрать * из РегистрСведений.КурсыВалют.СрезПоследних()";
      КонецФункции
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int methodTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Method);
    var methodTokens = tokens.stream().filter(t -> t.type() == methodTypeIdx).toList();

    // СрезПоследних
    assertThat(methodTokens).hasSize(1);
  }

  @Test
  void testQueryWithValueFunction() {
    // given
    String bsl = """
      Функция Тест()
        Запрос = "Выбрать * где Валюта = Значение(Справочник.Валюты.Рубль)";
      КонецФункции
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int namespaceTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Namespace);
    int classTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Class);
    int enumMemberTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.EnumMember);

    var namespaceTokens = tokens.stream().filter(t -> t.type() == namespaceTypeIdx).toList();
    var classTokens = tokens.stream().filter(t -> t.type() == classTypeIdx).toList();
    var enumMemberTokens = tokens.stream().filter(t -> t.type() == enumMemberTypeIdx).toList();

    // Справочник
    assertThat(namespaceTokens).hasSize(1);
    // Валюты
    assertThat(classTokens).hasSize(1);
    // Рубль
    assertThat(enumMemberTokens).hasSize(1);
  }

  @Test
  void testQueryWithEnumValue() {
    // given
    String bsl = """
      Функция Тест()
        Запрос = "Выбрать * где Пол = Значение(Перечисление.Пол.Мужской)";
      КонецФункции
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int enumTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Enum);
    int enumMemberTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.EnumMember);

    var enumTokens = tokens.stream().filter(t -> t.type() == enumTypeIdx).toList();
    var enumMemberTokens = tokens.stream().filter(t -> t.type() == enumMemberTypeIdx).toList();

    // Пол (enum)
    assertThat(enumTokens).hasSize(1);
    // Мужской (enum member)
    assertThat(enumMemberTokens).hasSize(1);
  }

  @Test
  void testSplitsStringAroundQueryTokens() {
    // given
    String bsl = """
      Функция Тест()
        Запрос = "Выбрать Поле из Справочник.Контрагенты";
      КонецФункции
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int stringTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.String);
    var stringTokens = tokens.stream().filter(t -> t.type() == stringTypeIdx).toList();

    // String parts (quotes, spaces) around query tokens
    assertThat(stringTokens).isNotEmpty();
  }
}

