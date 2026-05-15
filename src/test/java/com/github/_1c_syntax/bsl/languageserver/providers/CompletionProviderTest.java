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
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class CompletionProviderTest extends AbstractServerContextAwareTest {

  @Autowired
  private CompletionProvider completionProvider;

  @Test
  void testDotCompletionOnArray() {
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/TypeResolver.os", context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // ДругоеИмяМассива.Добавить(1); — позиция сразу после точки на строке 19 (line 18)
    params.setPosition(new Position(18, 17));

    var items = completionProvider.getCompletion(documentContext, params);

    assertThat(items)
      .isNotEmpty()
      .extracting(CompletionItem::getLabel)
      .contains("Добавить");
  }

  @Test
  void testNoDotCompletionReturnsGlobals() {
    var content = "Сооб";
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(0, 4));

    var items = completionProvider.getCompletion(documentContext, params);
    assertThat(items)
      .extracting(CompletionItem::getLabel)
      .contains("Сообщить");
  }

  @Test
  void testDotCompletionReturnsPropertiesAsProperties() {
    // ТЗ = Новый ТаблицаЗначений;
    // ТЗ.Колонки.Добавить("Имя");
    initServerContext("./src/test/resources/providers", false);
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/providers/completion-properties.os", context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // позиция сразу после "ТЗ." на строке 2 (line 1)
    params.setPosition(new Position(1, 3));

    var items = completionProvider.getCompletion(documentContext, params);

    // и поля (PROPERTY → CompletionItemKind.Property), и методы доступны
    assertThat(items).isNotEmpty();
    var byName = items.stream().collect(java.util.stream.Collectors.toMap(
      CompletionItem::getLabel, item -> item, (a, b) -> a));

    assertThat(byName).containsKey("Колонки");
    assertThat(byName.get("Колонки").getKind()).isEqualTo(CompletionItemKind.Property);

    assertThat(byName).containsKey("Количество");
    assertThat(byName.get("Количество").getKind()).isEqualTo(CompletionItemKind.Method);
  }

  @Test
  void testDotCompletionMembersHaveCorrectKinds() {
    // sanity: проверяем, что и Method, и Property kinds реально мапятся
    initServerContext("./src/test/resources/providers", false);
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/providers/completion-properties.os", context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(1, 3));

    var items = completionProvider.getCompletion(documentContext, params);

    assertThat(items)
      .extracting(CompletionItem::getKind)
      .contains(CompletionItemKind.Property, CompletionItemKind.Method);

    // и Method, и Property — оба представлены в выдаче, как и в реестре
    var anyProperty = items.stream().anyMatch(it -> it.getKind() == CompletionItemKind.Property);
    var anyMethod = items.stream().anyMatch(it -> it.getKind() == CompletionItemKind.Method);
    assertThat(anyProperty).isTrue();
    assertThat(anyMethod).isTrue();

    // и MemberKind enum, через который мапим, действительно покрывает оба
    assertThat(MemberKind.values()).contains(MemberKind.METHOD, MemberKind.PROPERTY);
  }

  @Test
  void dotCompletionFiltersByPrefix() {
    // Кодировка = КодировкаТекста.UTF8;  →  при курсоре после "UT" даёт только "UTF8"
    var content = "Кодировка = КодировкаТекста.UT;";
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // курсор сразу после "UT" — между "T" и ";"
    params.setPosition(new Position(0, 29));

    var items = completionProvider.getCompletion(documentContext, params);

    assertThat(items)
      .isNotEmpty()
      .extracting(CompletionItem::getLabel)
      .allMatch(label -> label.toLowerCase().startsWith("ut"))
      .contains("UTF8");
  }

  @Test
  void noDotCompletionExposesPlatformGlobalVariables() {
    var content = "Библ";
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(0, 4));

    var items = completionProvider.getCompletion(documentContext, params);

    var byName = items.stream().collect(java.util.stream.Collectors.toMap(
      CompletionItem::getLabel, item -> item, (a, b) -> a));

    assertThat(byName).containsKey("БиблиотекаКартинок");
    assertThat(byName.get("БиблиотекаКартинок").getKind()).isEqualTo(CompletionItemKind.Variable);
    assertThat(byName).containsKey("БиблиотекаСтилей");
    assertThat(byName.get("БиблиотекаСтилей").getKind()).isEqualTo(CompletionItemKind.Variable);
  }

  @Test
  void testDotCompletionOnBareClassNameDoesNotReturnClassMembers() {
    // Bare class identifier (no variable declaration) must not yield its instance members.
    // E.g. `Структура.` at module top — `Структура` is a class name, not a value.
    var content = "Структура.";
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(0, 10));

    var items = completionProvider.getCompletion(documentContext, params);

    assertThat(items)
      .as("bare class name should not produce instance-member completion")
      .extracting(CompletionItem::getLabel)
      .doesNotContain("Вставить", "Insert", "Удалить", "Delete");
  }
}
