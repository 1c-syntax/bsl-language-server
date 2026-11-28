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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AbstractFindMethodDiagnosticTest {

  @Test
  void singleConstantArgumentMatchesOnlyGivenToken() {
    var calls = globalCalls("""
      Метод(Ложь);
      Метод(Истина);
      Метод(Ложь, Истина);
      Метод(Переменная);
      Метод();
      """);

    assertThat(AbstractFindMethodDiagnostic.isCalledWithSingleConstant(calls.get(0), BSLParser.FALSE)).isTrue();
    assertThat(AbstractFindMethodDiagnostic.isCalledWithSingleConstant(calls.get(0), BSLParser.TRUE)).isFalse();
    assertThat(AbstractFindMethodDiagnostic.isCalledWithSingleConstant(calls.get(1), BSLParser.TRUE)).isTrue();
    assertThat(AbstractFindMethodDiagnostic.isCalledWithSingleConstant(calls.get(2), BSLParser.FALSE)).isFalse();
    assertThat(AbstractFindMethodDiagnostic.isCalledWithSingleConstant(calls.get(3), BSLParser.FALSE)).isFalse();
    assertThat(AbstractFindMethodDiagnostic.isCalledWithSingleConstant(calls.get(4), BSLParser.FALSE)).isFalse();
  }

  private static List<BSLParser.GlobalMethodCallContext> globalCalls(String content) {
    var ast = TestUtils.getDocumentContext(content).getAst();
    return Trees.findAllRuleNodes(ast, BSLParser.RULE_globalMethodCall).stream()
      .map(BSLParser.GlobalMethodCallContext.class::cast)
      .toList();
  }
}
