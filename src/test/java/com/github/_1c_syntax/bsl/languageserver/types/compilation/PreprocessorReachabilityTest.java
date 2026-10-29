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

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.compilation.PreprocessorReachability;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseListener;
import com.github._1c_syntax.bsl.parser.BSLTokenizer;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PreprocessorReachabilityTest {
  @ParameterizedTest
  @CsvSource(value = {
    "BSL | Сервер И НЕ Сервер | true",
    "BSL | Клиент И Сервер | false",
    "BSL | Сервер И ТонкийКлиент | true",
    "BSL | Client И НЕ Клиент | true",
    "OS | Сервер И ТонкийКлиент | false",
    "OS | Client И НЕ Клиент | false",
    "OS | Windows | false",
    "OS | Windows И Linux | false",
    "OS | Windows И НЕ wInDoWs | true",
    "OS | НЕ Windows И Windows | true",
    "OS | NOT Windows AND Windows | true",
    "OS | MyFlag И НЕ myflag | true",
    "OS | (Windows ИЛИ Linux) И НЕ Windows И НЕ Linux | true",
    "OS | (НЕ (Windows ИЛИ Linux)) И Windows | true",
    "OS | Windows ИЛИ Linux И НЕ Linux | false",
    "OS | НЕ Windows ИЛИ Windows | false",
    "OS | (A ИЛИ B) И (A ИЛИ НЕ B) И (НЕ A ИЛИ B) И (НЕ A ИЛИ НЕ B) | true",
    "OS | (A ИЛИ B) И (A ИЛИ НЕ B) И (НЕ A ИЛИ B) | false"
  }, delimiter = '|')
  void provesImpossibilityWithoutAssumingDefinitions(FileType fileType, String condition, boolean unreachable) {
    assertThat(branches("#Если " + condition + " Тогда\nМетка();\n#КонецЕсли\n", fileType))
      .containsExactly(unreachable);
  }

  @ParameterizedTest
  @EnumSource(FileType.class)
  void respectsParentsAndPreviousAlternatives(FileType fileType) {
    assertThat(branches("""
      #Если Windows Тогда
        #Если НЕ Windows Тогда
          Метка();
        #Иначе
          Метка();
        #КонецЕсли
      #ИначеЕсли Windows Тогда
        Метка();
      #ИначеЕсли НЕ Windows Тогда
        Метка();
      #Иначе
        Метка();
      #КонецЕсли
      Метка();
      """, fileType)).containsExactly(true, false, true, false, true, false);
  }

  private static List<Boolean> branches(String source, FileType fileType) {
    var reachability = new PreprocessorReachability(fileType);
    var result = new ArrayList<Boolean>();
    ParseTreeWalker.DEFAULT.walk(new BSLParserBaseListener() {
      @Override
      public void enterPreprocessor(BSLParser.PreprocessorContext ctx) { reachability.enter(ctx); }
      @Override
      public void enterGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
        result.add(reachability.isUnreachable());
      }
    }, new BSLTokenizer(source).getAst());
    assertThat(reachability.isWellFormed()).isTrue();
    return result;
  }
}
