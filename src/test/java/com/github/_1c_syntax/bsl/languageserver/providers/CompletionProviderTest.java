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

import com.github._1c_syntax.bsl.languageserver.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CompletionCapabilities;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemCapabilities;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class CompletionProviderTest extends AbstractServerContextAwareTest {

  @Autowired
  private CompletionProvider completionProvider;

  @Autowired
  private LanguageServerConfiguration languageServerConfiguration;

  @Autowired
  private ClientCapabilitiesHolder clientCapabilitiesHolder;

  @AfterEach
  void resetClientCapabilities() {
    clientCapabilitiesHolder.setCapabilities(null);
    completionProvider.handleInitializeEvent(null);
  }

  private void enableSnippetSupport(boolean enabled) {
    var itemCaps = new CompletionItemCapabilities();
    itemCaps.setSnippetSupport(enabled);
    var completionCaps = new CompletionCapabilities();
    completionCaps.setCompletionItem(itemCaps);
    var textDocumentCaps = new TextDocumentClientCapabilities();
    textDocumentCaps.setCompletion(completionCaps);
    var caps = new ClientCapabilities();
    caps.setTextDocument(textDocumentCaps);
    clientCapabilitiesHolder.setCapabilities(caps);
    completionProvider.handleInitializeEvent(null);
  }

  @Test
  void dotCompletionOnValueTableColumnsPropertyInCombinedScenario() {
    // Trailing-dot после `ТЗ1.Колонки.` плюс следующий statement в той же процедуре.
    // bsl-parser >= 0.34.1 эмитит DOT_TRAILING на висячей точке перед EOL,
    // поэтому такой код корректно разбивается на два statement'а.
    initServerContext("./src/test/resources/providers", false);
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/providers/completion-value-table-combo.bsl", context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // line 2 (0-based) = `\tТЗ1.Колонки.`, символ сразу после второй точки = индекс 13
    params.setPosition(new Position(2, 13));

    var items = completionProvider.getCompletion(documentContext, params).getItems();

    assertThat(items)
      .as("В комбинированном сценарии `ТЗ1.Колонки.` всё равно должен давать члены КоллекцияКолонок")
      .isNotEmpty()
      .extracting(CompletionItem::getLabel)
      .contains("Добавить", "Количество");
  }

  @Test
  void dotCompletionOnValueTableColumnsProperty() {
    // ТЗ1 = Новый ТаблицаЗначений(); ТЗ1.Колонки. — должен дать члены КоллекцияКолонокТаблицыЗначений
    initServerContext("./src/test/resources/providers", false);
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/providers/completion-value-table-columns.bsl", context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // строка `ТЗ1.Колонки.` — позиция сразу после второй точки (line 2, char 12)
    params.setPosition(new Position(2, 12));

    var items = completionProvider.getCompletion(documentContext, params).getItems();

    assertThat(items)
      .as("После ТЗ1.Колонки. должны появиться члены КоллекцияКолонокТаблицыЗначений")
      .isNotEmpty()
      .extracting(CompletionItem::getLabel)
      .contains("Добавить", "Количество");
  }

  @Test
  void dotCompletionOnValueTableAddResultRow() {
    // Строка = ТЗ1.Добавить(); Строка. — должен дать члены СтрокаТаблицыЗначений
    initServerContext("./src/test/resources/providers", false);
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/providers/completion-value-table-add-row.bsl", context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // строка `Строка.` (line 4, idx 3) — позиция сразу после точки (char 7)
    params.setPosition(new Position(3, 7));

    var items = completionProvider.getCompletion(documentContext, params).getItems();

    assertThat(items)
      .as("После Строка. должны появиться члены СтрокаТаблицыЗначений")
      .isNotEmpty()
      .extracting(CompletionItem::getLabel)
      .contains("Владелец");
  }

  @Test
  void testDotCompletionOnArray() {
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/TypeResolver.os", context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // ДругоеИмяМассива.Добавить(1); — позиция сразу после точки на строке 19 (line 18)
    params.setPosition(new Position(18, 17));

    var items = completionProvider.getCompletion(documentContext, params).getItems();

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

    var items = completionProvider.getCompletion(documentContext, params).getItems();
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

    var items = completionProvider.getCompletion(documentContext, params).getItems();

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

    var items = completionProvider.getCompletion(documentContext, params).getItems();

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
  void forEachRowSeesDynamicColumnsAndPlatformMembers() {
    // На итераторе Для Каждого должны быть видны и динамические колонки
    // (Колонки.Добавить), и платформенные члены СтрокаТаблицыЗначений
    // (например, «Владелец») — оба источника обогащают TypeSet строки.
    var content = """
      Процедура Тест()
      \tТЗ = Новый ТаблицаЗначений;
      \tТЗ.Колонки.Добавить("ИмяКолонки");
      \tДля Каждого Строка Из ТЗ Цикл
      \t\tX = Строка.
      \tКонецЦикла;
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // позиция сразу после `Строка.` на 5-й строке (line index 4)
    params.setPosition(new Position(4, "\t\tX = Строка.".length()));

    var items = completionProvider.getCompletion(documentContext, params).getItems();

    assertThat(items)
      .as("Для-Каждого-итератор: и динамическая колонка ИмяКолонки, и platform-член Владелец")
      .extracting(CompletionItem::getLabel)
      .contains("ИмяКолонки", "Владелец");
  }

  @Test
  void forEachStructurePairSeesKeyValuePlatformMembers() {
    // Для Каждого Пара Из Стр Цикл — Пара = КлючИЗначение из defaultElementTypes.
    // На Пара. должны быть видны «Ключ» и «Значение» (платформенные свойства).
    var content = """
      Стр = Новый Структура;
      Стр.Вставить("X", 42);
      Для Каждого Пара Из Стр Цикл
      \tY = Пара.
      КонецЦикла;
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(3, "\tY = Пара.".length()));

    var items = completionProvider.getCompletion(documentContext, params).getItems();

    assertThat(items)
      .as("итератор Структуры — КлючИЗначение, его свойства Ключ и Значение должны быть в completion")
      .extracting(CompletionItem::getLabel)
      .contains("Ключ", "Значение");
  }

  @Test
  void forEachMapPairSeesKeyValuePlatformMembers() {
    // Для Каждого Пара Из Соответствие Цикл — Пара = КлючИЗначение.
    var content = """
      С = Новый Соответствие;
      С.Вставить("X", 42);
      Для Каждого Пара Из С Цикл
      \tY = Пара.
      КонецЦикла;
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(3, "\tY = Пара.".length()));

    var items = completionProvider.getCompletion(documentContext, params).getItems();

    assertThat(items)
      .as("итератор Соответствия — КлючИЗначение, Ключ/Значение должны быть в completion")
      .extracting(CompletionItem::getLabel)
      .contains("Ключ", "Значение");
  }

  @Test
  void forEachValueListItemSeesPlatformMembers() {
    // Для Каждого Эл Из СписокЗначений Цикл — Эл = ЭлементСпискаЗначений.
    var content = """
      СЗ = Новый СписокЗначений;
      СЗ.Добавить("X");
      Для Каждого Эл Из СЗ Цикл
      \tY = Эл.
      КонецЦикла;
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(3, "\tY = Эл.".length()));

    var items = completionProvider.getCompletion(documentContext, params).getItems();

    assertThat(items)
      .as("итератор СпискаЗначений — ЭлементСпискаЗначений, Значение/Представление/Пометка/Картинка")
      .extracting(CompletionItem::getLabel)
      .contains("Значение", "Представление");
  }

  @Test
  void twoValueTablesKeepColumnsIsolated() {
    // Регрессия: колонки одной ТЗ не должны утекать в другую через общий TypeRef
    // ТаблицаЗначений / СтрокаТаблицыЗначений. У каждой переменной свой TypeSet,
    // localFields — на TypeSet, а не на TypeRef.
    var content = """
      Процедура Тест()
      \tТЗ1 = Новый ТаблицаЗначений;
      \tТЗ1.Колонки.Добавить("КолонкаА");
      \tТЗ2 = Новый ТаблицаЗначений;
      \tТЗ2.Колонки.Добавить("КолонкаБ");
      \tСтрока1 = ТЗ1.Добавить();
      \tСтрока2 = ТЗ2.Добавить();
      \tX1 = Строка1.
      \tX2 = Строка2.
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var paramsRow1 = new CompletionParams();
    paramsRow1.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    paramsRow1.setPosition(new Position(7, "\tX1 = Строка1.".length()));
    var labelsRow1 = completionProvider.getCompletion(documentContext, paramsRow1).getItems()
      .stream().map(CompletionItem::getLabel).toList();

    assertThat(labelsRow1)
      .as("Строка1 — колонка А есть")
      .contains("КолонкаА")
      .as("Строка1 — колонка из ТЗ2 не должна утечь")
      .doesNotContain("КолонкаБ");

    var paramsRow2 = new CompletionParams();
    paramsRow2.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    paramsRow2.setPosition(new Position(8, "\tX2 = Строка2.".length()));
    var labelsRow2 = completionProvider.getCompletion(documentContext, paramsRow2).getItems()
      .stream().map(CompletionItem::getLabel).toList();

    assertThat(labelsRow2)
      .as("Строка2 — колонка Б есть")
      .contains("КолонкаБ")
      .as("Строка2 — колонка из ТЗ1 не должна утечь")
      .doesNotContain("КолонкаА");
  }

  @Test
  void twoStructuresKeepKeysIsolated() {
    // Аналогично для Структуры: ключи Вставить накапливаются на TypeSet
    // конкретной переменной, через общий TypeRef Структура их не «склеить».
    var content = """
      Стр1 = Новый Структура;
      Стр1.Вставить("КлючА", 1);
      Стр2 = Новый Структура;
      Стр2.Вставить("КлючБ", 2);
      A = Стр1.
      B = Стр2.
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var paramsA = new CompletionParams();
    paramsA.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    paramsA.setPosition(new Position(4, "A = Стр1.".length()));
    var labelsA = completionProvider.getCompletion(documentContext, paramsA).getItems()
      .stream().map(CompletionItem::getLabel).toList();

    assertThat(labelsA)
      .as("Стр1 — ключ А есть")
      .contains("КлючА")
      .as("Стр1 — ключ из Стр2 не должен утечь")
      .doesNotContain("КлючБ");

    var paramsB = new CompletionParams();
    paramsB.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    paramsB.setPosition(new Position(5, "B = Стр2.".length()));
    var labelsB = completionProvider.getCompletion(documentContext, paramsB).getItems()
      .stream().map(CompletionItem::getLabel).toList();

    assertThat(labelsB)
      .as("Стр2 — ключ Б есть")
      .contains("КлючБ")
      .as("Стр2 — ключ из Стр1 не должен утечь")
      .doesNotContain("КлючА");
  }

  @Test
  void dotCompletionOnAddRowRowSeesDynamicColumns() {
    // Регрессия: НоваяСтрока = ТЗ.Добавить() + НоваяСтрока. — в выпадашке должны
    // быть колонки, заявленные ТЗ.Колонки.Добавить("X", ...). Раньше Добавить()
    // отдавал «голый» СтрокаТаблицыЗначений без localFields, и колонки терялись.
    var content = """
      Процедура Тест()
      \tТЗ = Новый ТаблицаЗначений;
      \tТЗ.Колонки.Добавить("ИмяКолонки", Новый ОписаниеТипов("Число"));
      \tНоваяСтрока = ТЗ.Добавить();
      \tX = НоваяСтрока.
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // позиция сразу после точки в `X = НоваяСтрока.`
    params.setPosition(new Position(4, "\tX = НоваяСтрока.".length()));

    var items = completionProvider.getCompletion(documentContext, params).getItems();

    assertThat(items)
      .as("completion на строке, полученной из ТЗ.Добавить(), должен показывать декларированные колонки ТЗ")
      .extracting(CompletionItem::getLabel)
      .contains("ИмяКолонки");
  }

  @Test
  void getCompletionReturnsCompletionListMarkedComplete() {
    // Контракт: getCompletion возвращает CompletionList с isIncomplete=false —
    // клиент может фильтровать локально, повторного запроса не требуется.
    var content = "Сооб";
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(0, 4));

    var result = completionProvider.getCompletion(documentContext, params);

    assertThat(result).isNotNull();
    assertThat(result.isIncomplete()).isFalse();
    assertThat(result.getItems()).isNotEmpty();
  }

  @Test
  void methodInsertTextFallsBackToOpenParenWithoutSnippetSupport() {
    // Дефолтное поведение для клиентов без snippetSupport: один `(`, как и раньше.
    var content = "Сооб";
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(0, 4));

    var items = completionProvider.getCompletion(documentContext, params).getItems();
    var message = items.stream()
      .filter(it -> "Сообщить".equals(it.getLabel()))
      .findFirst()
      .orElseThrow();

    assertThat(message.getInsertText()).isEqualTo("Сообщить(");
    assertThat(message.getInsertTextFormat()).isNull();
    assertThat(message.getCommand()).isNull();
  }

  @Test
  void methodInsertTextIsSnippetWhenClientSupportsSnippets() {
    // `Сообщить($0)` как сниппет + команда triggerParameterHints для всплытия signatureHelp.
    enableSnippetSupport(true);

    var content = "Сооб";
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(0, 4));

    var items = completionProvider.getCompletion(documentContext, params).getItems();
    var message = items.stream()
      .filter(it -> "Сообщить".equals(it.getLabel()))
      .findFirst()
      .orElseThrow();

    assertThat(message.getInsertText()).isEqualTo("Сообщить($0)");
    assertThat(message.getInsertTextFormat()).isEqualTo(InsertTextFormat.Snippet);
    assertThat(message.getCommand()).isNotNull();
    assertThat(message.getCommand().getCommand()).isEqualTo("editor.action.triggerParameterHints");
  }

  @Test
  void dotCompletionMethodUsesSnippetWhenClientSupportsSnippets() {
    enableSnippetSupport(true);
    initServerContext("./src/test/resources/providers", false);
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/providers/completion-value-table-columns.bsl", context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(2, 12));

    var items = completionProvider.getCompletion(documentContext, params).getItems();
    var add = items.stream()
      .filter(it -> "Добавить".equals(it.getLabel()))
      .findFirst()
      .orElseThrow();

    assertThat(add.getInsertText()).isEqualTo("Добавить($0)");
    assertThat(add.getInsertTextFormat()).isEqualTo(InsertTextFormat.Snippet);
    assertThat(add.getCommand()).isNotNull();
    assertThat(add.getCommand().getCommand()).isEqualTo("editor.action.triggerParameterHints");
  }

  @Test
  void classCompletionAfterNovyiUsesSnippetWhenClientSupportsSnippets() {
    enableSnippetSupport(true);

    var content = "А = Новый Масс";
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(0, content.length()));

    var items = completionProvider.getCompletion(documentContext, params).getItems();
    var array = items.stream()
      .filter(it -> "Массив".equals(it.getLabel()))
      .findFirst()
      .orElseThrow();

    assertThat(array.getInsertText()).isEqualTo("Массив($0)");
    assertThat(array.getInsertTextFormat()).isEqualTo(InsertTextFormat.Snippet);
    assertThat(array.getCommand()).isNotNull();
    assertThat(array.getCommand().getCommand()).isEqualTo("editor.action.triggerParameterHints");
  }

  @Test
  void dotCompletionMethodDetailHoldsSignatureNotDescription() {
    // Регрессия: раньше purposeDescription дублировался в detail и в documentation
    // → VS Code показывал одну и ту же фразу дважды в подсказке. Теперь detail для
    // метода — это сигнатура (имена параметров) или счётчик вариантов, а описание
    // живёт только в documentation.
    initServerContext("./src/test/resources/providers", false);
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/providers/completion-value-table-columns.bsl", context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // позиция сразу после второй точки в `ТЗ1.Колонки.` — члены КоллекцияКолонокТаблицыЗначений
    params.setPosition(new Position(2, 12));

    var items = completionProvider.getCompletion(documentContext, params).getItems();

    var add = items.stream()
      .filter(it -> "Добавить".equals(it.getLabel()))
      .findFirst()
      .orElseThrow();

    var detail = add.getDetail();
    assertThat(detail).as("detail метода должен быть проставлен — сигнатура или счётчик вариантов")
      .isNotNull().isNotBlank();
    var detailMatchesSignature = detail.startsWith("(");
    var detailMatchesMultiSignaturesCount = detail.endsWith("синтаксиса");
    assertThat(detailMatchesSignature || detailMatchesMultiSignaturesCount)
      .as("detail метода — либо `(...)`, либо `N вариантов синтаксиса`, а не текст описания. Получили: %s", detail)
      .isTrue();
  }

  @Test
  void dotCompletionPropertyDetailHoldsTypeNameNotDescription() {
    // Свойство: detail должно быть именем типа (например, "КоллекцияКолонокТаблицыЗначений"),
    // а не дублировать текст описания.
    initServerContext("./src/test/resources/providers", false);
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/providers/completion-properties.os", context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // позиция сразу после `ТЗ.`
    params.setPosition(new Position(1, 3));

    var items = completionProvider.getCompletion(documentContext, params).getItems();

    var columns = items.stream()
      .filter(it -> "Колонки".equals(it.getLabel()))
      .findFirst()
      .orElseThrow();

    var detail = columns.getDetail();
    if (detail != null && !detail.isBlank()) {
      assertThat(detail.length())
        .as("detail свойства — короткое имя типа, а не предложение из описания. Получили: %s", detail)
        .isLessThanOrEqualTo(60);
      assertThat(detail).doesNotContain(" ");
    }
  }

  @Test
  void dotCompletionMemberDetailDoesNotDuplicateDocumentation() {
    // Сквозная проверка: ни у одного item из dot-completion detail не равен documentation
    // (именно так выглядел баг с двойным описанием в подсказке VS Code).
    initServerContext("./src/test/resources/providers", false);
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/providers/completion-properties.os", context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(1, 3));

    var items = completionProvider.getCompletion(documentContext, params).getItems();

    assertThat(items).isNotEmpty();
    for (var it : items) {
      var detail = it.getDetail();
      if (detail == null || detail.isBlank()) {
        continue;
      }
      var doc = it.getDocumentation();
      if (doc == null) {
        continue;
      }
      var docText = doc.isLeft() ? doc.getLeft() : doc.getRight().getValue();
      assertThat(detail)
        .as("detail не должен повторять documentation для %s", it.getLabel())
        .isNotEqualTo(docText);
    }
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

    var items = completionProvider.getCompletion(documentContext, params).getItems();

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

    var items = completionProvider.getCompletion(documentContext, params).getItems();

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

    var items = completionProvider.getCompletion(documentContext, params).getItems();

    assertThat(items)
      .as("bare class name should not produce instance-member completion")
      .extracting(CompletionItem::getLabel)
      .doesNotContain("Вставить", "Insert", "Удалить", "Delete");
  }

  @Test
  void testDotCompletionFiltersMembersByConfiguredLanguageRu() {
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/TypeResolver.os", context);

    languageServerConfiguration.setLanguage(Language.RU);
    try {
      var params = new CompletionParams();
      params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
      params.setPosition(new Position(18, 17));

      var items = completionProvider.getCompletion(documentContext, params).getItems();

      assertThat(items).extracting(CompletionItem::getLabel)
        .contains("Добавить", "Вставить", "Удалить", "Найти")
        .doesNotContain("Add", "Insert", "Delete", "Find");
    } finally {
      languageServerConfiguration.setLanguage(Language.DEFAULT_LANGUAGE);
    }
  }

  @Test
  void testDotCompletionFiltersMembersByConfiguredLanguageEn() {
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/TypeResolver.os", context);

    languageServerConfiguration.setLanguage(Language.EN);
    try {
      var params = new CompletionParams();
      params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
      params.setPosition(new Position(18, 17));

      var items = completionProvider.getCompletion(documentContext, params).getItems();

      assertThat(items).extracting(CompletionItem::getLabel)
        .contains("Add", "Insert", "Delete", "Find")
        .doesNotContain("Добавить", "Вставить", "Удалить", "Найти");
    } finally {
      languageServerConfiguration.setLanguage(Language.DEFAULT_LANGUAGE);
    }
  }

  @Test
  void dotCompletionExposesStructureKeysFromConstructor() {
    // Размещение = Новый Структура("Варианты, Действие, Приемник, Источник");
    // Y = Размещение.В;  → completion после точки с prefix "В" должен включать
    // и ключ "Варианты" из конструктора, и дефолтный метод "Вставить".
    initServerContext("./src/test/resources/providers", false);
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/providers/completion-structure-keys.bsl", context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var content = documentContext.getContent();
    int dotOffset = content.indexOf("Размещение.В");
    int afterPrefix = dotOffset + "Размещение.В".length();
    int lineStart = content.lastIndexOf('\n', afterPrefix) + 1;
    int line = content.substring(0, afterPrefix).split("\n").length - 1;
    int character = afterPrefix - lineStart;
    params.setPosition(new Position(line, character));

    var items = completionProvider.getCompletion(documentContext, params).getItems();

    assertThat(items)
      .extracting(CompletionItem::getLabel)
      .as("по prefix 'В' должны быть и ключ из конструктора 'Варианты', и метод 'Вставить'")
      .contains("Варианты", "Вставить");
  }

  @Test
  void dotCompletionExposesAllStructureKeysWithoutPrefix() {
    initServerContext("./src/test/resources/providers", false);
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/providers/completion-structure-keys.bsl", context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var content = documentContext.getContent();
    int dotOffset = content.indexOf("Размещение.В");
    int afterDot = dotOffset + "Размещение.".length();
    int lineStart = content.lastIndexOf('\n', afterDot) + 1;
    int line = content.substring(0, afterDot).split("\n").length - 1;
    int character = afterDot - lineStart;
    params.setPosition(new Position(line, character));

    var items = completionProvider.getCompletion(documentContext, params).getItems();

    assertThat(items)
      .extracting(CompletionItem::getLabel)
      .as("все ключи структуры должны быть в completion")
      .contains("Варианты", "Действие", "Приемник", "Источник");
  }
}
