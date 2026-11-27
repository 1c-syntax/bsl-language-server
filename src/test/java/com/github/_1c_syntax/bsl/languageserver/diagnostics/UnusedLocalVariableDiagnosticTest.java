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

import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class UnusedLocalVariableDiagnosticTest extends AbstractDiagnosticTest<UnusedLocalVariableDiagnostic> {
  UnusedLocalVariableDiagnosticTest() {
    super(UnusedLocalVariableDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();
    assertThat(diagnostics).hasSize(5);
    assertThat(diagnostics, true)
      .hasRange(1, 6, 36)
      .hasRange(19, 10, 35)
      .hasRange(19, 37, 63)
      .hasRange(24, 4, 28)
      .hasRange(83, 0, 25);

  }

  @Test
  void ignoresDirectEvalResultsInTheirOwnScope() {
    var document = TestUtils.getDocumentContext("""
      Процедура Проверить(Выражение, Объект)
        Перем Явная;
        Проверка = вЫчИсЛиТь(Выражение);
        Явная = ((eVaL(Выражение)));
        Поздняя = 0;
        Поздняя = Вычислить(Выражение);
        Метод = Объект.Вычислить(Выражение);
        Вложенный = Строка(Вычислить(Выражение));
        Операция = Eval(Выражение) + 1;
        Цепочка = Eval(Выражение).Ключ;
        Унарная = -Eval(Выражение);
      КонецПроцедуры

      Процедура ДругаяПроверка()
        Проверка = 1;
      КонецПроцедуры
      """, context);

    var diagnostics = getDiagnostics(document);
    assertThat(diagnostics).hasSize(6);
    assertThat(diagnostics, true)
      .hasRange(6, 2, 7)
      .hasRange(7, 2, 11)
      .hasRange(8, 2, 10)
      .hasRange(9, 2, 9)
      .hasRange(10, 2, 9)
      .hasRange(14, 2, 10);
  }

  @Test
  void checksResultsOfModuleFunctionsNamedEval() {
    var document = TestUtils.getDocumentContext("""
      Процедура Проверить()
        Результат = Eval("1");
      КонецПроцедуры

      Функция Eval(Выражение)
        Возврат Выражение;
      КонецФункции
      """, context);

    var diagnostics = getDiagnostics(document);
    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true).hasRange(1, 2, 11);
  }
}
