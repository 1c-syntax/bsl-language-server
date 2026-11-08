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
package com.github._1c_syntax.bsl.languageserver.types.compilation;

import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.compilation.PreprocessorContexts;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseListener;
import com.github._1c_syntax.bsl.parser.BSLTokenizer;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PreprocessorContextsTest {
  @ParameterizedTest
  @CsvSource({
    "Сервер, SERVER;MOBILE_APPLICATION_SERVER;MOBILE_STANDALONE_SERVER",
    "НаСервере, SERVER;MOBILE_APPLICATION_SERVER;MOBILE_STANDALONE_SERVER",
    "ТонкийКлиент, THIN_CLIENT",
    "ВебКлиент, WEB_CLIENT",
    "МобильныйКлиент, MOBILE_CLIENT",
    "ТолстыйКлиентУправляемоеПриложение, THICK_CLIENT_MANAGED",
    "ТолстыйКлиентОбычноеПриложение, THICK_CLIENT_ORDINARY",
    "ВнешнееСоединение, EXTERNAL_CONNECTION",
    "МобильноеПриложениеКлиент, MOBILE_APPLICATION_CLIENT",
    "МобильноеПриложениеСервер, MOBILE_APPLICATION_SERVER",
    "МобильныйАвтономныйСервер, MOBILE_STANDALONE_SERVER",
    "AtServer, SERVER;MOBILE_APPLICATION_SERVER;MOBILE_STANDALONE_SERVER",
    "Сервер ИЛИ ВебКлиент И ТонкийКлиент, SERVER;MOBILE_APPLICATION_SERVER;MOBILE_STANDALONE_SERVER"
  })
  void evaluatesCompilationSymbols(String condition, String expected) {
    var active = contexts("#Если " + condition + " Тогда\nМетка();\n#КонецЕсли\n").getFirst();
    assertThat(active.stream().filter(context -> !context.isFileMode())).containsExactlyInAnyOrderElementsOf(
      Arrays.stream(expected.split(";")).map(ExecutionContext::valueOf).toList());
  }

  @Test
  void unknownConditionsStayUnknownInBothBranches() {
    assertThat(contexts("#Если Windows Тогда\nМетка();\n#Иначе\nМетка();\n#КонецЕсли\n"))
      .hasSize(2).containsOnlyNulls();
  }

  @Test
  void nestedBranchesRespectParentAndPreviousAlternatives() {
    var found = contexts("""
      #Если Клиент Тогда
        #Если ТонкийКлиент Тогда
          Метка();
        #ИначеЕсли ВебКлиент Тогда
          Метка();
        #Иначе
          Метка();
        #КонецЕсли
      #КонецЕсли
      """);
    assertThat(found.get(0)).containsExactly(ExecutionContext.THIN_CLIENT);
    assertThat(found.get(1)).containsExactly(ExecutionContext.WEB_CLIENT);
    assertThat(found.get(2)).containsExactlyInAnyOrder(ExecutionContext.MOBILE_CLIENT,
      ExecutionContext.THICK_CLIENT_MANAGED, ExecutionContext.THICK_CLIENT_ORDINARY,
      ExecutionContext.MOBILE_APPLICATION_CLIENT, ExecutionContext.THICK_CLIENT_MANAGED_FILE,
      ExecutionContext.THICK_CLIENT_ORDINARY_FILE, ExecutionContext.THICK_CLIENT_MANAGED_FILE_CLIENT,
      ExecutionContext.THICK_CLIENT_ORDINARY_FILE_CLIENT, ExecutionContext.THICK_CLIENT_MANAGED_FILE_SERVER,
      ExecutionContext.THICK_CLIENT_ORDINARY_FILE_SERVER);
  }

  @Test
  void parenthesesAndNegationDoNotTreatExternalConnectionAsClient() {
    assertThat(contexts("#Если НЕ (Сервер ИЛИ ВнешнееСоединение) Тогда\nМетка();\n#КонецЕсли\n")
      .getFirst()).containsExactlyInAnyOrder(ExecutionContext.THIN_CLIENT, ExecutionContext.WEB_CLIENT,
        ExecutionContext.MOBILE_CLIENT, ExecutionContext.THICK_CLIENT_MANAGED, ExecutionContext.THICK_CLIENT_ORDINARY,
        ExecutionContext.MOBILE_APPLICATION_CLIENT);
  }

  /** Совмещённые символы форм подтверждены запуском 1С 8.3.27, включая совместимость с 8.2.16. */
  @ParameterizedTest
  @CsvSource({
    "THICK_CLIENT_MANAGED_FILE, Клиент;Сервер;ТолстыйКлиентУправляемоеПриложение",
    "THICK_CLIENT_MANAGED, Клиент;ТолстыйКлиентУправляемоеПриложение",
    "THICK_CLIENT_ORDINARY_FILE, Клиент;Сервер;ТолстыйКлиентОбычноеПриложение",
    "THICK_CLIENT_ORDINARY, Клиент;ТолстыйКлиентОбычноеПриложение",
    "EXTERNAL_CONNECTION_FILE, Сервер;ВнешнееСоединение",
    "EXTERNAL_CONNECTION, ВнешнееСоединение",
    "THICK_CLIENT_MANAGED_FILE_CLIENT, Клиент;Сервер;ТолстыйКлиентУправляемоеПриложение",
    "THICK_CLIENT_MANAGED_FILE_SERVER, Клиент;Сервер;ТолстыйКлиентУправляемоеПриложение",
    "THICK_CLIENT_ORDINARY_FILE_CLIENT, Клиент;Сервер;ТолстыйКлиентОбычноеПриложение",
    "THICK_CLIENT_ORDINARY_FILE_SERVER, Клиент;Сервер;ТолстыйКлиентОбычноеПриложение"
  })
  void distinguishesFileAndClientServerCompilation(ExecutionContext context, String expectedSymbols) {
    var expected = Set.of(expectedSymbols.split(";"));
    for (var symbol : List.of("Клиент", "Сервер", "ТолстыйКлиентУправляемоеПриложение",
      "ТолстыйКлиентОбычноеПриложение", "ВнешнееСоединение")) {
      var branches = contexts("#Если " + symbol + " Тогда\nМетка();\n#Иначе\nМетка();\n#КонецЕсли\n");
      assertThat(branches.get(0).contains(context)).as("%s in %s", symbol, context).isEqualTo(expected.contains(symbol));
      assertThat(branches.get(1).contains(context)).as("NOT %s in %s", symbol, context)
        .isEqualTo(!expected.contains(symbol));
    }
  }

  private static List<Set<ExecutionContext>> contexts(String code) {
    var evaluator = new PreprocessorContexts();
    List<Set<ExecutionContext>> result = new ArrayList<>();
    ParseTreeWalker.DEFAULT.walk(new BSLParserBaseListener() {
      @Override
      public void enterPreproc_if(BSLParser.Preproc_ifContext ctx) { evaluator.enterIf(ctx); }
      @Override
      public void enterPreproc_elsif(BSLParser.Preproc_elsifContext ctx) { evaluator.enterElsif(ctx); }
      @Override
      public void enterPreproc_else(BSLParser.Preproc_elseContext ctx) { evaluator.enterElse(); }
      @Override
      public void enterPreproc_endif(BSLParser.Preproc_endifContext ctx) { evaluator.enterEndif(); }
      @Override
      public void enterGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
        var active = evaluator.active();
        result.add(active == null ? null : Set.copyOf(active));
      }
    }, new BSLTokenizer(code).getAst());
    assertThat(result).as("Calls in %s; AST: %s", code,
      new BSLTokenizer(code).getAst().toStringTree(Arrays.asList(BSLParser.ruleNames))).isNotEmpty();
    return result;
  }
}
