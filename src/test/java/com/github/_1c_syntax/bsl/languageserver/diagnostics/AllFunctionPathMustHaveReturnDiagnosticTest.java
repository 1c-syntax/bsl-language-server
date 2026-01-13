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
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class AllFunctionPathMustHaveReturnDiagnosticTest extends AbstractDiagnosticTest<AllFunctionPathMustHaveReturnDiagnostic> {
  AllFunctionPathMustHaveReturnDiagnosticTest() {
    super(AllFunctionPathMustHaveReturnDiagnostic.class);
  }

  @Test
  void testDefaultBehavior() {

    var config = diagnosticInstance.getInfo().getDefaultConfiguration();
    diagnosticInstance.configure(config);
    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics, true)
      .hasRange(0, 8, 0, 27)
      .hasRange(25, 8, 25, 19)
    ;

  }

  @Test
  void testLoopsCanBeNotVisited() {

    var config = diagnosticInstance.getInfo().getDefaultConfiguration();
    config.put("loopsExecutedAtLeastOnce", false);
    diagnosticInstance.configure(config);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(3);
    assertThat(diagnostics, true)
      .hasRange(0, 8, 0, 27)
      .hasRange(25, 8, 25, 19)
      .hasRange(36, 8, 36, 23)
    ;
  }

  @Test
  void testElsIfClausesWithoutElse() {

    var config = diagnosticInstance.getInfo().getDefaultConfiguration();
    config.put("ignoreMissingElseOnExit", true);
    diagnosticInstance.configure(config);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(25, 8, 25, 19)
    ;
  }

  @Test
  void testEmptyIfBodies() {
    var sample = """
      Функция Тест()
        Список = Новый СписокЗначений;
        #Если Сервер Или ТолстыйКлиентОбычноеПриложение Или ВнешнееСоединение Тогда
            Если Условие Тогда
            Иначе
            КонецЕсли;
        #КонецЕсли
        Возврат Список;
      КонецФункции""";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();

  }

  @Test
  void testExitByRaiseException() {
    var sample =
      """
        Функция Тест()
          #Если Не ВебКлиент Тогда
            Массив = Новый Массив;
            Если Условие Тогда
                Возврат Массив;
            КонецЕсли;
            Возврат ПустойМассив;
          #Иначе
            ВызватьИсключение "Упс";
          #КонецЕсли
        КонецФункции""";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();

  }

  @Test
  void testPreprocessorWithMissingReturn() {
    // Positive test: function should be flagged for missing return when preprocessor is used
    var sample = """
      Функция ТестСПрепроцессором()
          #Если Сервер Тогда
              Если Условие Тогда
                  Возврат 1;
              КонецЕсли;
          #КонецЕсли
          // Missing return here - should be detected
      КонецФункции""";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(0, 8, 0, 27);

  }

  @Test
  void testPreprocessorInElsifWithMissingReturn() {
    // Positive test: missing return when inner if without else is inside preprocessor
    var sample = """
      Функция ТестПрепроцессорВElsif()
          #Если Сервер Тогда
              Если Условие1 Тогда
                  Возврат 1;
              ИначеЕсли Условие2 Тогда
                  Возврат 2;
              КонецЕсли;
          #КонецЕсли
          // Missing return at function end
      КонецФункции""";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(0, 8, 0, 30);

  }

  @Test
  void testPreprocessorWithAllPathsHaveReturn() {
    // Negative test: should not crash and should not report any issues
    var sample = """
      Функция ТестВсеПутиСВозвратом()
          Если Условие1 Тогда
              #Если Сервер Тогда
                  Если Условие2 Тогда
                      Возврат 1;
                  ИначеЕсли Условие3 Тогда
                      Возврат 2;
                  Иначе
                      Возврат 3;
                  КонецЕсли;
              #Иначе
                  Возврат 4;
              #КонецЕсли
          Иначе
              Возврат 5;
          КонецЕсли;
      КонецФункции""";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    // Should not throw ClassCastException and should have no diagnostics
    assertThat(diagnostics).isEmpty();

  }

  @Test
  void testPreprocessorInsideIfBlockWithMissingReturn() {
    // Positive test: preprocessor inside if block, missing return should be detected
    // The outer if-elsif chain is missing else, causing missing return
    var sample = """
      Функция ПрепроцессорВнутриIfБлока()
          Если Условие1 Тогда
              #Если Сервер Тогда
                  Возврат 1;
              #Иначе
                  Возврат 2;
              #КонецЕсли
          ИначеЕсли Условие2 Тогда
              Возврат 3;
          КонецЕсли;
          // Missing return here - no else branch for outer if
      КонецФункции""";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(0, 8, 0, 33);

  }

  @Test
  void testNestedPreprocessorNoCrash() {
    // Negative test: should not crash when processing nested preprocessor with if-statements
    var sample = """
      Функция ВложенныйПрепроцессор()
          #Если Сервер Тогда
              Если Условие1 Тогда
                  #Если НЕ ВебКлиент Тогда
                      Если Условие2 Тогда
                          Возврат 1;
                      Иначе
                          Возврат 2;
                      КонецЕсли;
                  #Иначе
                      Возврат 3;
                  #КонецЕсли
              Иначе
                  Возврат 4;
              КонецЕсли;
          #Иначе
              Возврат 5;
          #КонецЕсли
      КонецФункции""";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    // Should not throw ClassCastException
    assertThat(diagnostics).isEmpty();

  }
}
