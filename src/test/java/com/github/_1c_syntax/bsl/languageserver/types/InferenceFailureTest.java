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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.CommonModuleByNameInference;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.ReturnTypeFromBodyInference;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionTreeBuildingVisitor;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Сорвавшийся расчёт не роняет анализ: он отдаёт пустой тип и оставляет запись в журнале.
 * <p>
 * Молчать здесь нельзя. Снаружи пустой тип неотличим от честного «ничего не вывелось»,
 * поэтому поломка выглядит как «типы просто не выводятся» и обнаруживается только замером
 * на большой конфигурации.
 */
@CleanupContextBeforeClassAndAfterClass
class InferenceFailureTest extends AbstractServerContextAwareTest {

  @Autowired
  private ExpressionTypeInferencer inferencer;

  @MockitoBean
  private ReturnTypeFromBodyInference returnTypeFromBodyInference;

  @MockitoBean
  private CommonModuleByNameInference commonModuleByNameInference;

  @Test
  void failedComputationOfMethodValueYieldsEmptyInsteadOfPropagating() {
    // given: расчёт значения метода срывается.
    when(returnTypeFromBodyInference.of(any(), any())).thenThrow(new IllegalStateException("сбой"));
    var documentContext = TestUtils.getDocumentContext("""
      Функция Ф() Экспорт
        Возврат Новый Массив;
      КонецФункции
      """);
    var method = documentContext.getSymbolTree().getMethodSymbol("Ф");
    assertThat(method).isPresent();

    // when, then: наружу срыв не выходит, значение выходит пустым.
    assertThatCode(() -> {
      var computed = inferencer.computeReturnTypes(method.get());
      assertThat(computed.types().isEmpty()).isTrue();
    }).doesNotThrowAnyException();
  }

  @Test
  void failedInferenceOfExpressionYieldsEmptyInsteadOfPropagating() {
    // given: срывается уточнение вызова — оно зовётся прямо из разбора выражения, поэтому
    // срыв доходит до самого вывода, а не гасится расчётом значения метода по дороге.
    when(commonModuleByNameInference.localCallTypes(any(), any()))
      .thenThrow(new IllegalStateException("сбой"));
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Проверка() Экспорт
        Значение = Ф();
      КонецПроцедуры
      Функция Ф()
        Возврат Новый Массив;
      КонецФункции
      """);
    var assignment = Trees.findAllRuleNodes(documentContext.getAst(), BSLParser.RULE_assignment)
      .stream().findFirst();
    assertThat(assignment).isPresent();
    var expression = ExpressionTreeBuildingVisitor.buildExpressionTree(
      ((BSLParser.AssignmentContext) assignment.get()).expression());
    assertThat(expression).isNotNull();

    // when, then
    assertThatCode(() -> assertThat(inferencer.infer(expression, documentContext).isEmpty()).isTrue())
      .doesNotThrowAnyException();
  }
}
