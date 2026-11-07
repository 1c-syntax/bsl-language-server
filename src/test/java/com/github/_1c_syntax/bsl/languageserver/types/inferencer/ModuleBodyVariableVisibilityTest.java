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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleBodyVariableVisibilityTest extends AbstractServerContextAwareTest {
  @Autowired
  private ExpressionTypeInferencer inferencer;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void onlyDeclaredModuleVariablesAreVisibleInsideMethods(boolean declared, TestReporter reporter) {
    var document = document(declared);
    var tree = document.getSymbolTree();
    var variable = tree.getVariableSymbol("Модульное", tree.getModule()).orElseThrow();
    reporter.publishEntry("moduleVariableKind", variable.getKind().name());
    var use = Trees.findAllRuleNodes(document.getAst(), BSLParser.RULE_complexIdentifier).stream()
      .map(BSLParser.ComplexIdentifierContext.class::cast)
      .filter(node -> node.IDENTIFIER() != null && node.IDENTIFIER().getText().equals("Модульное"))
      .filter(node -> Trees.getAncestorByRuleIndex(node, BSLParser.RULE_sub) != null)
      .findFirst().orElseThrow();
    var resolved = tree.getVariableSymbolInScope(use, "Модульное");
    reporter.publishEntry("methodResolvedKind", resolved.map(VariableSymbol::getKind).map(Enum::name).orElse("none"));
    assertThat(resolved.isPresent()).as("visibility from a method").isEqualTo(declared);
    var assignment = Trees.findAllRuleNodes(document.getAst(), BSLParser.RULE_lValue).stream()
      .map(BSLParser.LValueContext.class::cast).findFirst().orElseThrow();
    assertThat(tree.getVariableSymbolInScope(assignment, "Модульное")).as("visibility in the module body").isPresent();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void aModuleBodyLocalDoesNotMakeTheFunctionIncomplete(boolean declared, TestReporter reporter) {
    var document = document(declared);
    var method = document.getSymbolTree().getMethodSymbol("Фабрика").orElseThrow();
    var result = inferencer.computeReturnTypes(method);
    reporter.publishEntry("incomplete", Boolean.toString(result.incomplete()));
    reporter.publishEntry("returnedTypes", result.types().refs().toString());
    assertThat(result.incomplete()).as("an out-of-scope name is not a failed flow calculation").isFalse();
    assertThat(result.types().refs().isEmpty()).isEqualTo(!declared);
  }

  private DocumentContext document(boolean declared) {
    initServerContext();
    return TestUtils.getDocumentContext(URI.create("file:///module-body-visibility.bsl"),
      (declared ? "Перем Модульное;\n" : "") + """
      Функция Фабрика()
        Возврат Модульное;
      КонецФункции
      Модульное = "версия";
      """, context);
  }
}
