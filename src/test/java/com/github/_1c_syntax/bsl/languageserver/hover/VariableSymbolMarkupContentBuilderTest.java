/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.types.ModuleType;
import jakarta.annotation.PostConstruct;
import org.eclipse.lsp4j.Location;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Paths;
import java.util.Arrays;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterClass
class VariableSymbolMarkupContentBuilderTest {

  @Autowired
  private VariableSymbolMarkupContentBuilder markupContentBuilder;

  @Autowired
  private ServerContext serverContext;

  private static final String PATH_TO_FILE = "./src/test/resources/hover/variableSymbolMarkupContentBuilder.bsl";

  @PostConstruct
  void prepareServerContext() {
    serverContext.setConfigurationRoot(Paths.get(PATH_TO_METADATA));
    serverContext.populateContext();
  }

  @Test
  void testFileVarContentFromDirectFile_NoComments() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    final var symbolTree = documentContext.getSymbolTree();
    var varSymbol = symbolTree.getVariableSymbol("ИмяБезОписания", symbolTree.getModule()).orElseThrow();
    var reference = getReference(documentContext, varSymbol);

    // when
    var content = markupContentBuilder.getContent(reference, varSymbol).getValue();

    assertThat(content).isNotEmpty();

    var blocks = Arrays.asList(content.split("---\n?"));

    assertThat(blocks).hasSize(2);
    assertThat(blocks.get(0)).isEqualTo("""
      ```bsl
      Перем ИмяБезОписания
      ```

      """);
    assertThat(blocks.get(1)).matches("""
      \\[file://.*/src/test/resources/hover/variableSymbolMarkupContentBuilder.bsl]\\(.*src/test/resources/hover/variableSymbolMarkupContentBuilder.bsl#\\d+\\)

      """);
  }

  @Test
  void testFileVarContentFromDirectFile_OneCommentsStringFromRight() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    final var symbolTree = documentContext.getSymbolTree();
    var varSymbol = symbolTree.getVariableSymbol("Имя_ОписаниеСправаОднойСтрокой", symbolTree.getModule()).orElseThrow();
    var reference = getReference(documentContext, varSymbol);

    // when
    var content = markupContentBuilder.getContent(reference, varSymbol).getValue();

    assertThat(content).isNotEmpty();

    var blocks = Arrays.asList(content.split("---\n?"));

    assertThat(blocks).hasSize(3);
    assertThat(blocks.get(0)).isEqualTo("""
      ```bsl
      Перем Имя_ОписаниеСправаОднойСтрокой
      ```

      """);
    assertThat(blocks.get(1)).matches("""
      \\[file://.*/src/test/resources/hover/variableSymbolMarkupContentBuilder.bsl]\\(.*src/test/resources/hover/variableSymbolMarkupContentBuilder.bsl#\\d+\\)

      """);
    assertThat(blocks.get(2)).matches("""
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
    var reference = getReference(documentContext, varSymbol);

    // when
    var content = markupContentBuilder.getContent(reference, varSymbol).getValue();

    assertThat(content).isNotEmpty();

    var blocks = Arrays.asList(content.split("---\n?"));

    assertThat(blocks).hasSize(3);
    assertThat(blocks.get(0)).isEqualTo("""
      ```bsl
      Перем Имя_ОписаниеСверхуДвеСтроки_Функция
      ```

      """);
    assertThat(blocks.get(1)).matches("""
      \\[file://.*/src/test/resources/hover/variableSymbolMarkupContentBuilder.bsl.ИмяФункции]\\(.*src/test/resources/hover/variableSymbolMarkupContentBuilder.bsl#\\d+\\)

      """);
    // TODO баг - нет \n для многострочного описания переменной
    assertThat(blocks.get(2)).matches("""
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
    var reference = getReference(documentContext, varSymbol);

    // when
    var content = markupContentBuilder.getContent(reference, varSymbol).getValue();

    assertThat(content).isNotEmpty();

    var blocks = Arrays.asList(content.split("---\n?"));

    assertThat(blocks).hasSize(3);
    assertThat(blocks.get(0)).isEqualTo("""
      ```bsl
      Перем Имя_ОписаниеСверхуТриСтрокиПоследняяПустая_Функция
      ```

      """);
    assertThat(blocks.get(1)).matches("""
      \\[file://.*/src/test/resources/hover/variableSymbolMarkupContentBuilder.bsl.ИмяФункции]\\(.*src/test/resources/hover/variableSymbolMarkupContentBuilder.bsl#\\d+\\)

      """);
    assertThat(blocks.get(2)).matches("""
      описание 1 строка
      2 строка

      """);
  }

  @Test
  void testContentFromObjectModule() {

    // given
    var documentContext = serverContext.getDocument("Catalog.Справочник1", ModuleType.ObjectModule).orElseThrow();
    final var symbolTree = documentContext.getSymbolTree();
    var varSymbol = symbolTree.getVariableSymbol("ВалютаУчета", symbolTree.getModule()).orElseThrow();
    var reference = getReference(documentContext, varSymbol);

    // when
    var content = markupContentBuilder.getContent(reference, varSymbol).getValue();

    // then
    assertThat(content).isNotEmpty();

    var blocks = Arrays.asList(content.split("---\n?"));

    assertThat(blocks).hasSize(2);
    assertThat(blocks.get(0)).isEqualTo("""
      ```bsl
      Перем ВалютаУчета
      ```

      """);
    assertThat(blocks.get(1)).matches("\\[Catalog.Справочник1]\\(.*Catalogs/.*/Ext/ObjectModule.bsl#\\d+\\)\n\n");
  }

  private static Reference getReference(DocumentContext documentContext, VariableSymbol variableSymbol) {
    return Reference.of(
      documentContext.getSymbolTree().getModule(),
      variableSymbol,
      new Location(documentContext.getUri().toString(), variableSymbol.getSelectionRange()),
      OccurrenceType.DEFINITION
    );
  }
}
