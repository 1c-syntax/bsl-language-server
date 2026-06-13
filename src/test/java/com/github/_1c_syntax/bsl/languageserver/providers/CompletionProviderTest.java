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
import org.eclipse.lsp4j.CompletionItemResolveSupportCapabilities;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
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
    completionProvider.handleInitializeEvent();
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
    completionProvider.handleInitializeEvent();
  }

  private void enableMarkdownDocumentation(boolean enabled) {
    var itemCaps = new CompletionItemCapabilities();
    itemCaps.setDocumentationFormat(enabled ? List.of(MarkupKind.MARKDOWN) : List.of(MarkupKind.PLAINTEXT));
    var completionCaps = new CompletionCapabilities();
    completionCaps.setCompletionItem(itemCaps);
    var textDocumentCaps = new TextDocumentClientCapabilities();
    textDocumentCaps.setCompletion(completionCaps);
    var caps = new ClientCapabilities();
    caps.setTextDocument(textDocumentCaps);
    clientCapabilitiesHolder.setCapabilities(caps);
    completionProvider.handleInitializeEvent();
  }

  private void enableLabelDetailsSupport(boolean enabled) {
    var itemCaps = new CompletionItemCapabilities();
    itemCaps.setLabelDetailsSupport(enabled);
    var completionCaps = new CompletionCapabilities();
    completionCaps.setCompletionItem(itemCaps);
    var textDocumentCaps = new TextDocumentClientCapabilities();
    textDocumentCaps.setCompletion(completionCaps);
    var caps = new ClientCapabilities();
    caps.setTextDocument(textDocumentCaps);
    clientCapabilitiesHolder.setCapabilities(caps);
    completionProvider.handleInitializeEvent();
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
  void dotCompletionOnMidCallDotOfCommonModuleReturnsModuleMembers() {
    // #3991: автодополнение прямо на точке внутри уже завершённого вызова
    // общего модуля — `ОбщегоНазначения.|ОбщийМодуль("Имя")` — должно давать
    // члены модуля (тип ресивера), а не тип возврата вызова.
    // given
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/CommonModuleMidCallCompletion.bsl");

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // строка `\tОбщегоНазначения.ОбщийМодуль("Имя");` — позиция сразу после точки
    // (таб + 16 символов имени модуля + точка → член начинается с char 18)
    params.setPosition(new Position(1, 18));

    // when
    var items = completionProvider.getCompletion(documentContext, params).getItems();

    // then
    assertThat(items)
      .as("mid-call точка должна предлагать члены общего модуля, а не тип возврата вызова")
      .isNotEmpty()
      .extracting(CompletionItem::getLabel)
      .contains("ОбщийМодуль", "ЗначениеВМассиве");
  }

  @Test
  void dotCompletionOnCatalogManagerReturnsPredefinedValues() {
    // Автодополнение после `Справочники.Справочник1.` должно предлагать
    // предопределённые значения справочника (включая вложенные в группах).
    // given
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/PredefinedValuesCompletion.bsl");

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // строка `Справочники.Справочник1.` — позиция сразу после завершающей точки
    params.setPosition(new Position(1, 24));

    // when
    var items = completionProvider.getCompletion(documentContext, params).getItems();

    // then
    assertThat(items)
      .as("после `Справочники.Справочник1.` должны предлагаться предопределённые значения")
      .isNotEmpty()
      .extracting(CompletionItem::getLabel)
      .contains(
        "ПредопределённыйЭлемент1",
        "ПредопределённыйЭлемент2",
        "ПредопределённаяГруппа",
        "ВложенныйЭлемент");
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
      ТЗ = Новый ТаблицаЗначений;
      ТЗ.Колонки.Добавить("ИмяКолонки");
      Для Каждого Строка Из ТЗ Цикл
      X = Строка.
      КонецЦикла;
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(4, "X = Строка.".length()));

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
      Y = Пара.
      КонецЦикла;
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(3, "Y = Пара.".length()));

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
      Y = Пара.
      КонецЦикла;
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(3, "Y = Пара.".length()));

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
      Y = Эл.
      КонецЦикла;
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(3, "Y = Эл.".length()));

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
      ТЗ1 = Новый ТаблицаЗначений;
      ТЗ1.Колонки.Добавить("КолонкаА");
      ТЗ2 = Новый ТаблицаЗначений;
      ТЗ2.Колонки.Добавить("КолонкаБ");
      Строка1 = ТЗ1.Добавить();
      Строка2 = ТЗ2.Добавить();
      X1 = Строка1.
      X2 = Строка2.
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var paramsRow1 = new CompletionParams();
    paramsRow1.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    paramsRow1.setPosition(new Position(7, "X1 = Строка1.".length()));
    var labelsRow1 = completionProvider.getCompletion(documentContext, paramsRow1).getItems()
      .stream().map(CompletionItem::getLabel).toList();

    assertThat(labelsRow1)
      .as("Строка1 — колонка А есть")
      .contains("КолонкаА")
      .as("Строка1 — колонка из ТЗ2 не должна утечь")
      .doesNotContain("КолонкаБ");

    var paramsRow2 = new CompletionParams();
    paramsRow2.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    paramsRow2.setPosition(new Position(8, "X2 = Строка2.".length()));
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
  void indexAccessOnValueTableYieldsRowMembers() {
    // ТЗ[0].КолонкаХ — индексатор должен возвращать СтрокаТаблицыЗначений
    // с теми же localFields, что и Для-Каждого-итератор / .Добавить()-row.
    var content = """
      Процедура Тест()
      ТЗ = Новый ТаблицаЗначений;
      ТЗ.Колонки.Добавить("МояКолонка");
      X = ТЗ[0].
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(3, "X = ТЗ[0].".length()));

    var items = completionProvider.getCompletion(documentContext, params).getItems();

    assertThat(items)
      .as("ТЗ[0].<dot> — динамическая колонка + platform-member СтрокиТЗ")
      .extracting(CompletionItem::getLabel)
      .contains("МояКолонка", "Владелец");
  }

  @Test
  void indexAccessOnValueListItemHasPlatformMembers() {
    // СписокЗначений[i].Значение — обычный элемент списка
    var content = """
      СЗ = Новый СписокЗначений;
      X = СЗ[0].
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(1, "X = СЗ[0].".length()));

    var items = completionProvider.getCompletion(documentContext, params).getItems();

    assertThat(items)
      .as("СЗ[i].<dot> — ЭлементСпискаЗначений с Значение/Представление")
      .extracting(CompletionItem::getLabel)
      .contains("Значение", "Представление");
  }

  @Test
  void indexAccessOnColumnsCollectionYieldsColumn() {
    // ТЗ.Колонки[0].Имя — индексатор на коллекции колонок возвращает КолонкаТаблицыЗначений
    var content = """
      ТЗ = Новый ТаблицаЗначений;
      X = ТЗ.Колонки[0].
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(1, "X = ТЗ.Колонки[0].".length()));

    var items = completionProvider.getCompletion(documentContext, params).getItems();

    assertThat(items)
      .as("Колонки[0].<dot> — КолонкаТаблицыЗначений: Имя, Заголовок, Ширина")
      .extracting(CompletionItem::getLabel)
      .contains("Имя", "Заголовок");
  }

  @Test
  void indexAccessOnStructureWithStringLiteralKeyReturnsValueMembers() {
    // Стр["X"] — значение по ключу, у которого тип неприметивный → его dot-completion
    // показывает реальные члены этого типа, а не КлючИЗначение.
    var content = """
      Стр = Новый Структура;
      Стр.Вставить("X", Новый Массив);
      Y = Стр["X"].
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(2, "Y = Стр[\"X\"].".length()));

    var items = completionProvider.getCompletion(documentContext, params).getItems();
    var labels = items.stream().map(CompletionItem::getLabel).toList();

    assertThat(labels)
      .as("Стр[\"X\"] — значение Массив, видны его методы Добавить/Количество")
      .contains("Добавить", "Количество")
      .as("Стр[\"X\"] — не должны лезть свойства КлючИЗначение")
      .doesNotContain("Ключ", "Значение");
  }

  @Test
  void indexAccessOnMapWithStringLiteralKeyReturnsValueMembers() {
    // То же для Соответствие — accumulator теперь захватывает и .Вставить
    // на Соответствии (раньше работал только для Структуры).
    var content = """
      С = Новый Соответствие;
      С.Вставить("X", Новый ТаблицаЗначений);
      Y = С["X"].
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(2, "Y = С[\"X\"].".length()));

    var items = completionProvider.getCompletion(documentContext, params).getItems();
    var labels = items.stream().map(CompletionItem::getLabel).toList();

    assertThat(labels)
      .as("С[\"X\"] — значение ТаблицаЗначений, видны её свойства/методы")
      .contains("Колонки")
      .as("С[\"X\"] — не должны лезть свойства КлючИЗначение")
      .doesNotContain("Ключ", "Значение");
  }

  @Test
  void indexAccessOnStructureWithUnknownKeyReturnsEmpty() {
    // Стр["UnknownKey"] — ключа нет, value-тип неизвестен, completion пуст
    // (а не отдаёт ошибочно КлючИЗначение).
    var content = """
      Стр = Новый Структура;
      Стр.Вставить("X", Новый Массив);
      Y = Стр["Other"].
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(2, "Y = Стр[\"Other\"].".length()));

    var items = completionProvider.getCompletion(documentContext, params).getItems();

    assertThat(items)
      .as("Стр[\"Other\"] — неизвестный ключ, выпадашка пуста")
      .isEmpty();
  }

  @Test
  void indexAccessOnStructureWithDynamicKeyUnionsValueMembers() {
    // Стр[ключ] — индекс не литерал, value-тип = union по всем известным ключам.
    var content = """
      Стр = Новый Структура;
      Стр.Вставить("X", Новый Массив);
      Стр.Вставить("Y", Новый ТаблицаЗначений);
      Ключ = "X";
      Z = Стр[Ключ].
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(4, "Z = Стр[Ключ].".length()));

    var items = completionProvider.getCompletion(documentContext, params).getItems();
    var labels = items.stream().map(CompletionItem::getLabel).toList();

    assertThat(labels)
      .as("динамический индекс — union value-типов: видны члены и Массива, и ТЗ")
      .contains("Добавить", "Колонки");
  }

  @Test
  void dotCompletionOnAddRowRowSeesDynamicColumns() {
    // Регрессия: НоваяСтрока = ТЗ.Добавить() + НоваяСтрока. — в выпадашке должны
    // быть колонки, заявленные ТЗ.Колонки.Добавить("X", ...). Раньше Добавить()
    // отдавал «голый» СтрокаТаблицыЗначений без localFields, и колонки терялись.
    var content = """
      Процедура Тест()
      ТЗ = Новый ТаблицаЗначений;
      ТЗ.Колонки.Добавить("ИмяКолонки", Новый ОписаниеТипов("Число"));
      НоваяСтрока = ТЗ.Добавить();
      X = НоваяСтрока.
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(4, "X = НоваяСтрока.".length()));

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
  void classWithSingleParameterlessConstructorInsertsClosedParens() {
    // ТаблицаЗначений имеет ровно один беспараметровый конструктор (из builtin-данных) —
    // вставляем готовые `()` и оставляем курсор после них.
    enableSnippetSupport(true);

    var content = "А = Новый ТаблицаЗнач";
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(0, content.length()));

    var item = completionProvider.getCompletion(documentContext, params).getItems().stream()
      .filter(it -> "ТаблицаЗначений".equals(it.getLabel()))
      .findFirst()
      .orElseThrow();

    assertThat(item.getInsertText()).isEqualTo("ТаблицаЗначений()");
    assertThat(item.getInsertTextFormat()).isNull();
    assertThat(item.getCommand()).isNull();
  }

  @Test
  void classWithMultipleConstructorOverloadsStaysSnippet() {
    // Структура объявляет несколько перегрузок конструктора — даже если первая
    // могла бы оказаться беспараметровой, консервативно оставляем курсор между скобок.
    enableSnippetSupport(true);

    var content = "А = Новый Структ";
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(0, content.length()));

    var item = completionProvider.getCompletion(documentContext, params).getItems().stream()
      .filter(it -> "Структура".equals(it.getLabel()))
      .findFirst()
      .orElseThrow();

    assertThat(item.getInsertText()).isEqualTo("Структура($0)");
    assertThat(item.getInsertTextFormat()).isEqualTo(InsertTextFormat.Snippet);
    assertThat(item.getCommand()).isNotNull();
  }

  @Test
  void methodWithoutParametersInsertsClosedParensWithSnippetSupport() {
    // Метод без параметров: даже при snippetSupport вставляем готовые `()` и оставляем
    // курсор после них — между скобок вводить нечего, signatureHelp поднимать незачем.
    enableSnippetSupport(true);

    var content = "ТекущаяДат";
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(0, content.length()));

    var items = completionProvider.getCompletion(documentContext, params).getItems();
    var currentDate = items.stream()
      .filter(it -> "ТекущаяДата".equals(it.getLabel()))
      .findFirst()
      .orElseThrow();

    assertThat(currentDate.getInsertText()).isEqualTo("ТекущаяДата()");
    assertThat(currentDate.getInsertTextFormat()).isNull();
    assertThat(currentDate.getCommand()).isNull();
  }

  @Test
  void methodWithoutParametersInsertsClosedParensWithoutSnippetSupport() {
    // Без snippetSupport метод без параметров тоже вставляется с закрытой скобкой `()`,
    // а не одиночной `(`: дописывать пользователю нечего.
    var content = "ТекущаяДат";
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(0, content.length()));

    var items = completionProvider.getCompletion(documentContext, params).getItems();
    var currentDate = items.stream()
      .filter(it -> "ТекущаяДата".equals(it.getLabel()))
      .findFirst()
      .orElseThrow();

    assertThat(currentDate.getInsertText()).isEqualTo("ТекущаяДата()");
    assertThat(currentDate.getInsertTextFormat()).isNull();
    assertThat(currentDate.getCommand()).isNull();
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
    if (detailMatchesSignature) {
      // Необязательные параметры теперь помечаются «?», а не квадратными скобками.
      assertThat(detail)
        .as("необязательные параметры — через «?», без квадратных скобок. Получили: %s", detail)
        .doesNotContain("[").doesNotContain("]");
    }
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
    var instanceMemberLabels = items.stream()
      .map(CompletionItem::getLabel)
      .filter(Set.of("Вставить", "Insert", "Удалить", "Delete")::contains)
      .toList();

    assertThat(instanceMemberLabels)
      .as("bare class name should not produce instance-member completion")
      .isEmpty();
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
  void dotCompletionMemberDocumentationAndSignatureFollowConfiguredLanguageRu() {
    // BSL standalone-документ: getScriptVariantLanguage() = язык LS.
    // Массив.Добавить — единый bilingual-член (Добавить/Add) с bilingual-параметром
    // (Значение/Value) и bilingual-описанием в builtin-platform-types.json.
    var documentContext = TestUtils.getDocumentContext("М = Новый Массив;\nМ.");

    languageServerConfiguration.setLanguage(Language.RU);
    try {
      var add = dotCompletionItem(documentContext, new Position(1, 2), "Добавить");
      assertThat(documentationText(add))
        .as("документация члена — на русском при language=RU")
        .contains("Добавляет значение");
      assertThat(add.getDetail())
        .as("имя параметра в сигнатуре — на русском; необязательный → со знаком «?»")
        .isEqualTo("(Значение?)");
    } finally {
      languageServerConfiguration.setLanguage(Language.DEFAULT_LANGUAGE);
    }
  }

  @Test
  void dotCompletionMemberDocumentationAndSignatureFollowConfiguredLanguageEn() {
    var documentContext = TestUtils.getDocumentContext("М = Новый Массив;\nМ.");

    languageServerConfiguration.setLanguage(Language.EN);
    try {
      var add = dotCompletionItem(documentContext, new Position(1, 2), "Add");
      assertThat(documentationText(add))
        .as("документация члена — на английском при language=EN, а не русский primary")
        .contains("Adds a value")
        .doesNotContain("Добавляет");
      assertThat(add.getDetail())
        .as("имя параметра в сигнатуре — на английском; необязательный → со знаком «?»")
        .isEqualTo("(Value?)");
    } finally {
      languageServerConfiguration.setLanguage(Language.DEFAULT_LANGUAGE);
    }
  }

  @Test
  void dotCompletionReturnTypeInDetailFollowsConfiguredLanguage() {
    // Тип возвращаемого значения в детали completion-item локализуется по языку:
    // Массив.Количество(): Число (ru) / (): Number (en). Имя самого члена здесь
    // моноязычное — проверяется именно язык типа, а не имени.
    var documentContext = TestUtils.getDocumentContext("М = Новый Массив;\nМ.");

    languageServerConfiguration.setLanguage(Language.RU);
    try {
      var count = dotCompletionItem(documentContext, new Position(1, 2), "Количество");
      assertThat(count.getDetail())
        .as("тип возврата — на русском при language=RU")
        .isEqualTo("(): Число");
    } finally {
      languageServerConfiguration.setLanguage(Language.DEFAULT_LANGUAGE);
    }

    languageServerConfiguration.setLanguage(Language.EN);
    try {
      var count = dotCompletionItem(documentContext, new Position(1, 2), "Количество");
      assertThat(count.getDetail())
        .as("тип возврата — на английском при language=EN, а не русский primary")
        .isEqualTo("(): Number");
    } finally {
      languageServerConfiguration.setLanguage(Language.DEFAULT_LANGUAGE);
    }
  }

  private CompletionItem dotCompletionItem(
    com.github._1c_syntax.bsl.languageserver.context.DocumentContext documentContext,
    Position position,
    String label
  ) {
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(position);
    return completionProvider.getCompletion(documentContext, params).getItems().stream()
      .filter(it -> label.equals(it.getLabel()))
      .findFirst()
      .orElseThrow();
  }

  private static String documentationText(CompletionItem item) {
    var doc = item.getDocumentation();
    assertThat(doc).as("у члена должна быть проставлена документация").isNotNull();
    return doc.isLeft() ? doc.getLeft() : doc.getRight().getValue();
  }

  @Test
  void documentationReturnedAsMarkdownMarkupWhenClientSupportsMarkdown() {
    // given
    enableMarkdownDocumentation(true);
    var documentContext = TestUtils.getDocumentContext("М = Новый Массив;\nМ.");

    // when
    var add = dotCompletionItem(documentContext, new Position(1, 2), "Добавить");

    // then
    var doc = add.getDocumentation();
    assertThat(doc.isRight())
      .as("при поддержке markdown документация отдаётся как MarkupContent, а не голой строкой")
      .isTrue();
    assertThat(doc.getRight().getKind()).isEqualTo(MarkupKind.MARKDOWN);
    assertThat(doc.getRight().getValue()).contains("Добавляет значение");
  }

  @Test
  void documentationReturnedAsPlainStringWhenClientLacksMarkdown() {
    // given
    enableMarkdownDocumentation(false);
    var documentContext = TestUtils.getDocumentContext("М = Новый Массив;\nМ.");

    // when
    var add = dotCompletionItem(documentContext, new Position(1, 2), "Добавить");

    // then
    var doc = add.getDocumentation();
    assertThat(doc.isLeft())
      .as("без поддержки markdown документация отдаётся голой строкой (plaintext)")
      .isTrue();
    assertThat(doc.getLeft()).contains("Добавляет значение");
  }

  @Test
  void methodSignatureGoesToLabelDetailsWhenClientSupportsLabelDetails() {
    // given
    // Клиент заявил completionItem.labelDetailsSupport: сигнатуру и тип возврата
    // кладём в labelDetails (detail/description) и не дублируем в плоский detail.
    enableLabelDetailsSupport(true);
    languageServerConfiguration.setLanguage(Language.RU);
    var documentContext = TestUtils.getDocumentContext("М = Новый Массив;\nМ.");

    // when
    CompletionItem count;
    try {
      count = dotCompletionItem(documentContext, new Position(1, 2), "Количество");
    } finally {
      languageServerConfiguration.setLanguage(Language.DEFAULT_LANGUAGE);
    }

    // then
    var labelDetails = count.getLabelDetails();
    assertThat(labelDetails)
      .as("при поддержке labelDetailsSupport сигнатура кладётся в labelDetails")
      .isNotNull();
    assertThat(labelDetails.getDetail())
      .as("labelDetails.detail — сигнатура «()»")
      .isEqualTo("()");
    assertThat(labelDetails.getDescription())
      .as("labelDetails.description — возвращаемый тип")
      .isEqualTo("Число");
    assertThat(count.getDetail())
      .as("плоский detail не дублирует сигнатуру, когда она ушла в labelDetails")
      .isNull();
  }

  @Test
  void propertyTypeGoesToLabelDetailsDescriptionWhenClientSupportsLabelDetails() {
    // given
    enableLabelDetailsSupport(true);
    initServerContext("./src/test/resources/providers", false);
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/providers/completion-properties.os", context);

    // when
    var columns = dotCompletionItem(documentContext, new Position(1, 3), "Колонки");

    // then
    var labelDetails = columns.getLabelDetails();
    assertThat(labelDetails)
      .as("у свойства тип уходит в labelDetails.description")
      .isNotNull();
    assertThat(labelDetails.getDescription()).isNotBlank();
    assertThat(labelDetails.getDetail())
      .as("у свойства нет сигнатуры, поэтому labelDetails.detail не заполняется")
      .isNull();
    assertThat(columns.getDetail())
      .as("плоский detail не дублирует тип, когда он ушёл в labelDetails")
      .isNull();
  }

  @Test
  void methodSignatureStaysInFlatDetailWhenClientLacksLabelDetails() {
    // given
    // Клиент без labelDetailsSupport — прежнее поведение: сигнатура в плоском detail,
    // labelDetails не заполняется.
    enableLabelDetailsSupport(false);
    languageServerConfiguration.setLanguage(Language.RU);
    var documentContext = TestUtils.getDocumentContext("М = Новый Массив;\nМ.");

    // when
    CompletionItem count;
    try {
      count = dotCompletionItem(documentContext, new Position(1, 2), "Количество");
    } finally {
      languageServerConfiguration.setLanguage(Language.DEFAULT_LANGUAGE);
    }

    // then
    assertThat(count.getLabelDetails())
      .as("без labelDetailsSupport labelDetails не заполняется")
      .isNull();
    assertThat(count.getDetail())
      .as("без labelDetailsSupport сигнатура остаётся в плоском detail, как раньше")
      .isEqualTo("(): Число");
  }

  @Test
  void deprecatedItemDocumentationKeepsReasonWithoutTextualMarker() {
    // given
    initServerContext(PATH_TO_METADATA);
    enableMarkdownDocumentation(true);
    var documentContext = TestUtils.getDocumentContext("ПервыйОбщийМодуль.");

    // when
    var deprecated = dotCompletionItem(documentContext, new Position(0, 18), "УстаревшаяПроцедура");

    // then
    assertThat(deprecated.getDeprecated())
      .as("факт устаревания передаётся родным механизмом LSP, а не текстом в documentation")
      .isTrue();
    var doc = deprecated.getDocumentation();
    assertThat(doc.isRight()).isTrue();
    assertThat(doc.getRight().getKind()).isEqualTo(MarkupKind.MARKDOWN);
    assertThat(doc.getRight().getValue())
      .as("в documentation остаётся только причина устаревания, без дублирующей пометки")
      .contains("См. НеУстаревшаяПроцедура.")
      .doesNotContain("Устарела", "Устарело", "Deprecated");
  }

  @Test
  void deprecatedItemReasonShownForPlaintextClient() {
    // given
    initServerContext(PATH_TO_METADATA);
    enableMarkdownDocumentation(false);
    var documentContext = TestUtils.getDocumentContext("ПервыйОбщийМодуль.");

    // when
    var deprecated = dotCompletionItem(documentContext, new Position(0, 18), "УстаревшаяПроцедура");

    // then
    var doc = deprecated.getDocumentation();
    assertThat(doc.isLeft())
      .as("без поддержки markdown документация отдаётся голой строкой")
      .isTrue();
    assertThat(doc.getLeft())
      .contains("См. НеУстаревшаяПроцедура.")
      .doesNotContain("**");
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

  @Test
  void afterNewCompletionListsPlatformClasses() {
    // given — курсор после `Новый ` с пустым префиксом → все классы платформы.
    var content = "А = Новый ";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(0, content.length()));

    // when
    var items = completionProvider.getCompletion(documentContext, params).getItems();

    // then — должны быть платформенные классы.
    assertThat(items).isNotEmpty();
    assertThat(items).extracting(CompletionItem::getKind)
      .as("все элементы после `Новый ` — классы")
      .contains(CompletionItemKind.Class);
    assertThat(items).extracting(CompletionItem::getLabel).contains("Массив", "Структура");
  }

  @Test
  void afterNewWithPrefixFiltersClassesByPrefix() {
    // given — после `Новый Стр` ожидаем только классы, начинающиеся на «Стр».
    var content = "А = Новый Стр";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(0, content.length()));

    // when
    var items = completionProvider.getCompletion(documentContext, params).getItems();

    // then
    assertThat(items)
      .extracting(CompletionItem::getLabel)
      .as("Структура должна быть, Массив — нет (префикс не совпадает)")
      .contains("Структура")
      .doesNotContain("Массив");
  }

  @Test
  void emptyDocumentReturnsCompletionList() {
    // given
    var documentContext = TestUtils.getDocumentContext("");
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(0, 0));

    // when
    var result = completionProvider.getCompletion(documentContext, params);

    // then — completion на пустом документе не падает; результат корректен.
    assertThat(result).isNotNull();
    assertThat(result.getItems()).isNotNull();
  }

  @Test
  void dotCompletionOnValueTableHasCopyMethodWithMultipleSignatures() {
    // given — ТЗ.| на ТаблицаЗначений — есть метод Скопировать с
    // несколькими сигнатурами.
    var content = """
            ТЗ = Новый ТаблицаЗначений;
            ТЗ.""";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(1, 3));

    // when
    var result = completionProvider.getCompletion(documentContext, params);

    // then — Скопировать в списке + detail с "вариантов синтаксиса" (multiple sigs).
    var copyItems = result.getItems().stream()
      .filter(it -> it.getLabel().equalsIgnoreCase("Скопировать") || it.getLabel().equalsIgnoreCase("Copy"))
      .toList();
    assertThat(copyItems).as("ТЗ имеет метод Скопировать").isNotEmpty();
  }

  @Test
  void multiSignatureDetailFollowsConfiguredLanguage() {
    // detail метода с несколькими сигнатурами («N вариантов синтаксиса») —
    // на языке проекта, а не всегда по-русски.
    var content = "ТЗ = Новый ТаблицаЗначений;\nТЗ.";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(1, 3));

    languageServerConfiguration.setLanguage(Language.RU);
    try {
      assertThat(copyDetail(documentContext, params))
        .as("RU detail — «N … синтаксиса»").endsWith("синтаксиса");
    } finally {
      languageServerConfiguration.setLanguage(Language.DEFAULT_LANGUAGE);
    }

    languageServerConfiguration.setLanguage(Language.EN);
    try {
      assertThat(copyDetail(documentContext, params))
        .as("EN detail — английский формат «N overload(s)», не русский")
        .matches("\\d+ overloads?")
        .doesNotContain("синтаксиса");
    } finally {
      languageServerConfiguration.setLanguage(Language.DEFAULT_LANGUAGE);
    }
  }

  private String copyDetail(com.github._1c_syntax.bsl.languageserver.context.DocumentContext documentContext,
                            CompletionParams params) {
    return completionProvider.getCompletion(documentContext, params).getItems().stream()
      .filter(it -> "Скопировать".equalsIgnoreCase(it.getLabel()) || "Copy".equalsIgnoreCase(it.getLabel()))
      .findFirst().orElseThrow().getDetail();
  }

  @Test
  void dotCompletionOnMassivShowsMethodsAsMethodKind() {
    // given — М.| на Массив — все members с MemberKind=METHOD получают
    // CompletionItemKind.Method (см. buildMemberItem).
    var content = """
            М = Новый Массив;
            М.""";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(1, 2));

    // when
    var result = completionProvider.getCompletion(documentContext, params);

    // then — есть Method kind items.
    assertThat(result.getItems())
      .anySatisfy(it -> assertThat(it.getKind()).isEqualTo(CompletionItemKind.Method));
  }

  @Test
  void completionForPartiallyTypedAfterDot() {
    // given — М.Доб| — partial prefix после точки.
    var content = "М = Новый Массив;\nМ.Доб";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(1, 5));

    // when
    var result = completionProvider.getCompletion(documentContext, params);

    // then — supplier не падает; конкретные members зависят от bsl-context.
    assertThat(result).isNotNull();
    assertThat(result.getItems()).isNotNull();
  }

  @Test
  void noDotCompletionShowsLocalProceduresAndFunctions() {
    // given — модуль с локальной функцией и процедурой.
    var content =
      "Функция МояФун() Экспорт\n"
        + "  Возврат 0;\n"
        + "КонецФункции\n"
        + "\n"
        + "Процедура МояПроц() Экспорт\n"
        + "КонецПроцедуры\n"
        + "\n"
        + "Мо";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(7, 2));

    // when
    var result = completionProvider.getCompletion(documentContext, params);

    // then — функция Function kind, процедура Method kind.
    var labels = result.getItems().stream()
      .filter(it -> it.getLabel().startsWith("Мо")).toList();
    assertThat(labels).anySatisfy(it -> assertThat(it.getLabel()).isEqualTo("МояФун"));
    assertThat(labels).anySatisfy(it -> assertThat(it.getLabel()).isEqualTo("МояПроц"));
  }

  @Test
  void localMethodWithoutParametersInsertsClosedParens() {
    // Локальный метод без параметров: курсор ставим после скобок, а не между ними —
    // для обоих режимов клиента (snippet и фолбэк) вставляется `()`.
    enableSnippetSupport(true);

    var content = """
      Процедура БезПараметров() Экспорт
      КонецПроцедуры

      БезПар""";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(3, 6));

    var item = completionProvider.getCompletion(documentContext, params).getItems().stream()
      .filter(it -> "БезПараметров".equals(it.getLabel()))
      .findFirst()
      .orElseThrow();

    assertThat(item.getInsertText()).isEqualTo("БезПараметров()");
    assertThat(item.getInsertTextFormat()).isNull();
    assertThat(item.getCommand()).isNull();
  }

  @Test
  void localMethodWithParametersStaysSnippetWithCursorBetweenParens() {
    // Регрессия: метод с параметрами по-прежнему получает `($0)` и triggerParameterHints.
    enableSnippetSupport(true);

    var content = """
      Процедура СПараметром(Знач П) Экспорт
      КонецПроцедуры

      СПара""";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(3, 5));

    var item = completionProvider.getCompletion(documentContext, params).getItems().stream()
      .filter(it -> "СПараметром".equals(it.getLabel()))
      .findFirst()
      .orElseThrow();

    assertThat(item.getInsertText()).isEqualTo("СПараметром($0)");
    assertThat(item.getInsertTextFormat()).isEqualTo(InsertTextFormat.Snippet);
    assertThat(item.getCommand()).isNotNull();
    assertThat(item.getCommand().getCommand()).isEqualTo("editor.action.triggerParameterHints");
  }

  @Test
  void noDotCompletionShowsLocalVariables() {
    // given
    var content =
      "Перем МояПеременная;\n"
        + "\n"
        + "Мо";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(2, 2));

    // when
    var result = completionProvider.getCompletion(documentContext, params);

    // then
    assertThat(result.getItems())
      .anySatisfy(it -> assertThat(it.getLabel()).isEqualTo("МояПеременная"));
  }

  @Test
  void noDotCompletionFiltersByPrefix() {
    // given — пользователь набрал "Соо", ожидает Сообщить.
    var content = "Соо";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(0, 3));

    // when
    var result = completionProvider.getCompletion(documentContext, params);

    // then — есть хотя бы один кандидат, начинающийся с "Соо".
    assertThat(result.getItems())
      .as("completion подсказывает Сообщить и подобные")
      .anySatisfy(it ->
        assertThat(it.getLabel().toLowerCase()).startsWith("соо"));
  }

  @Test
  void afterNewCompletionWithEmptyPrefix() {
    // given — "Х = Новый " (после пробела, без префикса).
    var content = "Х = Новый ";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(0, content.length()));

    // when
    var result = completionProvider.getCompletion(documentContext, params);

    // then — есть список конструктабельных классов.
    assertThat(result.getItems()).isNotEmpty();
  }

  @Test
  void afterNewCompletionShowsClassKind() {
    // given — "Х = Новый Стр" — completion items должны иметь Class kind.
    var content = "Х = Новый Стр";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(0, content.length()));

    // when
    var result = completionProvider.getCompletion(documentContext, params);

    // then — Структура и пр. с Class kind.
    assertThat(result.getItems())
      .anySatisfy(it -> assertThat(it.getKind()).isEqualTo(CompletionItemKind.Class));
  }

  @Test
  void afterNewCompletionListsConstructibleTypes() {
    // given — позиция после "Новый ".
    var content = "Х = Новый Стр";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(0, content.length()));

    // when
    var result = completionProvider.getCompletion(documentContext, params);

    // then — Структура есть в списке.
    assertThat(result.getItems())
      .anySatisfy(it ->
        assertThat(it.getLabel().toLowerCase()).startsWith("стр"));
  }

  @Test
  void dotCompletionOnUnknownTypeReturnsEmpty() {
    // given
    var content = """
            Х = Неизв12345;
            Х.""";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(1, 2));

    // when
    var result = completionProvider.getCompletion(documentContext, params);

    // then — нет типа, нет членов.
    assertThat(result.getItems()).isEmpty();
  }

  @Test
  void dotCompletionOnStructureFieldsListsKeys() {
    // given
    var content = """
            Стр = Новый Структура("Имя, Возраст", "Иван", 30);
            Стр.""";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(1, 4));

    // when
    var result = completionProvider.getCompletion(documentContext, params);

    // then — Имя и Возраст в списке.
    var labels = result.getItems().stream().map(CompletionItem::getLabel).toList();
    assertThat(labels).contains("Имя", "Возраст");
  }

  @Test
  void noDotCompletionRanksLocalMethodAboveGlobalFunctionAndKeyword() {
    // sortText-«корзины»: локальный метод документа должен ранжироваться выше
    // (лексикографически меньший sortText) глобальной функции и ключевого слова,
    // чтобы не тонуть среди сотен платформенных кандидатов.
    // given
    var content = """
      Процедура Сообщение() Экспорт
      КонецПроцедуры

      Сооб""";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(3, 4));

    // when
    var items = completionProvider.getCompletion(documentContext, params).getItems();
    var localMethod = sortTextOf(items, "Сообщение");
    var globalFunction = sortTextOf(items, "Сообщить");

    // then
    assertThat(localMethod)
      .as("sortText локального метода и глобальной функции должны быть проставлены")
      .isNotNull();
    assertThat(globalFunction).isNotNull();
    assertThat(localMethod)
      .as("локальный метод документа ранжируется выше глобальной функции")
      .isLessThan(globalFunction);
  }

  @Test
  void noDotCompletionRanksLocalVariableAboveKeyword() {
    // Локальная переменная документа должна ранжироваться выше ключевого слова.
    // given
    var content = """
      Перем ЕслиЧтоТо;

      Е""";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(2, 1));

    // when
    var items = completionProvider.getCompletion(documentContext, params).getItems();
    var localVariable = sortTextOf(items, "ЕслиЧтоТо");
    var keyword = sortTextOf(items, "Если");

    // then
    assertThat(localVariable).isNotNull();
    assertThat(keyword).as("ключевое слово Если должно быть в выдаче").isNotNull();
    assertThat(localVariable)
      .as("локальная переменная ранжируется выше ключевого слова")
      .isLessThan(keyword);
  }

  @Test
  void noDotCompletionDemotesDeprecatedLocalMethodBelowNonDeprecatedNeighbor() {
    // Устаревший локальный метод получает sortText хуже неустаревшего соседа той же
    // корзины. Имя устаревшего («МетодА») лексикографически меньше неустаревшего
    // («МетодБ») — значит, перестановку обеспечивает именно пометка устаревания.
    // given
    var content = """
      // Устарела. См. МетодБ.
      Процедура МетодА() Экспорт
      КонецПроцедуры

      Процедура МетодБ() Экспорт
      КонецПроцедуры

      Мет""";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(7, 3));

    // when
    var items = completionProvider.getCompletion(documentContext, params).getItems();
    var deprecated = sortTextOf(items, "МетодА");
    var fresh = sortTextOf(items, "МетодБ");

    // then
    assertThat(deprecated).as("устаревший метод должен быть в выдаче").isNotNull();
    assertThat(fresh).isNotNull();
    assertThat(deprecated)
      .as("устаревший метод ранжируется ниже неустаревшего соседа, несмотря на меньшее имя")
      .isGreaterThan(fresh);
  }

  private static String sortTextOf(List<CompletionItem> items, String label) {
    return items.stream()
      .filter(it -> label.equals(it.getLabel()))
      .map(CompletionItem::getSortText)
      .findFirst()
      .orElse(null);
  }

  @Test
  void positionBeyondEndOfFileReturnsEmptyOrSafe() {
    // given — позиция за пределами файла.
    var documentContext = TestUtils.getDocumentContext("Сооб");
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(100, 100));

    // when
    var result = completionProvider.getCompletion(documentContext, params);

    // then — supplier не падает.
    assertThat(result).isNotNull();
  }

  private void enableDocumentationResolveSupport(boolean markdown) {
    var itemCaps = new CompletionItemCapabilities();
    itemCaps.setDocumentationFormat(markdown ? List.of(MarkupKind.MARKDOWN) : List.of(MarkupKind.PLAINTEXT));
    itemCaps.setResolveSupport(new CompletionItemResolveSupportCapabilities(List.of("documentation")));
    var completionCaps = new CompletionCapabilities();
    completionCaps.setCompletionItem(itemCaps);
    var textDocumentCaps = new TextDocumentClientCapabilities();
    textDocumentCaps.setCompletion(completionCaps);
    var caps = new ClientCapabilities();
    caps.setTextDocument(textDocumentCaps);
    clientCapabilitiesHolder.setCapabilities(caps);
    completionProvider.handleInitializeEvent();
  }

  @Test
  void dotCompletionItemArrivesWithoutDocumentationButWithDataWhenResolveSupported() {
    // given — клиент умеет лениво разрешать documentation
    enableDocumentationResolveSupport(true);
    var documentContext = TestUtils.getDocumentContext("М = Новый Массив;\nМ.");

    // when
    var add = dotCompletionItem(documentContext, new Position(1, 2), "Добавить");

    // then — documentation отложена, но data приложена для resolve
    assertThat(add.getDocumentation())
      .as("при поддержке resolveSupport documentation отдаётся лениво")
      .isNull();
    assertThat(add.getData())
      .as("для отложенного resolve в item кладётся data-ключ члена")
      .isNotNull();
    // detail остаётся жадным (фильтрация/вставка не требуют resolve)
    assertThat(add.getDetail()).isEqualTo("(Значение?)");
  }

  @Test
  void resolveCompletionItemRestoresSameDocumentationAsEagerWhenResolveSupported() {
    // given — ленивый item из dot-completion
    enableDocumentationResolveSupport(true);
    var documentContext = TestUtils.getDocumentContext("М = Новый Массив;\nМ.");
    var lazy = dotCompletionItem(documentContext, new Position(1, 2), "Добавить");

    // when — клиент запрашивает resolve этого item
    var resolved = completionProvider.resolveCompletionItem(lazy);

    // then — documentation восстановлена тем же контентом, что был бы жадным
    assertThat(resolved.getDocumentation())
      .as("resolve восстанавливает documentation")
      .isNotNull();
    assertThat(resolved.getDocumentation().getRight().getKind()).isEqualTo(MarkupKind.MARKDOWN);
    assertThat(resolved.getDocumentation().getRight().getValue()).contains("Добавляет значение");
    assertThat(resolved.getData())
      .as("после resolve data очищается для экономии трафика")
      .isNull();
  }

  private CompletionItem noDotCompletionItem(
    com.github._1c_syntax.bsl.languageserver.context.DocumentContext documentContext,
    Position position,
    String label
  ) {
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(position);
    return completionProvider.getCompletion(documentContext, params).getItems().stream()
      .filter(it -> label.equals(it.getLabel()))
      .findFirst()
      .orElseThrow();
  }

  @Test
  void globalFunctionItemArrivesWithoutDocumentationButWithDataWhenResolveSupported() {
    // given — клиент умеет лениво разрешать documentation
    enableDocumentationResolveSupport(true);
    var documentContext = TestUtils.getDocumentContext("Сооб");

    // when
    var message = noDotCompletionItem(documentContext, new Position(0, 4), "Сообщить");

    // then — documentation отложена, но data приложена для resolve;
    // жадно построенные поля (insertText) остаются
    assertThat(message.getDocumentation())
      .as("при поддержке resolveSupport documentation глобальной функции отдаётся лениво")
      .isNull();
    assertThat(message.getData())
      .as("для отложенного resolve в item глобальной функции кладётся data-ключ")
      .isNotNull();
    assertThat(message.getInsertText()).isEqualTo("Сообщить(");
  }

  @Test
  void resolveCompletionItemRestoresSameGlobalFunctionDocumentationAsEager() {
    // given — то же описание, что было бы при жадной сборке (клиент без resolveSupport)
    enableMarkdownDocumentation(true);
    var eagerContext = TestUtils.getDocumentContext("Сооб");
    var eager = noDotCompletionItem(eagerContext, new Position(0, 4), "Сообщить");
    var expectedDoc = documentationText(eager);

    enableDocumentationResolveSupport(true);
    var lazyContext = TestUtils.getDocumentContext("Сооб");
    var lazy = noDotCompletionItem(lazyContext, new Position(0, 4), "Сообщить");

    // when — клиент запрашивает resolve этого item
    var resolved = completionProvider.resolveCompletionItem(lazy);

    // then — documentation восстановлена тем же контентом, что был бы жадным
    assertThat(resolved.getDocumentation())
      .as("resolve восстанавливает documentation глобальной функции")
      .isNotNull();
    assertThat(resolved.getDocumentation().getRight().getKind()).isEqualTo(MarkupKind.MARKDOWN);
    assertThat(resolved.getDocumentation().getRight().getValue()).isEqualTo(expectedDoc);
    assertThat(resolved.getData())
      .as("после resolve data очищается для экономии трафика")
      .isNull();
  }

  @Test
  void globalFunctionKeepsEagerDocumentationWhenClientLacksResolveSupport() {
    // given — клиент без resolveSupport (только markdown documentation)
    enableMarkdownDocumentation(true);
    var documentContext = TestUtils.getDocumentContext("Сооб");

    // when
    var message = noDotCompletionItem(documentContext, new Position(0, 4), "Сообщить");

    // then — прежнее поведение: documentation приходит сразу, data не нужна
    assertThat(message.getDocumentation())
      .as("без resolveSupport documentation глобальной функции остаётся жадной")
      .isNotNull();
    assertThat(message.getData()).isNull();
  }

  private void enableCommitCharactersSupport(boolean enabled) {
    var itemCaps = new CompletionItemCapabilities();
    itemCaps.setCommitCharactersSupport(enabled);
    var completionCaps = new CompletionCapabilities();
    completionCaps.setCompletionItem(itemCaps);
    var textDocumentCaps = new TextDocumentClientCapabilities();
    textDocumentCaps.setCompletion(completionCaps);
    var caps = new ClientCapabilities();
    caps.setTextDocument(textDocumentCaps);
    clientCapabilitiesHolder.setCapabilities(caps);
    completionProvider.handleInitializeEvent();
  }

  @Test
  void methodMemberGetsOpenParenCommitCharacter() {
    // given — клиент поддерживает commitCharactersSupport
    enableCommitCharactersSupport(true);
    var documentContext = TestUtils.getDocumentContext("М = Новый Массив;\nМ.");

    // when
    var add = dotCompletionItem(documentContext, new Position(1, 2), "Добавить");

    // then — метод фиксируется вставкой открывающей скобки
    assertThat(add.getKind()).isEqualTo(CompletionItemKind.Method);
    assertThat(add.getCommitCharacters()).containsExactly("(");
  }

  @Test
  void propertyMemberGetsDotCommitCharacter() {
    // given — клиент поддерживает commitCharactersSupport; ТЗ.Колонки — свойство
    enableCommitCharactersSupport(true);
    initServerContext("./src/test/resources/providers", false);
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/providers/completion-properties.os", context);

    // when
    var columns = dotCompletionItem(documentContext, new Position(1, 3), "Колонки");

    // then — у свойства осмысленно дальнейшее обращение через точку
    assertThat(columns.getKind()).isEqualTo(CompletionItemKind.Property);
    assertThat(columns.getCommitCharacters()).containsExactly(".");
  }

  @Test
  void localVariableGetsDotCommitCharacter() {
    // given — клиент поддерживает commitCharactersSupport; локальная переменная
    enableCommitCharactersSupport(true);
    var content = """
      Перем МояПеременная;

      МояПерем""";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(2, 8));

    // when
    var variable = completionProvider.getCompletion(documentContext, params).getItems().stream()
      .filter(it -> "МояПеременная".equals(it.getLabel()))
      .findFirst()
      .orElseThrow();

    // then
    assertThat(variable.getKind()).isEqualTo(CompletionItemKind.Variable);
    assertThat(variable.getCommitCharacters()).containsExactly(".");
  }

  @Test
  void keywordHasNoCommitCharacters() {
    // given — клиент поддерживает commitCharactersSupport
    enableCommitCharactersSupport(true);
    var documentContext = TestUtils.getDocumentContext("Если");
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(0, 4));

    // when
    var keyword = completionProvider.getCompletion(documentContext, params).getItems().stream()
      .filter(it -> it.getKind() == CompletionItemKind.Keyword)
      .findFirst()
      .orElseThrow();

    // then — ключевым словам commitCharacters не задаются
    assertThat(keyword.getCommitCharacters()).isNull();
  }

  @Test
  void methodMemberHasNoCommitCharactersWhenClientLacksSupport() {
    // given — клиент не поддерживает commitCharactersSupport
    enableCommitCharactersSupport(false);
    var documentContext = TestUtils.getDocumentContext("М = Новый Массив;\nМ.");

    // when
    var add = dotCompletionItem(documentContext, new Position(1, 2), "Добавить");

    // then — без клиентской capability commitCharacters не задаются
    assertThat(add.getCommitCharacters()).isNull();
  }

  @Test
  void dotCompletionKeepsEagerDocumentationWhenClientLacksResolveSupport() {
    // given — клиент без resolveSupport (только markdown documentation)
    enableMarkdownDocumentation(true);
    var documentContext = TestUtils.getDocumentContext("М = Новый Массив;\nМ.");

    // when
    var add = dotCompletionItem(documentContext, new Position(1, 2), "Добавить");

    // then — прежнее поведение: documentation приходит сразу, data не нужна
    assertThat(add.getDocumentation())
      .as("без resolveSupport documentation остаётся жадной")
      .isNotNull();
    assertThat(add.getDocumentation().getRight().getValue()).contains("Добавляет значение");
    assertThat(add.getData()).isNull();
  }
}
