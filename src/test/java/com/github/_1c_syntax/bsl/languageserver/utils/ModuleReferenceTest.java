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
package com.github._1c_syntax.bsl.languageserver.utils;

import com.github._1c_syntax.bsl.languageserver.configuration.references.ReferencesOptions;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class ModuleReferenceTest {

  private static final ModuleReference.ParsedAccessors DEFAULT_ACCESSORS =
    ModuleReference.parseAccessors(new ReferencesOptions().getCommonModuleAccessors());

  @Test
  void testDetectCommonModuleExpression() {
    var code = """
      Процедура Тест()
        Модуль = ОбщегоНазначения.ОбщийМодуль("ПервыйОбщийМодуль");
      КонецПроцедуры""";

    var documentContext = TestUtils.getDocumentContext(code);
    var ast = documentContext.getAst();

    // Find assignment
    var assignments = new ArrayList<BSLParser.AssignmentContext>();
    Trees.findAllRuleNodes(ast, BSLParser.RULE_assignment).forEach(node ->
      assignments.add((BSLParser.AssignmentContext) node)
    );

    assertThat(assignments).hasSize(1);

    var expression = assignments.get(0).expression();
    assertThat(ModuleReference.isCommonModuleExpression(expression, DEFAULT_ACCESSORS)).isTrue();

    var moduleName = ModuleReference.extractCommonModuleName(expression, DEFAULT_ACCESSORS);
    assertThat(moduleName)
      .isPresent()
      .contains("ПервыйОбщийМодуль");
  }

  @Test
  void testCustomAccessor() {
    var code = """
      Процедура Тест()
        Модуль = МойМодуль.ПолучитьОбщийМодуль("ТестовыйМодуль");
      КонецПроцедуры""";

    var documentContext = TestUtils.getDocumentContext(code);
    var ast = documentContext.getAst();

    var assignments = new ArrayList<BSLParser.AssignmentContext>();
    Trees.findAllRuleNodes(ast, BSLParser.RULE_assignment).forEach(node ->
      assignments.add((BSLParser.AssignmentContext) node)
    );

    assertThat(assignments).hasSize(1);

    var expression = assignments.get(0).expression();

    // With default accessors - should not match
    assertThat(ModuleReference.isCommonModuleExpression(expression, DEFAULT_ACCESSORS)).isFalse();

    // With custom accessor - should match
    var customAccessors = ModuleReference.parseAccessors(List.of("МойМодуль.ПолучитьОбщийМодуль"));
    assertThat(ModuleReference.isCommonModuleExpression(expression, customAccessors)).isTrue();

    var moduleName = ModuleReference.extractCommonModuleName(expression, customAccessors);
    assertThat(moduleName)
      .isPresent()
      .contains("ТестовыйМодуль");
  }

  @Test
  void testParseAccessors() {
    var accessors = List.of(
      "ОбщийМодуль",
      "CommonModule",
      "ОбщегоНазначения.ОбщийМодуль",
      "Common.CommonModule"
    );

    var parsed = ModuleReference.parseAccessors(accessors);

    // Проверяем локальные методы
    assertThat(parsed.localMethods()).containsExactlyInAnyOrder("общиймодуль", "commonmodule");

    // Проверяем пары модуль.метод
    assertThat(parsed.moduleMethodPairs()).containsKey("общегоназначения");
    assertThat(parsed.moduleMethodPairs().get("общегоназначения")).contains("общиймодуль");
    assertThat(parsed.moduleMethodPairs()).containsKey("common");
    assertThat(parsed.moduleMethodPairs().get("common")).contains("commonmodule");
  }

  @Test
  void testExtractCommonModuleNameContext() {
    var code = """
      Процедура Тест()
        Модуль = ОбщегоНазначения.ОбщийМодуль("ПервыйОбщийМодуль");
      КонецПроцедуры""";

    var documentContext = TestUtils.getDocumentContext(code);
    var ast = documentContext.getAst();

    var assignments = new ArrayList<BSLParser.AssignmentContext>();
    Trees.findAllRuleNodes(ast, BSLParser.RULE_assignment).forEach(node ->
      assignments.add((BSLParser.AssignmentContext) node)
    );

    assertThat(assignments).hasSize(1);

    var expression = assignments.get(0).expression();

    // Проверяем, что контекст параметра извлекается корректно
    var moduleNameContext = ModuleReference.extractCommonModuleNameContext(expression, DEFAULT_ACCESSORS);
    assertThat(moduleNameContext).isPresent();

    // Проверяем, что текст содержит имя модуля с кавычками
    assertThat(moduleNameContext.get().getText()).isEqualTo("\"ПервыйОбщийМодуль\"");

    // Проверяем диапазон (range) контекста параметра
    var startToken = moduleNameContext.get().getStart();
    var stopToken = moduleNameContext.get().getStop();

    // Строка 2 (индекс 1), позиция должна соответствовать строковому литералу
    assertThat(startToken.getLine()).isEqualTo(2);
    assertThat(stopToken.getLine()).isEqualTo(2);
    // Проверяем, что это именно строковый литерал (начинается с кавычки)
    assertThat(startToken.getText()).startsWith("\"");
  }
}
