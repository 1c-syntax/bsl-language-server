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
package com.github._1c_syntax.bsl.languageserver.types.oscript;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class OScriptExtendsTest extends AbstractServerContextAwareTest {

  @Autowired
  private OScriptExtends oScriptExtends;

  @BeforeEach
  void init() {
    initServerContext();
  }

  @Test
  void parentClassNameForBslFileIsEmpty() {
    var dc = TestUtils.getDocumentContext("Процедура П()\nКонецПроцедуры\n");
    assertThat(oScriptExtends.parentClassName(dc)).isEmpty();
  }

  @Test
  void parentClassNameDirectRussianAnnotation() {
    var dc = os("&Расширяет(\"Родитель\")\nПроцедура ПриСозданииОбъекта()\nКонецПроцедуры\n");
    assertThat(oScriptExtends.parentClassName(dc)).contains("Родитель");
  }

  @Test
  void parentClassNameEnglishAliasAnnotation() {
    var dc = os("&Extends(\"Родитель\")\nПроцедура ПриСозданииОбъекта()\nКонецПроцедуры\n");
    assertThat(oScriptExtends.parentClassName(dc)).contains("Родитель");
  }

  @Test
  void parentClassNameEmptyForAnnotationWithoutStringArgument() {
    var dc = os("&Extends\nПроцедура ПриСозданииОбъекта()\nКонецПроцедуры\n");
    assertThat(oScriptExtends.parentClassName(dc)).isEmpty();
  }

  @Test
  void parentClassNameAbsentWithoutAnnotation() {
    var dc = os("Процедура ПриСозданииОбъекта()\nКонецПроцедуры\n");
    assertThat(oScriptExtends.parentClassName(dc)).isEmpty();
  }

  @Test
  void parentClassNameIgnoresAnnotationOnNonConstructorMethod() {
    // given — &Расширяет стоит на вспомогательном методе, а не на конструкторе.
    var dc = os("""
      Процедура ПриСозданииОбъекта()
      КонецПроцедуры

      &Расширяет("Родитель")
      Процедура Вспомогательный()
      КонецПроцедуры
      """);

    // when / then — наследование не объявлено (аннотация не на конструкторе).
    assertThat(oScriptExtends.parentClassName(dc)).isEmpty();
  }

  @Test
  void implementedInterfaceNamesForBslFileIsEmpty() {
    var dc = TestUtils.getDocumentContext("Процедура П()\nКонецПроцедуры\n");
    assertThat(oScriptExtends.implementedInterfaceNames(dc)).isEmpty();
  }

  @Test
  void implementedInterfaceNamesCollectsMultiple() {
    var dc = os("""
      &Реализует("ИнтерфейсА")
      &Реализует("ИнтерфейсБ")
      Процедура ПриСозданииОбъекта()
      КонецПроцедуры
      """);
    assertThat(oScriptExtends.implementedInterfaceNames(dc))
      .containsExactlyInAnyOrder("ИнтерфейсА", "ИнтерфейсБ");
  }

  @Test
  void implementedInterfaceNamesIgnoresAnnotationOnNonConstructorMethod() {
    // given — &Реализует на вспомогательном методе, конструктор без аннотации.
    var dc = os("""
      Процедура ПриСозданииОбъекта()
      КонецПроцедуры

      &Реализует("ИнтерфейсА")
      Процедура Вспомогательный()
      КонецПроцедуры
      """);

    // when / then — реализация не объявлена.
    assertThat(oScriptExtends.implementedInterfaceNames(dc)).isEmpty();
  }

  @Test
  void isInterfaceTrueForInterfaceMarker() {
    var dc = os("&Интерфейс\nПроцедура ПриСозданииОбъекта()\nКонецПроцедуры\n");
    assertThat(oScriptExtends.isInterface(dc)).isTrue();
  }

  @Test
  void isInterfaceFalseForPlainClassAndBsl() {
    var os = os("Процедура ПриСозданииОбъекта()\nКонецПроцедуры\n");
    var bsl = TestUtils.getDocumentContext("Процедура П()\nКонецПроцедуры\n");
    assertThat(oScriptExtends.isInterface(os)).isFalse();
    assertThat(oScriptExtends.isInterface(bsl)).isFalse();
  }

  @Test
  void isParentHolderForImplicitField() {
    var variable = moduleVariable("Перем _ОбъектРодитель;\n", "_ОбъектРодитель");
    assertThat(oScriptExtends.isParentHolder(variable)).isTrue();
  }

  @Test
  void isParentHolderForAnnotatedField() {
    var variable = moduleVariable("&Родитель\nПерем СсылкаНаРодителя;\n", "СсылкаНаРодителя");
    assertThat(oScriptExtends.isParentHolder(variable)).isTrue();
  }

  @Test
  void isParentHolderFalseForPlainField() {
    var variable = moduleVariable("Перем ОбычноеПоле;\n", "ОбычноеПоле");
    assertThat(oScriptExtends.isParentHolder(variable)).isFalse();
  }

  private DocumentContext os(String content) {
    return TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);
  }

  private VariableSymbol moduleVariable(String content, String name) {
    var dc = os(content);
    return dc.getSymbolTree().getVariables().stream()
      .filter(v -> v.getName().equalsIgnoreCase(name))
      .findFirst()
      .orElseThrow();
  }
}
