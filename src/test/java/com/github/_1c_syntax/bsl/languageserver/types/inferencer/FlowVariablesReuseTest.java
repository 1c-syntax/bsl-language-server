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
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

class FlowVariablesReuseTest extends AbstractServerContextAwareTest {
  @Autowired
  private TypeService typeService;

  @Test
  void bodyStructureIsPreparedOnceAcrossModesAndRebuiltAfterEdit(TestReporter reporter) {
    var root = Path.of("src/test/resources/metadata/commonModuleCalls");
    initServerContext(root, false);
    var source = "Функция Проверка()\n  Х = 1;\n  Возврат Х;\nКонецФункции\n";
    var document = TestUtils.getDocumentContext(
      root.resolve("CommonModules/КлиентСерверныйМодуль/Ext/Module.bsl").toUri(), source, context);
    var preparations = new AtomicInteger();
    try (var counted = mockStatic(FlowVariables.class, CALLS_REAL_METHODS)) {
      counted.when(() -> FlowVariables.collect(any(), any(), any(), any())).thenAnswer(call -> {
        preparations.incrementAndGet();
        return call.callRealMethod();
      });
      for (var mode : new ExecutionContext[]{ExecutionContext.SERVER, ExecutionContext.THIN_CLIENT,
          ExecutionContext.MOBILE_APPLICATION_SERVER}) {
        assertThat(typeService.expressionTypesAt(document, new Position(2, 10), mode).refs())
          .extracting(TypeRef::qualifiedName).containsExactly("Число");
      }
      reporter.publishEntry("bodyPreparations", Integer.toString(preparations.get()));
      assertThat(preparations.get()).as("one structural preparation for three execution contexts").isEqualTo(1);
      context.rebuildDocument(document, source.replace("= 1", "= \"строка\""), 1);
      assertThat(typeService.expressionTypesAt(document, new Position(2, 10), ExecutionContext.SERVER).refs())
        .extracting(TypeRef::qualifiedName).containsExactly("Строка");
      assertThat(preparations.get()).as("editing the source invalidates the structural preparation").isEqualTo(2);
    }
  }
}