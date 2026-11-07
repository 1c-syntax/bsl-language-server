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
package com.github._1c_syntax.bsl.languageserver.types.model;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

@CleanupContextBeforeClassAndAfterClass
class CallBoundaryUnionTest extends AbstractServerContextAwareTest {
  @Autowired
  private TypeService typeService;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void repeatedCallsDoNotMergeUnchangedValuesAgain(boolean separateBlocks, TestReporter reporter) {
    var source = """
      Перем Общая;
      Процедура Проверка(Условие)
        Перем Значение;
        Значение = 1;
        Сообщить("");
        Значение = "";
      """ + (separateBlocks
      ? "  Если Условие Тогда\n    Сообщить(\"\");\n  КонецЕсли;\n"
      : "  Сообщить(\"\");\n").repeat(400) + """
        Результат = Значение;
      КонецПроцедуры
      """;
    var document = TestUtils.getDocumentContext(source, context);
    var line = source.substring(0, source.indexOf("Результат =")).lines().count() - 1;
    var merges = new AtomicInteger();
    try (var counted = mockStatic(TypeDecorations.class, CALLS_REAL_METHODS)) {
      counted.when(() -> TypeDecorations.mergedFlat(any(), any(), any())).thenAnswer(call -> {
        merges.incrementAndGet();
        return call.callRealMethod();
      });
      assertThat(typeService.expressionTypesAt(document, new Position((int) line, "  Результат = ".length())).refs())
        .extracting(TypeRef::qualifiedName).containsExactly("Строка");
      var method = document.getSymbolTree().getMethodSymbol("Проверка").orElseThrow();
      var variable = document.getSymbolTree().getVariableSymbol("Значение", method).orElseThrow();
      var declaration = new Reference(variable, variable, document.getUri(), variable.getSelectionRange(),
        OccurrenceType.DEFINITION);
      assertThat(typeService.typesAt(declaration).refs()).extracting(TypeRef::qualifiedName)
        .containsExactlyInAnyOrder("Число", "Строка");
      reporter.publishEntry("decorationMerges", Integer.toString(merges.get()));
      assertThat(merges.get()).as("repeated calls with unchanged values need no repeated union per variable")
        .isLessThan(200);
    }
  }
  @Test
  void callsAcrossBranchesAndLoopIterationsKeepIncomingAndResetValues() {
    var source = """
      Перем Общая;
      Процедура Изменить(Условие)
        Общая = 1;
        Сообщить("");
        Общая = "";
        Если Условие Тогда
          Сообщить("");
        Иначе
          Сообщить("");
        КонецЕсли;
        Общая = Ложь;
        Пока Условие Цикл
          Сообщить("");
          Общая = Новый Массив;
        КонецЦикла;
        Общая = Новый Структура;
      КонецПроцедуры
      Процедура Читать()
        Результат = Общая;
      КонецПроцедуры
      """;
    var document = TestUtils.getDocumentContext(source, context);
    var line = source.substring(0, source.indexOf("Результат =")).lines().count() - 1;
    assertThat(typeService.expressionTypesAt(document, new Position((int) line, "  Результат = ".length())).refs())
      .extracting(TypeRef::qualifiedName)
      .containsExactlyInAnyOrder("Неопределено", "Число", "Строка", "Булево", "Массив", "Структура");
  }
}
