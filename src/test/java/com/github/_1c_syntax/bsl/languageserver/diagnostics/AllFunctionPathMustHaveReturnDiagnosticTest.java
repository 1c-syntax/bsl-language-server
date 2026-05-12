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

    assertThat(diagnostics).hasSize(5);
    assertThat(diagnostics, true)
      .hasRange(0, 8, 0, 27)
      .hasRange(25, 8, 25, 19)
      .hasRange(93, 8, 93, 27) // ТестСПрепроцессором
      .hasRange(102, 8, 102, 30) // ТестПрепроцессорВElsif
      .hasRange(131, 8, 131, 33) // ПрепроцессорВнутриIfБлока
    ;

  }

  @Test
  void testLoopsCanBeNotVisited() {

    var config = diagnosticInstance.getInfo().getDefaultConfiguration();
    config.put("loopsExecutedAtLeastOnce", false);
    diagnosticInstance.configure(config);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(6);
    assertThat(diagnostics, true)
      .hasRange(0, 8, 0, 27)
      .hasRange(25, 8, 25, 19)
      .hasRange(36, 8, 36, 23)
      .hasRange(93, 8, 93, 27) // ТестСПрепроцессором
      .hasRange(102, 8, 102, 30) // ТестПрепроцессорВElsif
      .hasRange(131, 8, 131, 33) // ПрепроцессорВнутриIfБлока
    ;
  }

  @Test
  void testElsIfClausesWithoutElse() {

    var config = diagnosticInstance.getInfo().getDefaultConfiguration();
    config.put("ignoreMissingElseOnExit", true);
    diagnosticInstance.configure(config);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(3);
    assertThat(diagnostics, true)
      .hasRange(25, 8, 25, 19)
      .hasRange(93, 8, 93, 27) // ТестСПрепроцессором
      .hasRange(102, 8, 102, 30) // ТестПрепроцессорВElsif
      // ПрепроцессорВнутриIfБлока is suppressed by ignoreMissingElseOnExit
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
  void testPreprocessorWrappingElsifBranches_NoError() {
    // All paths have returns (via return + raise) — no diagnostic expected.
    // Previously caused Diagnostic computation error (NoSuchElementException).
    var sample =
      """
        Функция МенеджерОбъектаПоИмени(Имя)
          Перем КлассОМ, Менеджер;
          ЧастиИмени = СтрРазделить(Имя, ".");
          Если ЧастиИмени.Количество() > 0 Тогда
            КлассОМ = ВРег(ЧастиИмени[0]);
          КонецЕсли;
          Если      КлассОМ = "ПЛАНОБМЕНА" Тогда
            Менеджер = ПланыОбмена;
          ИначеЕсли КлассОМ = "СПРАВОЧНИК" Тогда
            Менеджер = Справочники;
          ИначеЕсли КлассОМ = "ДОКУМЕНТ" Тогда
            Менеджер = Документы;
          #Если НЕ МобильноеПриложениеСервер Тогда
          ИначеЕсли КлассОМ = "ОТЧЕТ" Тогда
            Менеджер = Отчеты;
          ИначеЕсли КлассОМ = "ОБРАБОТКА" Тогда
            Менеджер = Обработки;
          #КонецЕсли
          КонецЕсли;
          Если Менеджер <> Неопределено Тогда
            Возврат Менеджер;
          КонецЕсли;
          ВызватьИсключение "Ошибка";
        КонецФункции""";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testPreprocessorWrappingElsifBranches_MissingReturnOutsidePreprocessor() {
    // Missing return OUTSIDE the preprocessor block — diagnostic expected.
    // The function does something after the if-chain but has no return/raise at the end.
    var sample =
      """
        Функция МенеджерОбъектаПоИмени(Имя)
          Перем КлассОМ, Менеджер;
          Если      КлассОМ = "ПЛАНОБМЕНА" Тогда
            Менеджер = ПланыОбмена;
          ИначеЕсли КлассОМ = "СПРАВОЧНИК" Тогда
            Менеджер = Справочники;
          #Если НЕ МобильноеПриложениеСервер Тогда
          ИначеЕсли КлассОМ = "ОТЧЕТ" Тогда
            Менеджер = Отчеты;
          #КонецЕсли
          КонецЕсли;
          Если Менеджер <> Неопределено Тогда
            Возврат Менеджер;
          КонецЕсли;
          Сообщить("Не найден менеджер");
          // Missing return/raise at end — function exits without value
        КонецФункции""";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(0, 8, 0, 30);
  }

  @Test
  void testPreprocessorWrappingElsifBranches_MissingReturnInBranchOutsidePreprocessor() {
    // One branch OUTSIDE the preprocessor block has no return — diagnostic expected.
    var sample =
      """
        Функция Тест()
          Если Условие1 Тогда
            Возврат 1;
          ИначеЕсли Условие2 Тогда
            // no return here
            А = 1;
          #Если Сервер Тогда
          ИначеЕсли Условие3 Тогда
            Возврат 3;
          #КонецЕсли
          Иначе
            Возврат 4;
          КонецЕсли;
        КонецФункции""";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(0, 8, 0, 12);
  }

  @Test
  void testPreprocessorWrappingElsifBranches_AllBranchesReturn() {
    // All branches (including those wrapped by preprocessor) have returns — no diagnostic.
    var sample =
      """
        Функция Тест()
          Если Условие1 Тогда
            Возврат 1;
          ИначеЕсли Условие2 Тогда
            Возврат 2;
          #Если Сервер Тогда
          ИначеЕсли Условие3 Тогда
            Возврат 3;
          ИначеЕсли Условие4 Тогда
            Возврат 4;
          #КонецЕсли
          Иначе
            Возврат 5;
          КонецЕсли;
        КонецФункции""";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testPreprocessorWrappingElsifBranches_MissingReturnInsidePreprocessor() {
    // An elsif branch INSIDE the preprocessor block has no return — diagnostic expected.
    var sample =
      """
        Функция Тест()
          Если Условие1 Тогда
            Возврат 1;
          ИначеЕсли Условие2 Тогда
            Возврат 2;
          #Если Сервер Тогда
          ИначеЕсли Условие3 Тогда
            А = 3;
          ИначеЕсли Условие4 Тогда
            Возврат 4;
          #КонецЕсли
          Иначе
            Возврат 5;
          КонецЕсли;
        КонецФункции""";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(0, 8, 0, 12);
  }

  @Test
  void testPreprocessorWrappingElsifBranches_MultiplePreprocessorBlocks() {
    // Multiple preprocessor blocks wrapping different elsif branches — no crash, and
    // function with return+raise on all paths should produce no diagnostic.
    var sample =
      """
        Функция Тест(Знач Вид)
          Если Вид = "А" Тогда
            Возврат 1;
          #Если НЕ МобильноеПриложениеСервер Тогда
          ИначеЕсли Вид = "Б" Тогда
            Возврат 2;
          #КонецЕсли
          #Если НЕ ВебКлиент Тогда
          ИначеЕсли Вид = "В" Тогда
            Возврат 3;
          #КонецЕсли
          Иначе
            Возврат 0;
          КонецЕсли;
        КонецФункции""";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testNestedPreprocessorDepth2_NoError() {
    // Two nested statement-level preprocessor blocks (#Если inside #Если), all branches return.
    // hasMatchingEndIfInSameCodeBlock must correctly count depth > 1.
    var sample =
      """
        Функция Тест()
          Если Условие1 Тогда
            Возврат 1;
          ИначеЕсли Условие2 Тогда
            Возврат 2;
          #Если Сервер Тогда
          #Если Клиент Тогда
          ИначеЕсли Условие3 Тогда
            Возврат 3;
          #КонецЕсли
          ИначеЕсли Условие4 Тогда
            Возврат 4;
          #КонецЕсли
          Иначе
            Возврат 5;
          КонецЕсли;
        КонецФункции""";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testNestedPreprocessorDepth2_MissingReturnInInnerBranch() {
    // Two nested statement-level preprocessor blocks — missing return in inner branch is caught.
    var sample =
      """
        Функция Тест()
          Если Условие1 Тогда
            Возврат 1;
          ИначеЕсли Условие2 Тогда
            Возврат 2;
          #Если Сервер Тогда
          #Если Клиент Тогда
          ИначеЕсли Условие3 Тогда
            А = 3;
          #КонецЕсли
          ИначеЕсли Условие4 Тогда
            Возврат 4;
          #КонецЕсли
          Иначе
            Возврат 5;
          КонецЕсли;
        КонецФункции""";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(0, 8, 0, 12);
  }

  @Test
  void testNestedCodeBlockWithPreprocessor_NoError() {
    // #Если А wraps an elsif branch; between #Если А and #КонецЕсли А there is a sibling
    // statement whose nested codeBlock contains its own balanced #Если Б/#КонецЕсли Б.
    // Depth tracking must treat the inner pair as neutral (net 0) and correctly find the
    // matching #КонецЕсли for the outer #Если А.
    var sample =
      """
        Функция Тест()
          Если Условие1 Тогда
            Возврат 1;
          ИначеЕсли Условие2 Тогда
            Возврат 2;
          #Если Сервер Тогда
          ИначеЕсли Условие3 Тогда
            Если ВложенноеУсловие Тогда
              #Если Клиент Тогда
              А = 1;
              #КонецЕсли
            КонецЕсли;
            Возврат 3;
          #КонецЕсли
          Иначе
            Возврат 4;
          КонецЕсли;
        КонецФункции""";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testNestedCodeBlockWithPreprocessor_MissingReturn() {
    // Same structure but with a missing return — diagnostic must still be detected.
    var sample =
      """
        Функция Тест()
          Если Условие1 Тогда
            Возврат 1;
          ИначеЕсли Условие2 Тогда
            Возврат 2;
          #Если Сервер Тогда
          ИначеЕсли Условие3 Тогда
            Если ВложенноеУсловие Тогда
              #Если Клиент Тогда
              А = 1;
              #КонецЕсли
            КонецЕсли;
            А = 3;
          #КонецЕсли
          КонецЕсли;
        КонецФункции""";

    var documentContext = TestUtils.getDocumentContext(sample);
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(0, 8, 0, 12);
  }
}
