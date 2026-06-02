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
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Литералы разных видов: date в нескольких форматах, числовые (целые,
 * дробные, отрицательные, нули, миллионы), строковые (пустые, длинные,
 * специальные), Истина/Ложь, Неопределено/Null, конкатенация смешанных.
 */
@CleanupContextBeforeClassAndAfterClass
class QueryAndDateLiteralsTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void dateLiteralSimple() {
    var t = at("Д1 = '20200101'", "Д1 = ".length());
    assertThat(qnames(t)).contains("Дата");
  }

  @Test
  void dateLiteralWithTime() {
    var t = at("Д2 = '20200101120000'", "Д2 = ".length());
    assertThat(qnames(t)).contains("Дата");
  }

  @Test
  void integerZeroDoesNotCrash() {
    var t = at("Ц1 = 0", "Ц1 = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void integerOneDoesNotCrash() {
    var t = at("Ц2 = 1", "Ц2 = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void negativeInteger() {
    var t = at("Ц3 = -1", "Ц3 = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void floatLiteral() {
    var t = at("Ц4 = 1.5", "Ц4 = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void smallFloat() {
    var t = at("Ц5 = 0.001", "Ц5 = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void largeInteger() {
    var t = at("Ц6 = 1000000", "Ц6 = ".length());
    assertThat(qnames(t)).contains("Число");
  }

  @Test
  void emptyString() {
    var t = at("С1 = \"\"", "С1 = ".length());
    assertThat(qnames(t)).contains("Строка");
  }

  @Test
  void simpleString() {
    var t = at("С2 = \"однострочник\"", "С2 = ".length());
    assertThat(qnames(t)).contains("Строка");
  }

  @Test
  void stringWithSpecialChars() {
    var t = at("С3 = \"тест с пробелами и спецсимволами !@#$%^&*()\"",
      "С3 = ".length());
    assertThat(qnames(t)).contains("Строка");
  }

  @Test
  void booleanTrue() {
    var t = at("Б1 = Истина", "Б1 = ".length());
    assertThat(qnames(t)).contains("Булево");
  }

  @Test
  void booleanFalse() {
    var t = at("Б2 = Ложь", "Б2 = ".length());
    assertThat(qnames(t)).contains("Булево");
  }

  @Test
  void undefined() {
    var t = at("Н1 = Неопределено", "Н1 = ".length());
    assertThat(qnames(t)).contains("Неопределено");
  }

  @Test
  void nullValue() {
    var t = at("Н2 = NULL", "Н2 = ".length());
    assertThat(t).isNotNull();
  }

  @Test
  void concatenationStringAndNumber() {
    var t = at("См1 = \"число: \" + 100", "См1 = ".length());
    assertThat(qnames(t)).contains("Строка");
  }

  private TypeSet at(String marker, int offsetInMarker) {
    var dc = doc();
    var content = dc.getContent();
    int markerStart = content.indexOf(marker);
    int targetOffset = markerStart + offsetInMarker;
    int lineStart = content.lastIndexOf('\n', targetOffset) + 1;
    int line = content.substring(0, targetOffset).split("\n").length - 1;
    int charInLine = targetOffset - lineStart;
    return typeService.expressionTypesAt(dc, new Position(line, charInLine + 1));
  }

  private DocumentContext doc() {
    return TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/QueryAndDateLiterals.bsl");
  }

  private static java.util.List<String> qnames(TypeSet ts) {
    return ts.refs().stream().map(r -> r.qualifiedName()).toList();
  }
}
