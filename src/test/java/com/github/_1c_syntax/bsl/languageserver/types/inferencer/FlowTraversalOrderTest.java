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

import com.github._1c_syntax.bsl.languageserver.cfg.CfgBuildOptions;
import com.github._1c_syntax.bsl.languageserver.cfg.ControlFlowGraphIndex;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

@CleanupContextBeforeClassAndAfterClass
class FlowTraversalOrderTest extends AbstractServerContextAwareTest {
  @Autowired
  private TypeService typeService;
  @Autowired
  private ControlFlowGraphIndex graphs;
  @Autowired
  private VariableFlowAnalyzer flow;

  @Test
  void acyclicNestedBranchesSettleInOneForwardSweep() {
    var source = "Процедура Проверка(Условие)\n  Значение = 1;\n"
      + "  Если Условие Тогда\n".repeat(12)
      + "    Значение = \"строка\";\n"
      + "  Иначе\n    Значение = ТекущаяДата();\n  КонецЕсли;\n".repeat(12)
      + "  Результат = Значение;\nКонецПроцедуры\n";
    var document = TestUtils.getDocumentContext(source, context);
    var line = source.substring(0, source.indexOf("Результат =")).lines().count() - 1;
    var position = new Position((int) line, "  Результат = ".length());
    var body = VariableFlowAnalyzer.bodyAt(document, position);
    var graph = graphs.graphOf(document, body, CfgBuildOptions.defaults());
    flow.clear(document.getUri());
    var narrowed = new AtomicInteger();
    try (var counted = mockStatic(FlowConditionNarrowing.class, CALLS_REAL_METHODS)) {
      counted.when(() -> FlowConditionNarrowing.narrow(any(), any(), any(), any(), any(), any()))
        .thenAnswer(call -> {
          narrowed.incrementAndGet();
          return call.callRealMethod();
        });
      assertThat(typeService.expressionTypesAt(document, position).refs()).extracting(TypeRef::qualifiedName)
        .containsExactlyInAnyOrder("Строка", "Дата");
      assertThat(narrowed.get()).isPositive().isLessThanOrEqualTo(2 * graph.edgeSet().size());
    }
  }

  @Test
  void loopRetainsEntryAndBodyTypes() {
    var source = """
      Процедура Проверка(Условие)
        Значение = 1;
        Пока Условие Цикл
          Значение = "строка";
        КонецЦикла;
        Результат = Значение;
      КонецПроцедуры
      """;
    var document = TestUtils.getDocumentContext(source, context);
    var line = source.substring(0, source.indexOf("Результат =")).lines().count() - 1;
    var position = new Position((int) line, "  Результат = ".length());
    assertThat(typeService.expressionTypesAt(document, position).refs())
      .extracting(TypeRef::qualifiedName).containsExactlyInAnyOrder("Число", "Строка");
  }

  @Test
  void longAcyclicGraphDoesNotUseJavaRecursion() {
    var source = "Процедура Проверка(Условие)\n  Значение = 1;\n"
      + "  Если Условие Тогда\n    Сообщить(\"\");\n  КонецЕсли;\n".repeat(2000)
      + "  Результат = Значение;\nКонецПроцедуры\n";
    var document = TestUtils.getDocumentContext(source, context);
    var line = source.substring(0, source.indexOf("Результат =")).lines().count() - 1;
    assertThat(typeService.expressionTypesAt(document, new Position((int) line, "  Результат = ".length())).refs())
      .extracting(TypeRef::qualifiedName).containsExactly("Число");
  }
}
