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
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Completion после ключевого слова Новый: с разными префиксами, без префикса,
 * на разной глубине вложенности.
 */
@CleanupContextBeforeClassAndAfterClass
class AfterNewCompletionTest extends AbstractServerContextAwareTest {

  @Autowired
  private CompletionProvider completionProvider;

  @Test
  void afterNewSpacePrefixЗ() {
    var content = "Х = Новый З";
    var result = completeAt(content, content.length());
    assertThat(result).isNotNull();
    assertThat(result.getItems()).anySatisfy(it ->
      assertThat(it.getLabel().toLowerCase()).startsWith("з"));
  }

  @Test
  void afterNewSpacePrefixСт() {
    var content = "Х = Новый Ст";
    var result = completeAt(content, content.length());
    assertThat(result).isNotNull();
    // Структура, Строка, etc.
    assertThat(result.getItems()).anySatisfy(it ->
      assertThat(it.getLabel().toLowerCase()).startsWith("ст"));
  }

  @Test
  void afterNewSpaceEmpty() {
    var content = "Х = Новый ";
    var result = completeAt(content, content.length());
    assertThat(result).isNotNull();
    assertThat(result.getItems()).isNotEmpty();
  }

  @Test
  void completionInsideMethodCall() {
    var content = "Параметры = Новый Структура;\nСообщить(";
    var result = completeAt(content, content.length());
    assertThat(result).isNotNull();
  }

  @Test
  void completionAfterDotOnVariable() {
    var content = "А = Новый Массив;\nА.";
    var result = completeAt(content, content.length());
    assertThat(result).isNotNull();
  }

  private CompletionList completeAt(String content, int offset) {
    var dc = TestUtils.getDocumentContext(content);
    var lineStart = content.lastIndexOf('\n', offset - 1) + 1;
    var line = content.substring(0, offset).split("\n").length - 1;
    var ch = offset - lineStart;
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(line, ch));
    return completionProvider.getCompletion(dc, params);
  }
}
