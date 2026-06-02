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
 * ОписаниеТипов и квалификаторы — конструкторы с разной арностью,
 * использование в ТЗ.
 */
@CleanupContextBeforeClassAndAfterClass
class TypeDescriptionPatternsTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void typeDescriptionNoArgs() {
    var t = at("ОТ_БезАргов = Новый ОписаниеТипов", "ОТ_БезАргов = ".length());
    assertThat(qnames(t)).contains("ОписаниеТипов");
  }

  @Test
  void typeDescriptionSingleType() {
    var t = at("ОТ_ОдинТип = Новый ОписаниеТипов(\"Число\")",
      "ОТ_ОдинТип = ".length());
    assertThat(qnames(t)).contains("ОписаниеТипов");
  }

  @Test
  void typeDescriptionMultipleTypes() {
    var t = at("ОТ_НесколькоТипов = Новый ОписаниеТипов(\"Число, Строка\")",
      "ОТ_НесколькоТипов = ".length());
    assertThat(qnames(t)).contains("ОписаниеТипов");
  }

  @Test
  void typeDescriptionWithQualifier() {
    var t = at(
      "ОТ_СКвалиф = Новый ОписаниеТипов(\"Число\", Новый КвалификаторыЧисла(10, 2))",
      "ОТ_СКвалиф = ".length());
    assertThat(qnames(t)).contains("ОписаниеТипов");
  }

  @Test
  void numberQualifierConstructor() {
    var t = at("КвЧ = Новый КвалификаторыЧисла(10, 2)", "КвЧ = ".length());
    assertThat(qnames(t)).contains("КвалификаторыЧисла");
  }

  @Test
  void numberQualifierEmpty() {
    var t = at("КвЧ2 = Новый КвалификаторыЧисла", "КвЧ2 = ".length());
    assertThat(qnames(t)).contains("КвалификаторыЧисла");
  }

  @Test
  void stringQualifier() {
    var t = at("КвС = Новый КвалификаторыСтроки(50)", "КвС = ".length());
    assertThat(qnames(t)).contains("КвалификаторыСтроки");
  }

  @Test
  void dateQualifier() {
    var t = at("КвД = Новый КвалификаторыДаты", "КвД = ".length());
    assertThat(qnames(t)).contains("КвалификаторыДаты");
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
      "./src/test/resources/types/TypeDescriptionPatterns.bsl");
  }

  private static java.util.List<String> qnames(TypeSet ts) {
    return ts.refs().stream().map(r -> r.qualifiedName()).toList();
  }
}
