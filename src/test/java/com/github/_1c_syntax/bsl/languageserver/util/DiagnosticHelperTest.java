/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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
package com.github._1c_syntax.bsl.languageserver.util;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticHelperTest {

  @Test
  void testNewExpressionTypeName() throws IOException {

    File testFile = new File("./src/test/resources/utils/NewExpressionTypeName.bsl");
    String fileContent = FileUtils.readFileToString(testFile, StandardCharsets.UTF_8);
    DocumentContext documentContext = new DocumentContext("file:///fake-uri.bsl", fileContent);

    NewExpressionContextFinder newExpressionContextFinder = new NewExpressionContextFinder();
    newExpressionContextFinder.visitFile(documentContext.getAst());
    List<BSLParser.NewExpressionContext> newExpressionContexts = newExpressionContextFinder.getNewExpressionContexts();
    assertThat(newExpressionContexts).hasSize(12);

    assertThat(DiagnosticHelper.newExpressionTypeName(newExpressionContexts.get(0))).isEqualToIgnoringCase("Соответствие");
    assertThat(DiagnosticHelper.newExpressionTypeName(newExpressionContexts.get(1))).isEqualToIgnoringCase("ХранилищеЗначения");
    assertThat(DiagnosticHelper.newExpressionTypeName(newExpressionContexts.get(2))).isEqualToIgnoringCase("Запрос");
    assertThat(DiagnosticHelper.newExpressionTypeName(newExpressionContexts.get(3))).isEqualToIgnoringCase("Структура");
    assertThat(DiagnosticHelper.newExpressionTypeName(newExpressionContexts.get(4))).isEqualToIgnoringCase("Структура");
    assertThat(DiagnosticHelper.newExpressionTypeName(newExpressionContexts.get(5))).isEqualToIgnoringCase("СписокЗначений");
    assertThat(DiagnosticHelper.newExpressionTypeName(newExpressionContexts.get(6))).isEqualToIgnoringCase("Массив");
    assertThat(DiagnosticHelper.newExpressionTypeName(newExpressionContexts.get(7))).isEqualToIgnoringCase("ТаблицаЗначений");
    assertThat(DiagnosticHelper.newExpressionTypeName(newExpressionContexts.get(8))).isEqualToIgnoringCase("Массив");
    assertThat(DiagnosticHelper.newExpressionTypeName(newExpressionContexts.get(9))).isEqualToIgnoringCase("Структура");
    assertThat(DiagnosticHelper.newExpressionTypeName(newExpressionContexts.get(10))).isEqualToIgnoringCase("Массив");
    assertThat(DiagnosticHelper.newExpressionTypeName(newExpressionContexts.get(11))).isEmpty();

  }

  private static class NewExpressionContextFinder extends BSLParserBaseVisitor<ParseTree> {

    private List<BSLParser.NewExpressionContext> newExpressionContexts = new ArrayList<>();

    @Override
    public ParseTree visitNewExpression(BSLParser.NewExpressionContext ctx) {
      newExpressionContexts.add(ctx);
      return super.visitNewExpression(ctx);
    }

    private List<BSLParser.NewExpressionContext> getNewExpressionContexts() {
      return newExpressionContexts;
    }

  }
}
