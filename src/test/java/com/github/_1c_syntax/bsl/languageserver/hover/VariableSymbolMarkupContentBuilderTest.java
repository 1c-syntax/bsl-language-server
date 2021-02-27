/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.PostConstruct;
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
  void testSubVarContentFromDirectFile_NoComments() {
    // given
    DocumentContext documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    final var symbolTree = documentContext.getSymbolTree();
    var varSymbol = symbolTree.getVariables().get(0);

    // when
    var content = markupContentBuilder.getContent(varSymbol).getValue();

    assertThat(content).isNotEmpty();

    var blocks = Arrays.asList(content.split("---\n?"));

    assertThat(blocks).hasSize(2);
    assertThat(blocks.get(0)).isEqualTo("```bsl\n" +
      "Перем ИмяБезОписания\n" +
      "```\n" +
      "\n");
    assertThat(blocks.get(1)).matches("Переменная из file:///T:/repo/1c-syntax/bsl-language-server/src/test/resources/hover/variableSymbolMarkupContentBuilder.bsl\n" +
      "\n");
  }

  @Test
  void testSubVarContentFromDirectFile_OneCommentsStringFromRight() {
    // given
    DocumentContext documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    final var symbolTree = documentContext.getSymbolTree();
    var varSymbol = symbolTree.getVariables().get(1);

    // when
    var content = markupContentBuilder.getContent(varSymbol).getValue();

    assertThat(content).isNotEmpty();

    var blocks = Arrays.asList(content.split("---\n?"));

    assertThat(blocks).hasSize(3);
    assertThat(blocks.get(0)).isEqualTo("```bsl\n" +
      "Перем Имя_ОписаниеСправаОднойСтрокой\n" +
      "```\n" +
      "\n");
    assertThat(blocks.get(1)).matches("Переменная из file:///T:/repo/1c-syntax/bsl-language-server/src/test/resources/hover/variableSymbolMarkupContentBuilder.bsl\n" +
      "\n");
    assertThat(blocks.get(2)).matches("описание\n" +
      "\n");
  }

  @Test
  void testMethodVarContentFromDirectFile_2_comments_strings() {
    // given
    DocumentContext documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    final var symbolTree = documentContext.getSymbolTree();
    var varSymbol = (VariableSymbol) symbolTree.getChildrenFlat().get(9);

    // when
    var content = markupContentBuilder.getContent(varSymbol).getValue();

    assertThat(content).isNotEmpty();

    var blocks = Arrays.asList(content.split("---\n?"));

    assertThat(blocks).hasSize(3);
    assertThat(blocks.get(0)).isEqualTo("```bsl\n" +
      "Перем Имя_ОписаниеСверхуДвеСтроки_Функция\n" +
      "```\n" +
      "\n");
    assertThat(blocks.get(1)).matches("Переменная из file:///T:/repo/1c-syntax/bsl-language-server/src/test/resources/hover/variableSymbolMarkupContentBuilder.bsl.ИмяФункции\n" +
      "\n");
    assertThat(blocks.get(2)).matches("описание 1 строка// 2 строка\n" +
      "\n");
  }

  @Test
  void testMethodVarContentFromDirectFile_3_comments_strings() {
    // given
    DocumentContext documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    final var symbolTree = documentContext.getSymbolTree();
    var varSymbol = (VariableSymbol) symbolTree.getChildrenFlat().get(10);

    // when
    var content = markupContentBuilder.getContent(varSymbol).getValue();

    assertThat(content).isNotEmpty();

    var blocks = Arrays.asList(content.split("---\n?"));

    assertThat(blocks).hasSize(3);
    assertThat(blocks.get(0)).isEqualTo("```bsl\n" +
      "Перем Имя_ОписаниеСверхуТриСтрокиПоследняяПустая_Функция\n" +
      "```\n" +
      "\n");
    assertThat(blocks.get(1)).matches("Переменная из file:///T:/repo/1c-syntax/bsl-language-server/src/test/resources/hover/variableSymbolMarkupContentBuilder.bsl.ИмяФункции\n" +
      "\n");
    assertThat(blocks.get(2)).matches("описание 1 строка// 2 строка//\n" +
      "\n");
  }

}