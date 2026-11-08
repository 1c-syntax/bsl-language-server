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
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.types.DereferenceLocator;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.types.index.InferredExpressionTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslExpression;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;

class ExternalRootContextCacheTest extends AbstractServerContextAwareTest {
  private static final String FACTORY = """
    Функция Фабрика()
    #Если Сервер Тогда
      Возврат Новый Структура;
    #Иначе
      Возврат Новый Массив;
    #КонецЕсли
    КонецФункции
    """;
  @Autowired private ExpressionTypeInferencer inferencer;
  @Autowired private InferredExpressionTypeIndex index;

  private record Fixture(DocumentContext document, BslExpression expression,
                         ReturnTypeFromBodyInference bodies, ExpressionTypeInferencer target) { }

  @Test
  void neutralRootPublishesSeparateCompleteModes() {
    withFactory(FACTORY, f -> {
      assertThat(inferencer.infer(f.expression(), f.document()).refs()).extracting(TypeRef::qualifiedName)
        .containsExactlyInAnyOrder("Структура", "Массив");
      assertThat(index.contextualQuery(f.document().getUri(), f.expression().getRepresentingAst(),
        ExecutionContext.SERVER).cached()).isNotNull().satisfies(types ->
          assertThat(types.refs()).extracting(TypeRef::qualifiedName).containsExactly("Структура"));
      assertThat(index.contextualQuery(f.document().getUri(), f.expression().getRepresentingAst(),
        ExecutionContext.THIN_CLIENT).cached()).isNotNull().satisfies(types ->
          assertThat(types.refs()).extracting(TypeRef::qualifiedName).containsExactly("Массив"));
    });
  }

  @Test
  void neutralRootThenExplicitModesDoesNotRepeatBodyInference() {
    withFactory(FACTORY, f -> {
      inferencer.infer(f.expression(), f.document());
      var firstCalls = mockingDetails(f.bodies()).getInvocations().size();
      assertThat(firstCalls).isPositive();
      assertThat(inferencer.infer(f.expression(), f.document(), ExecutionContext.SERVER).refs())
        .extracting(TypeRef::qualifiedName).containsExactly("Структура");
      assertThat(inferencer.infer(f.expression(), f.document(), ExecutionContext.THIN_CLIENT).refs())
        .extracting(TypeRef::qualifiedName).containsExactly("Массив");
      assertThat(mockingDetails(f.bodies()).getInvocations()).hasSize(firstCalls);
    });
  }

  @Test
  void explicitModesThenNeutralRootDoesNotRepeatBodyInference() {
    withFactory(FACTORY, f -> {
      inferencer.infer(f.expression(), f.document(), ExecutionContext.SERVER);
      inferencer.infer(f.expression(), f.document(), ExecutionContext.THIN_CLIENT);
      var firstCalls = mockingDetails(f.bodies()).getInvocations().size();
      assertThat(firstCalls).isPositive();
      assertThat(inferencer.infer(f.expression(), f.document()).refs()).extracting(TypeRef::qualifiedName)
        .containsExactlyInAnyOrder("Структура", "Массив");
      assertThat(mockingDetails(f.bodies()).getInvocations()).hasSize(firstCalls);
    });
  }

  @Test
  void neutralRootBeforePopulationDoesNotSeedModeResults() {
    withFactory(FACTORY, f -> {
      ReflectionTestUtils.setField(f.target(), "workspacePopulated", false);
      inferencer.infer(f.expression(), f.document());
      assertNoModeResults(f);
    });
  }

  @Test
  void neutralRootRecursiveCutDoesNotSeedModeResults() {
    withFactory("""
      Функция Фабрика()
        Локальная = Фабрика();
        Возврат Локальная;
      КонецФункции
      """, f -> {
      inferencer.infer(f.expression(), f.document());
      assertNoModeResults(f);
      assertThat(index.get(f.document().getUri(), f.expression().getRepresentingAst())).isNull();
    });
  }

  private void assertNoModeResults(Fixture f) {
    for (var mode : Set.of(ExecutionContext.SERVER, ExecutionContext.THIN_CLIENT)) {
      assertThat(index.contextualQuery(f.document().getUri(), f.expression().getRepresentingAst(), mode).cached()).isNull();
    }
  }

  private void withFactory(String factory, Consumer<Fixture> check) {
    var root = Path.of("src/test/resources/metadata/commonModuleCalls");
    initServerContext(root, false);
    var document = TestUtils.getDocumentContext(root.resolve("CommonModules/КлиентСерверныйМодуль/Ext/Module.bsl").toUri(),
      factory + "\nПроцедура Проверка()\n  Результат = Фабрика().Количество();\nКонецПроцедуры\n", context);
    var call = Trees.findAllRuleNodes(document.getAst(), BSLParser.RULE_methodCall).stream()
      .map(BSLParser.MethodCallContext.class::cast).findFirst().orElseThrow();
    var expression = DereferenceLocator.locate(call.methodName().IDENTIFIER()).getLeft();
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var originalResolver = (CompilationContextResolver) ReflectionTestUtils.getField(target, "compilationContextResolver");
    var originalBodies = (ReturnTypeFromBodyInference) ReflectionTestUtils.getField(target, "returnTypeFromBodyInference");
    CompilationContextResolver resolverTarget = AopTestUtils.getUltimateTargetObject(originalResolver);
    ReturnTypeFromBodyInference bodyTarget = AopTestUtils.getUltimateTargetObject(originalBodies);
    var resolver = spy(resolverTarget);
    var bodies = spy(bodyTarget);
    doReturn(Optional.of(new CompilationContextResolver.MethodContext(Optional.empty(),
      Set.of(ExecutionContext.SERVER, ExecutionContext.THIN_CLIENT))))
      .when(resolver).contextAt(same(document), any(Position.class), any());
    var populated = ReflectionTestUtils.getField(target, "workspacePopulated");
    ReflectionTestUtils.setField(target, "compilationContextResolver", resolver);
    ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", bodies);
    ReflectionTestUtils.setField(target, "workspacePopulated", true);
    try { check.accept(new Fixture(document, expression, bodies, target)); }
    finally {
      ReflectionTestUtils.setField(target, "compilationContextResolver", originalResolver);
      ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", originalBodies);
      ReflectionTestUtils.setField(target, "workspacePopulated", populated);
    }
  }
}
