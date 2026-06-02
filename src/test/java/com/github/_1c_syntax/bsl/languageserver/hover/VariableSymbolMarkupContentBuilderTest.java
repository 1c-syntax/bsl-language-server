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
package com.github._1c_syntax.bsl.languageserver.hover;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.junit.jupiter.api.BeforeEach;
import org.eclipse.lsp4j.Location;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class VariableSymbolMarkupContentBuilderTest extends AbstractServerContextAwareTest {

  @Autowired
  private VariableSymbolMarkupContentBuilder markupContentBuilder;

  private static final String PATH_TO_FILE = "./src/test/resources/hover/variableSymbolMarkupContentBuilder.bsl";

  @BeforeEach
  void prepareMetadataServerContext() {
    initServerContextOnce(Path.of(TestUtils.PATH_TO_METADATA));
  }

  @Test
  void testFileVarContentFromDirectFile_NoComments() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    final var symbolTree = documentContext.getSymbolTree();
    var varSymbol = symbolTree.getVariableSymbol("ИмяБезОписания", symbolTree.getModule()).orElseThrow();

    // when
    var content = markupContentBuilder.getContent(referenceTo(documentContext, varSymbol)).getValue();

    assertThat(content).isNotEmpty();

    var blocks = Arrays.asList(content.split("---\n?"));

    assertThat(blocks).hasSize(3);
    assertThat(blocks.get(0)).isEqualTo("""
      ```bsl
      Перем ИмяБезОписания
      ```

      """);
    assertThat(blocks.get(1)).isEqualTo("""
      Переменная уровня модуля

      """);
    assertThat(blocks.get(2)).matches("""
      \\[file://.*/src/test/resources/hover/variableSymbolMarkupContentBuilder.bsl]\\(.*src/test/resources/hover/variableSymbolMarkupContentBuilder.bsl#\\d+\\)

      """);
  }

  @Test
  void testFileVarContentFromDirectFile_OneCommentsStringFromRight() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    final var symbolTree = documentContext.getSymbolTree();
    var varSymbol = symbolTree.getVariableSymbol("Имя_ОписаниеСправаОднойСтрокой", symbolTree.getModule()).orElseThrow();

    // when
    var content = markupContentBuilder.getContent(referenceTo(documentContext, varSymbol)).getValue();

    assertThat(content).isNotEmpty();

    var blocks = Arrays.asList(content.split("---\n?"));

    assertThat(blocks).hasSize(4);
    assertThat(blocks.get(0)).isEqualTo("""
      ```bsl
      Перем Имя_ОписаниеСправаОднойСтрокой
      ```

      """);
    assertThat(blocks.get(1)).isEqualTo("""
      Переменная уровня модуля

      """);
    assertThat(blocks.get(2)).matches("""
      \\[file://.*/src/test/resources/hover/variableSymbolMarkupContentBuilder.bsl]\\(.*src/test/resources/hover/variableSymbolMarkupContentBuilder.bsl#\\d+\\)

      """);
    assertThat(blocks.get(3)).matches("""
      описание

      """);
  }

  @Test
  void testMethodVarContentFromDirectFile_2_comments_strings() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    final var symbolTree = documentContext.getSymbolTree();
    var methodSymbol = symbolTree.getMethodSymbol("ИмяФункции").orElseThrow();
    var varSymbol = symbolTree.getVariableSymbol("Имя_ОписаниеСверхуДвеСтроки_Функция", methodSymbol).orElseThrow();

    // when
    var content = markupContentBuilder.getContent(referenceTo(documentContext, varSymbol)).getValue();

    assertThat(content).isNotEmpty();

    var blocks = Arrays.asList(content.split("---\n?"));

    assertThat(blocks).hasSize(4);
    assertThat(blocks.get(0)).isEqualTo("""
      ```bsl
      Перем Имя_ОписаниеСверхуДвеСтроки_Функция
      ```

      """);
    assertThat(blocks.get(1)).isEqualTo("""
      Локальная переменная метода ИмяФункции

      """);
    assertThat(blocks.get(2)).matches("""
      \\[file://.*/src/test/resources/hover/variableSymbolMarkupContentBuilder.bsl.ИмяФункции]\\(.*src/test/resources/hover/variableSymbolMarkupContentBuilder.bsl#\\d+\\)

      """);
    // TODO баг - нет \n для многострочного описания переменной
    assertThat(blocks.get(3)).matches("""
      описание 1 строка
      2 строка

      """);
  }

  @Test
  void testMethodVarContentFromDirectFile_3_comments_strings() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    final var symbolTree = documentContext.getSymbolTree();
    var methodSymbol = symbolTree.getMethodSymbol("ИмяФункции").orElseThrow();
    var varSymbol = symbolTree.getVariableSymbol("Имя_ОписаниеСверхуТриСтрокиПоследняяПустая_Функция", methodSymbol).orElseThrow();

    // when
    var content = markupContentBuilder.getContent(referenceTo(documentContext, varSymbol)).getValue();

    assertThat(content).isNotEmpty();

    var blocks = Arrays.asList(content.split("---\n?"));

    assertThat(blocks).hasSize(4);
    assertThat(blocks.get(0)).isEqualTo("""
      ```bsl
      Перем Имя_ОписаниеСверхуТриСтрокиПоследняяПустая_Функция
      ```

      """);
    assertThat(blocks.get(1)).isEqualTo("""
      Локальная переменная метода ИмяФункции

      """);
    assertThat(blocks.get(2)).matches("""
      \\[file://.*/src/test/resources/hover/variableSymbolMarkupContentBuilder.bsl.ИмяФункции]\\(.*src/test/resources/hover/variableSymbolMarkupContentBuilder.bsl#\\d+\\)

      """);
    assertThat(blocks.get(3)).matches("""
      описание 1 строка
      2 строка

      """);
  }

  @Test
  void testContentFromObjectModule() {

    // given
    var documentContext = context.getDocument("Catalog.Справочник1", ModuleType.ObjectModule).orElseThrow();
    final var symbolTree = documentContext.getSymbolTree();
    var varSymbol = symbolTree.getVariableSymbol("ВалютаУчета", symbolTree.getModule()).orElseThrow();

    // when
    var content = markupContentBuilder.getContent(referenceTo(documentContext, varSymbol)).getValue();

    // then
    assertThat(content).isNotEmpty();

    var blocks = Arrays.asList(content.split("---\n?"));

    assertThat(blocks).hasSize(3);
    assertThat(blocks.get(0)).isEqualTo("""
      ```bsl
      Перем ВалютаУчета
      ```

      """);
    assertThat(blocks.get(1)).isEqualTo("""
      Переменная уровня модуля

      """);
    assertThat(blocks.get(2)).matches("\\[Catalog.Справочник1]\\(.*Catalogs/.*/Ext/ObjectModule.bsl#\\d+\\)\n\n");
  }

  @Test
  void testInferredTypeShownInHover() {
    // given
    var documentContext = TestUtils.getDocumentContext("""
      Перем СтрокаПеременная;
      СтрокаПеременная = "значение";
      """);
    final var symbolTree = documentContext.getSymbolTree();
    var varSymbol = symbolTree.getVariableSymbol("СтрокаПеременная", symbolTree.getModule()).orElseThrow();

    // when
    var content = markupContentBuilder.getContent(referenceTo(documentContext, varSymbol)).getValue();

    // then
    assertThat(content).contains("Тип: Строка");
  }

  @Test
  void structureContentsFromInferenceRenderedAsBulletList() {
    // given
    var documentContext = TestUtils.getDocumentContext("""
      Перем С;
      С = Новый Структура("Имя, Возраст", "Иван", 30);
      """);
    final var symbolTree = documentContext.getSymbolTree();
    var varSymbol = symbolTree.getVariableSymbol("С", symbolTree.getModule()).orElseThrow();

    // when
    var content = markupContentBuilder.getContent(referenceTo(documentContext, varSymbol)).getValue();

    // then: у структуры с полями элемент-итератор (КлючИЗначение) в заголовке не показываем.
    assertThat(content)
      .contains("Тип: Структура")
      .doesNotContain("КлючИЗначение")
      .contains("* **Имя**: `Строка`")
      .contains("* **Возраст**: `Число`");
  }

  @Test
  void valueTableColumnsFromInferenceRenderedAsBulletList() {
    // given
    var documentContext = TestUtils.getDocumentContext("""
      Перем ТЗ;
      ТЗ = Новый ТаблицаЗначений;
      ТЗ.Колонки.Добавить("Сумма");
      """);
    final var symbolTree = documentContext.getSymbolTree();
    var varSymbol = symbolTree.getVariableSymbol("ТЗ", symbolTree.getModule()).orElseThrow();

    // when
    var content = markupContentBuilder.getContent(referenceTo(documentContext, varSymbol)).getValue();

    // then: колонки строки ТЗ показываются маркдаун-списком.
    assertThat(content)
      .contains("Тип: ТаблицаЗначений из СтрокаТаблицыЗначений")
      .contains("* **Сумма**");
  }

  @Test
  void structureKeyDescriptionsFromParameterDocRendered() {
    // given: параметр-структура с задокументированными ключами.
    var documentContext = TestUtils.getDocumentContext("""
      // Параметры:
      //  Настройки - Структура - настройки подключения:
      //   * Адрес - Строка - адрес сервера.
      //   * Порт - Число - номер порта.
      Процедура Тест(Настройки) Экспорт
      КонецПроцедуры
      """);
    final var symbolTree = documentContext.getSymbolTree();
    var method = symbolTree.getMethodSymbol("Тест").orElseThrow();
    var paramSymbol = symbolTree.getVariableSymbol("Настройки", method).orElseThrow();

    // when
    var content = markupContentBuilder.getContent(referenceTo(documentContext, paramSymbol)).getValue();

    // then: ключи показаны с типами и описаниями из doc-комментария, без шума «из КлючИЗначение».
    assertThat(content)
      .contains("Тип: Структура")
      .doesNotContain("КлючИЗначение")
      .contains("* **Адрес**: `Строка` — адрес сервера.")
      .contains("* **Порт**: `Число` — номер порта.");
  }

  @Test
  void structureKeyWithMultipleDocTypesRenderedAsUnion() {
    // given: ключ с перечислением типов через запятую в doc-комментарии.
    var documentContext = TestUtils.getDocumentContext("""
      // Параметры:
      //  Настройки - Структура - настройки:
      //   * Значение - Строка, Число - значение настройки.
      Процедура Тест(Настройки) Экспорт
      КонецПроцедуры
      """);
    final var symbolTree = documentContext.getSymbolTree();
    var method = symbolTree.getMethodSymbol("Тест").orElseThrow();
    var paramSymbol = symbolTree.getVariableSymbol("Настройки", method).orElseThrow();

    // when
    var content = markupContentBuilder.getContent(referenceTo(documentContext, paramSymbol)).getValue();

    // then: перечисление типов отображается через « | », а не одним токеном.
    assertThat(content)
      .contains("* **Значение**: `Строка` | `Число`")
      .doesNotContain("`Строка, Число`");
  }


  private static Reference referenceTo(DocumentContext documentContext, SourceDefinedSymbol symbol) {
    return Reference.of(documentContext.getSymbolTree().getModule(), symbol,
      new Location(documentContext.getUri().toString(), symbol.getSelectionRange()));
  }
}
