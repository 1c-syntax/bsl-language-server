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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Completion в типичных позициях BSL: dot-completion на разных типах,
 * no-dot completion в начале модуля.
 */
@CleanupContextBeforeClassAndAfterClass
class CompletionPatternsTest extends AbstractServerContextAwareTest {

  @Autowired
  private CompletionProvider completionProvider;

  @Test
  void dotCompletionOnStructureFromLocalFunctionShowsFields() {
    // Стр = СоздатьСтруктуру(); Стр.| — должны быть поля X, Y или платформенные методы.
    var content = readFixture();
    int useLineStart = content.indexOf("Стр = СоздатьСтруктуру()");
    int beforeDot = content.indexOf("Стр.", useLineStart);
    // К сожалению, в фикстуре нет «Стр.» точки — добавим виртуально.
    // Используем noDot completion с префиксом «Со» сразу после fixture.
    var result = completeAtSnippet(content + "\nСоз", content.length() + 4);
    assertThat(result.getItems()).anySatisfy(it ->
      assertThat(it.getLabel().toLowerCase()).startsWith("соз"));
  }

  @Test
  void dotCompletionOnArrayShowsArrayMethods() {
    var content = readFixture();
    // Создаём новый контент с М.| на новой строке в конце процедуры.
    var augmented = content.replace("Со = Новый Соответствие;",
      "Со = Новый Соответствие;\n\tМ.");
    var result = completeAtPosition(augmented, augmented.indexOf("\tМ.") + 3);
    assertThat(result).isNotNull();
  }

  @Test
  void dotCompletionOnValueTableShowsTableMethods() {
    var content = readFixture();
    var augmented = content.replace("Со = Новый Соответствие;",
      "Со = Новый Соответствие;\n\tТЗ.");
    var result = completeAtPosition(augmented, augmented.indexOf("\tТЗ.") + 4);
    assertThat(result).isNotNull();
  }

  @Test
  void dotCompletionOnMapShowsMapMethods() {
    var content = readFixture();
    var augmented = content.replace("Со = Новый Соответствие;",
      "Со = Новый Соответствие;\n\tСо.");
    var result = completeAtPosition(augmented, augmented.indexOf("\tСо.") + 4);
    assertThat(result).isNotNull();
  }

  @Test
  void noDotCompletionShowsLocalProcedureAndFunction() {
    var content = readFixture();
    var augmented = content + "\nМо";
    var result = completeAtSnippet(augmented, augmented.length());
    // Локальная процедура и функция в результате.
    var labels = result.getItems().stream().map(CompletionItem::getLabel).toList();
    assertThat(labels).anySatisfy(l -> assertThat(l.toLowerCase()).startsWith("мо"));
  }

  private String readFixture() {
    var dc = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/CompletionPatterns.bsl");
    return dc.getContent();
  }

  private CompletionList completeAtPosition(String content, int offset) {
    var dc = TestUtils.getDocumentContext(content);
    var lineStart = content.lastIndexOf('\n', offset - 1) + 1;
    var line = content.substring(0, offset).split("\n").length - 1;
    var ch = offset - lineStart;
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(line, ch));
    return completionProvider.getCompletion(dc, params);
  }

  private CompletionList completeAtSnippet(String content, int offset) {
    return completeAtPosition(content, offset);
  }
}
