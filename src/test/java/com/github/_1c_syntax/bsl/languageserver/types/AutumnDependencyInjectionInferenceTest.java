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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Вывод типа внедряемых через {@code &Пластилин} зависимостей фреймворка «ОСень».
 */
@CleanupContextBeforeClassAndAfterClass
class AutumnDependencyInjectionInferenceTest extends AbstractServerContextAwareTest {

  private static final Path FIXTURE_ROOT =
    Path.of("src/test/resources/oscript-libraries/autumn-di").toAbsolutePath();

  @Autowired
  private OScriptLibraryIndex index;

  @Autowired
  private TypeService typeService;

  private DocumentContext consumer;

  @BeforeEach
  void setup() {
    initServerContext(FIXTURE_ROOT, false);
    index.reindex(context);
    var uri = Absolute.uri(FIXTURE_ROOT.resolve("src/Приложение.os").toUri());
    consumer = context.getDocument(uri);
  }

  @Test
  void infersBeanTypeForFieldWithExplicitName() {
    // when
    var types = typeService.findTypes(variable("ВнедренныйПоИмени"));

    // then
    assertThat(qualifiedNames(types)).containsExactly("Логгер");
  }

  @Test
  void infersBeanTypeForFieldByVariableName() {
    // given
    // поле объявлено как `&Пластилин Перем Логгер;` — имя желудя берётся из имени поля

    // when
    var types = typeService.findTypes(variable("Логгер"));

    // then
    assertThat(qualifiedNames(types)).containsExactly("Логгер");
  }

  @Test
  void infersBeanTypeForConstructorParameter() {
    // when
    var types = typeService.findTypes(variable("ЛоггерИзКонструктора"));

    // then
    assertThat(qualifiedNames(types)).containsExactly("Логгер");
  }

  @Test
  void infersBeanTypeByQualifier() {
    // given
    // желудь Василий объявлен с &Прозвище("Васян")

    // when
    var types = typeService.findTypes(variable("ЧерезПрозвище"));

    // then
    assertThat(qualifiedNames(types)).containsExactly("Василий");
  }

  @Test
  void prefersPrimaryBeanOnQualifierConflict() {
    // given
    // прозвище "Панк" есть у ДжонниРоттен и СидВишес; СидВишес помечен &Верховный

    // when
    var types = typeService.findTypes(variable("ПриоритетныйПанк"));

    // then
    assertThat(qualifiedNames(types)).containsExactly("СидВишес");
  }

  @Test
  void infersBeanTypeForRenamedComponent() {
    // given
    // РеальныйКласс объявлен как &Желудь("ЛогическоеИмя")

    // when
    var types = typeService.findTypes(variable("Переименованный"));

    // then
    assertThat(qualifiedNames(types)).containsExactly("РеальныйКласс");
  }

  @Test
  void infersBeanTypeForFactoryMethod() {
    // given
    // &Завязь(Значение = "ФабричныйЖелудь", Тип = "Логгер")

    // when
    var types = typeService.findTypes(variable("ИзФабрики"));

    // then
    assertThat(qualifiedNames(types)).containsExactly("Логгер");
  }

  @Test
  void infersBeanTypeThroughMetaAnnotations() {
    // given
    // поле помечено &Внедряемое (= &Пластилин через &Аннотация),
    // а желудь СовременныйЛоггер объявлен через &Компонент (= &Желудь)

    // when
    var types = typeService.findTypes(variable("ЧерезМетаАннотацию"));

    // then
    assertThat(qualifiedNames(types)).containsExactly("СовременныйЛоггер");
  }

  @Test
  void injectedParameterResolvesAtUsageAndFlowsIntoField() {
    // given: потребитель внедряет желудь через параметр конструктора и присваивает его полю.
    // Документ грузится со ссылками (как при открытии в редакторе) — без ручного рефилла.
    var path = FIXTURE_ROOT.resolve("src/ПотребительВКонструкторе.os").toString();
    var doc = TestUtils.getDocumentContextFromFile(path, context);
    var lines = doc.getContentList();

    int usageLine = -1;
    for (int i = 0; i < lines.length; i++) {
      if (lines[i].contains("= _Логгер")) {
        usageLine = i;
      }
    }
    var usagePosition = new Position(usageLine, lines[usageLine].indexOf("_Логгер") + 1);

    // when
    var atUsage = typeService.inferAtPosition(doc, usagePosition);
    var field = doc.getSymbolTree().getVariables().stream()
      .filter(v -> "Сохранённый".equals(v.getName()))
      .findFirst()
      .orElseThrow();
    var fieldTypes = typeService.findTypes(field);

    // then
    assertThat(qualifiedNames(atUsage))
      .as("использование параметра-желудя в теле конструктора резолвится в тип")
      .containsExactly("Логгер");
    assertThat(qualifiedNames(fieldTypes))
      .as("тип внедряемого желудя протекает в поле через присваивание")
      .containsExactly("Логгер");
  }

  private VariableSymbol variable(String name) {
    return consumer.getSymbolTree().getVariables().stream()
      .filter(v -> name.equals(v.getName()))
      .findFirst()
      .orElseThrow();
  }

  private static List<String> qualifiedNames(TypeSet types) {
    return types.refs().stream().map(TypeRef::qualifiedName).toList();
  }
}
