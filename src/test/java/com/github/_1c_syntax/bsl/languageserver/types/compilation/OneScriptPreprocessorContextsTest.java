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
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.compilation.PreprocessorContexts;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLTokenizer;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class OneScriptPreprocessorContextsTest {
  @ParameterizedTest
  @CsvSource({"Windows, OSCRIPT_WINDOWS, OSCRIPT_WINDOWS_MONO", "Linux, OSCRIPT_LINUX, OSCRIPT_LINUX_MONO",
    "MacOS, OSCRIPT_MACOS, OSCRIPT_MACOS_MONO"})
  void monoIsIndependentFromTheOperatingSystem(String os, ExecutionContext normal, ExecutionContext mono) {
    assertThat(PreprocessorContexts.evaluate(expression(os), FileType.OS)).containsExactlyInAnyOrder(normal, mono);
    assertThat(PreprocessorContexts.evaluate(expression(os + " И MONO"), FileType.OS)).containsExactly(mono);
    assertThat(PreprocessorContexts.evaluate(expression(os + " И НЕ mono"), FileType.OS)).containsExactly(normal);
  }

  @Test
  void domainsAreDisjointAndNegationUsesOnlyTheSelectedDialect() {
    assertThat(ExecutionContext.ALL).doesNotContainAnyElementsOf(ExecutionContext.OSCRIPT);
    assertThat(PreprocessorContexts.evaluate(expression("НЕ Windows"), FileType.OS))
      .containsExactlyInAnyOrder(ExecutionContext.OSCRIPT_LINUX, ExecutionContext.OSCRIPT_LINUX_MONO,
        ExecutionContext.OSCRIPT_MACOS, ExecutionContext.OSCRIPT_MACOS_MONO);
    assertThat(PreprocessorContexts.evaluate(expression("Windows И Linux"), FileType.OS)).isEmpty();
    assertThat(PreprocessorContexts.evaluate(expression("Windows ИЛИ Linux ИЛИ MacOS"), FileType.OS))
      .containsExactlyInAnyOrderElementsOf(ExecutionContext.OSCRIPT);
  }

  @Test
  void unknownSymbolsRemainUnknownAndBslDoesNotAcquireOneScriptDefinitions() {
    assertThat(PreprocessorContexts.evaluate(expression("CustomFlag"), FileType.OS)).isNull();
    assertThat(PreprocessorContexts.evaluate(expression("Сервер"), FileType.OS)).isNull();
    assertThat(PreprocessorContexts.evaluate(expression("Windows"), FileType.BSL)).isNull();
  }

  private static BSLParser.Preproc_expressionContext expression(String condition) {
    var tokenizer = new BSLTokenizer("#Если " + condition + " Тогда\nМетка();\n#КонецЕсли\n");
    var node = (BSLParser.Preproc_ifContext) Trees.findAllRuleNodes(tokenizer.getAst(), BSLParser.RULE_preproc_if)
      .iterator().next();
    return node.preproc_expression();
  }
}
