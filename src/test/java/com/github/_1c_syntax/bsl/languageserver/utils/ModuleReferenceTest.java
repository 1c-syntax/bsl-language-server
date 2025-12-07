/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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

  private static final List<String> DEFAULT_ACCESSORS = new ReferencesOptions().getCommonModuleAccessors();

  @Test
  void testDetectCommonModuleExpression() {
    var code = "Процедура Тест()\n" +
      "  Модуль = ОбщегоНазначения.ОбщийМодуль(\"ПервыйОбщийМодуль\");\n" +
      "КонецПроцедуры";
    
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
    assertThat(moduleName).isPresent();
    assertThat(moduleName.get()).isEqualTo("ПервыйОбщийМодуль");
  }

  @Test
  void testCustomAccessor() {
    var code = "Процедура Тест()\n" +
      "  Модуль = МойМодуль.ПолучитьОбщийМодуль(\"ТестовыйМодуль\");\n" +
      "КонецПроцедуры";
    
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
    var customAccessors = List.of("МойМодуль.ПолучитьОбщийМодуль");
    assertThat(ModuleReference.isCommonModuleExpression(expression, customAccessors)).isTrue();
    
    var moduleName = ModuleReference.extractCommonModuleName(expression, customAccessors);
    assertThat(moduleName).isPresent();
    assertThat(moduleName.get()).isEqualTo("ТестовыйМодуль");
  }

  @Test
  void testDetectManagerModuleExpression() {
    // Проверяем, что null возвращает false
    assertThat(ModuleReference.isManagerModuleExpression(null)).isFalse();
  }

  @Test
  void testExtractManagerModuleInfoCatalogs() {
    var code = "Процедура Тест()\n" +
      "  Результат = Справочники.Номенклатура.НайтиПоКоду(\"001\");\n" +
      "КонецПроцедуры";
    
    var documentContext = TestUtils.getDocumentContext(code);
    var ast = documentContext.getAst();
    
    var assignments = new ArrayList<BSLParser.AssignmentContext>();
    Trees.findAllRuleNodes(ast, BSLParser.RULE_assignment).forEach(node -> 
      assignments.add((BSLParser.AssignmentContext) node)
    );
    
    assertThat(assignments).hasSize(1);
    
    var expression = assignments.get(0).expression();
    assertThat(ModuleReference.isManagerModuleExpression(expression)).isTrue();
    
    var managerInfo = ModuleReference.extractManagerModuleInfo(expression);
    assertThat(managerInfo).isPresent();
    assertThat(managerInfo.get().managerType()).isEqualTo("Справочники");
    assertThat(managerInfo.get().objectName()).isEqualTo("Номенклатура");
  }

  @Test
  void testExtractManagerModuleInfoDocuments() {
    var code = "Процедура Тест()\n" +
      "  Результат = Документы.ПриходнаяНакладная.СоздатьДокумент();\n" +
      "КонецПроцедуры";
    
    var documentContext = TestUtils.getDocumentContext(code);
    var ast = documentContext.getAst();
    
    var assignments = new ArrayList<BSLParser.AssignmentContext>();
    Trees.findAllRuleNodes(ast, BSLParser.RULE_assignment).forEach(node -> 
      assignments.add((BSLParser.AssignmentContext) node)
    );
    
    assertThat(assignments).hasSize(1);
    
    var expression = assignments.get(0).expression();
    assertThat(ModuleReference.isManagerModuleExpression(expression)).isTrue();
    
    var managerInfo = ModuleReference.extractManagerModuleInfo(expression);
    assertThat(managerInfo).isPresent();
    assertThat(managerInfo.get().managerType()).isEqualTo("Документы");
    assertThat(managerInfo.get().objectName()).isEqualTo("ПриходнаяНакладная");
  }

  @Test
  void testExtractManagerModuleInfoEnglishSyntax() {
    var code = "Procedure Test()\n" +
      "  Result = Catalogs.Nomenclature.FindByCode(\"001\");\n" +
      "EndProcedure";
    
    var documentContext = TestUtils.getDocumentContext(code);
    var ast = documentContext.getAst();
    
    var assignments = new ArrayList<BSLParser.AssignmentContext>();
    Trees.findAllRuleNodes(ast, BSLParser.RULE_assignment).forEach(node -> 
      assignments.add((BSLParser.AssignmentContext) node)
    );
    
    assertThat(assignments).hasSize(1);
    
    var expression = assignments.get(0).expression();
    assertThat(ModuleReference.isManagerModuleExpression(expression)).isTrue();
    
    var managerInfo = ModuleReference.extractManagerModuleInfo(expression);
    assertThat(managerInfo).isPresent();
    assertThat(managerInfo.get().managerType()).isEqualTo("Catalogs");
    assertThat(managerInfo.get().objectName()).isEqualTo("Nomenclature");
  }

  @Test
  void testExtractManagerModuleInfoInformationRegisters() {
    var code = "Процедура Тест()\n" +
      "  Результат = РегистрыСведений.КурсыВалют.СоздатьМенеджерЗаписи();\n" +
      "КонецПроцедуры";
    
    var documentContext = TestUtils.getDocumentContext(code);
    var ast = documentContext.getAst();
    
    var assignments = new ArrayList<BSLParser.AssignmentContext>();
    Trees.findAllRuleNodes(ast, BSLParser.RULE_assignment).forEach(node -> 
      assignments.add((BSLParser.AssignmentContext) node)
    );
    
    assertThat(assignments).hasSize(1);
    
    var expression = assignments.get(0).expression();
    assertThat(ModuleReference.isManagerModuleExpression(expression)).isTrue();
    
    var managerInfo = ModuleReference.extractManagerModuleInfo(expression);
    assertThat(managerInfo).isPresent();
    assertThat(managerInfo.get().managerType()).isEqualTo("РегистрыСведений");
    assertThat(managerInfo.get().objectName()).isEqualTo("КурсыВалют");
  }

  @Test
  void testNonManagerModuleExpression() {
    var code = "Процедура Тест()\n" +
      "  Результат = МояПеременная.Метод();\n" +
      "КонецПроцедуры";
    
    var documentContext = TestUtils.getDocumentContext(code);
    var ast = documentContext.getAst();
    
    var assignments = new ArrayList<BSLParser.AssignmentContext>();
    Trees.findAllRuleNodes(ast, BSLParser.RULE_assignment).forEach(node -> 
      assignments.add((BSLParser.AssignmentContext) node)
    );
    
    assertThat(assignments).hasSize(1);
    
    var expression = assignments.get(0).expression();
    assertThat(ModuleReference.isManagerModuleExpression(expression)).isFalse();
    
    var managerInfo = ModuleReference.extractManagerModuleInfo(expression);
    assertThat(managerInfo).isEmpty();
  }
}
