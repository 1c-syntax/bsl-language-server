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
package com.github._1c_syntax.bsl.languageserver.semantictokens;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.util.SemanticTokensTestHelper;
import com.github._1c_syntax.bsl.languageserver.util.SemanticTokensTestHelper.ExpectedToken;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Set;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

@Import(SemanticTokensTestHelper.class)
class PlatformMemberPropertyAccessSemanticTokensSupplierTest extends AbstractServerContextAwareTest {

  @Autowired
  private PlatformMemberPropertyAccessSemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensTestHelper helper;

  @Test
  void testPlatformPropertyOnTypedVariable() {
    // given — таблица значений с известным свойством Колонки из платформы.
    String bsl = """
      Процедура Тест()
          ТЗ = Новый ТаблицаЗначений;
          К = ТЗ.Колонки;
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then — Колонки подсвечивается как Property+DefaultLibrary.
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(2, 11, 7, SemanticTokenTypes.Property,
        Set.of(SemanticTokenModifiers.DefaultLibrary), "Колонки")
    ));
  }

  @Test
  void testPropertyChain() {
    // given — обращение к свойству в середине цепочки accessProperty.
    String bsl = """
      Процедура Тест()
          ТЗ = Новый ТаблицаЗначений;
          Колво = ТЗ.Колонки.Количество();
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then — Колонки (accessProperty) подсвечен; Количество (accessCall) — нет.
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(2, 15, 7, SemanticTokenTypes.Property,
        Set.of(SemanticTokenModifiers.DefaultLibrary), "Колонки")
    ));
    // Количество начинается на line 2, char 23 (после "ТЗ.Колонки.").
    assertThat(decoded)
      .as("Количество — это accessCall, не accessProperty; данный сапплаер его не выдаёт")
      .noneMatch(token -> token.line() == 2 && token.start() == 23);
  }

  @Test
  void testStructureDynamicFieldHighlighted() {
    // given — кейс из issue: поле Структуры, накопленное из конструктора.
    String bsl = """
      Процедура Тест()
          Стр = Новый Структура("Имя", "Иван");
          А = Стр.Имя;
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then — Имя подсвечивается как Property, но без DefaultLibrary: ключ
    // структуры задан разработчиком, это не платформенный API.
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(2, 12, 3, SemanticTokenTypes.Property, Set.of(), "Имя")
    ));
  }

  @Test
  void testMethodCallNotHighlightedAsProperty() {
    // given — вызов метода платформенного типа (accessCall, со скобками).
    String bsl = """
      Процедура Тест()
          М = Новый Массив;
          М.Добавить(1);
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then — accessCall этим сапплаером не подсвечивается.
    assertThat(decoded).isEmpty();
  }

  @Test
  void testUnknownTypeReceiverProducesNoToken() {
    // given — переменная без type inference: тип не резолвится → свойство не подсвечивается.
    String bsl = """
      Процедура Тест(НеТипизированный)
          А = НеТипизированный.НеизвестноеСвойство;
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then — пусто (свойство неизвестно).
    assertThat(decoded).isEmpty();
  }

  @Test
  void testGlobalManagerChainSkipped() {
    // given — конфигурация поднята: Справочники.Справочник1 резолвится и как
    // глобальный менеджер (база цепочки), и как член (метаобъект). Без скипа член
    // покрасился бы как Property, но всю цепочку красит GlobalScope (→ Class).
    initServerContext(PATH_TO_METADATA);
    String bsl = """
      Процедура Тест()
          А = Справочники.Справочник1;
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then — на члене глобальной цепочки наш сапплаер ничего не выдаёт.
    assertThat(decoded).isEmpty();
  }

  @Test
  void testRangeScopingByOverlap() {
    // given — два обращения к свойству Колонки на разных строках (2 и 3).
    // На строке 2 «Колонки» занимает столбцы [11, 18).
    String bsl = """
      Процедура Тест()
          ТЗ = Новый ТаблицаЗначений;
          А = ТЗ.Колонки;
          Б = ТЗ.Колонки;
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(bsl);

    // when/then — диапазон строки 2 не захватывает обращение на строке 3.
    var line2 = helper.decodeFromEntries(supplier.getSemanticTokens(
      documentContext, new Range(new Position(2, 0), new Position(2, 100))));
    assertThat(line2).hasSize(1);
    assertThat(line2).allSatisfy(token -> assertThat(token.line()).isEqualTo(2));

    // when/then — имя начинается до границы диапазона (start=14 внутри «Колонки»),
    // но заходит внутрь → по overlap-семантике не отбрасывается.
    var straddling = helper.decodeFromEntries(supplier.getSemanticTokens(
      documentContext, new Range(new Position(2, 14), new Position(2, 100))));
    assertThat(straddling).hasSize(1);
  }

  @Test
  void testStandardAttributeColoredAsDefaultLibrary() {
    // given — типизированный объект справочника; обращение к стандартному
    // реквизиту Наименование (часть платформенной объектной модели).
    initServerContext(PATH_TO_METADATA);
    String bsl = """
      // Параметры:
      //  Объект - СправочникОбъект.Справочник1
      Процедура Тест(Объект)
          А = Объект.Наименование;
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then — стандартный реквизит подсвечивается как Property + DefaultLibrary.
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(3, 15, 12, SemanticTokenTypes.Property,
        Set.of(SemanticTokenModifiers.DefaultLibrary), "Наименование")
    ));
  }

  @Test
  void testOwnAttributeColoredAsPlainProperty() {
    // given — типизированный объект справочника; обращение к собственному
    // реквизиту Реквизит1, заведённому разработчиком конфигурации.
    initServerContext(PATH_TO_METADATA);
    String bsl = """
      // Параметры:
      //  Объект - СправочникОбъект.Справочник1
      Процедура Тест(Объект)
          А = Объект.Реквизит1;
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then — собственный реквизит — просто Property, без DefaultLibrary.
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(3, 15, 9, SemanticTokenTypes.Property,
        Set.of(), "Реквизит1")
    ));
  }

  @Test
  void testGlobalChainInCallStatementSkipped() {
    // given — глобальная цепочка в statement-позиции (callStatement): база — Справочники.
    initServerContext(PATH_TO_METADATA);
    String bsl = """
      Процедура Тест()
          Справочники.Справочник1.СоздатьЭлемент();
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then — пусто (член глобальной цепочки в callStatement тоже пропущен).
    assertThat(decoded).isEmpty();
  }
}
