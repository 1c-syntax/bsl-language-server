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
package com.github._1c_syntax.bsl.languageserver.context.computer;

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.getDocumentContext;
import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.getDocumentContextFromFile;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DiagnosticIgnoranceComputerTest {

  @Test
  void testDiagnosticIgnorance() {

    // given
    String filePath = "./src/test/resources/context/computer/DiagnosticIgnoranceComputerTest.bsl";
    final var documentContext = getDocumentContextFromFile(filePath);

    List<Diagnostic> ignoredDiagnostics = new ArrayList<>();

    ignoredDiagnostics.add(createDiagnostic("SpaceAtStartComment", 4));
    ignoredDiagnostics.add(createDiagnostic("SemicolonPresence", 5));
    ignoredDiagnostics.add(createDiagnostic("RandomDiagnostic", 6));
    ignoredDiagnostics.add(createDiagnostic("SemicolonPresence", 13));
    ignoredDiagnostics.add(createDiagnostic("SpaceAtStartComment", 20));
    ignoredDiagnostics.add(createDiagnostic("SemicolonPresence", 21));
    ignoredDiagnostics.add(createDiagnostic("SemicolonPresence", 27));

    List<Diagnostic> notIgnoredDiagnostics = new ArrayList<>();
    notIgnoredDiagnostics.add(createDiagnostic("SpaceAtStartComment", 12));
    notIgnoredDiagnostics.add(createDiagnostic("RandomDiagnostic", 20));
    notIgnoredDiagnostics.add(createDiagnostic("SemicolonPresence", 29));

    // when
    Computer<DiagnosticIgnoranceComputer.Data> diagnosticIgnoranceComputer =
      new DiagnosticIgnoranceComputer(documentContext);
    DiagnosticIgnoranceComputer.Data data = diagnosticIgnoranceComputer.compute();

    // then
    assertThat(ignoredDiagnostics).allMatch(data::diagnosticShouldBeIgnored);
    assertThat(notIgnoredDiagnostics).noneMatch(data::diagnosticShouldBeIgnored);
  }

  @Test
  void testDiagnosticIgnoranceExtension() {

    // given
    String filePath = "./src/test/resources/context/computer/DiagnosticIgnoranceComputerExtensionTest.bsl";
    final var documentContext = getDocumentContextFromFile(filePath);

    List<Diagnostic> ignoredDiagnostics = new ArrayList<>();
    ignoredDiagnostics.add(createDiagnostic("CanonicalSpellingKeywords", 3));
    ignoredDiagnostics.add(createDiagnostic("MissingSpace", 6));
    ignoredDiagnostics.add(createDiagnostic("CanonicalSpellingKeywords", 13));
    ignoredDiagnostics.add(createDiagnostic("CanonicalSpellingKeywords", 14));

    List<Diagnostic> notIgnoredDiagnostics = new ArrayList<>();
    notIgnoredDiagnostics.add(createDiagnostic("RandomDiagnostic", 1));
    notIgnoredDiagnostics.add(createDiagnostic("CanonicalSpellingKeywords", 2));
    notIgnoredDiagnostics.add(createDiagnostic("MissingSpace", 16));
    notIgnoredDiagnostics.add(createDiagnostic("UnusedLocalVariable", 16));
    notIgnoredDiagnostics.add(createDiagnostic("SemicolonPresence", 10));

    // when
    Computer<DiagnosticIgnoranceComputer.Data> diagnosticIgnoranceComputer =
      new DiagnosticIgnoranceComputer(documentContext);
    DiagnosticIgnoranceComputer.Data data = diagnosticIgnoranceComputer.compute();

    // then
    assertThat(ignoredDiagnostics).allMatch(data::diagnosticShouldBeIgnored);
    assertThat(notIgnoredDiagnostics).noneMatch(data::diagnosticShouldBeIgnored);

  }

  @Test
  void testDiagnosticIgnoranceExtensionAnnotations() {
    var documentContext = getDocumentContext("""
      // Аннотации расширения не относятся к исходному методу.
      &НаКлиенте
      &ИзменениеИКонтроль("Метод")
      &После("Метод")
      Процедура Расш_Метод()
        Сообщить("Исходный код");
      КонецПроцедуры
      """);
    var data = new DiagnosticIgnoranceComputer(documentContext).compute();

    assertThat(List.of(1, 2, 3, 4))
      .noneMatch(line -> data.diagnosticShouldBeIgnored(createDiagnostic("CanonicalSpellingKeywords", line)));
    assertThat(List.of(5, 6, 7))
      .allMatch(line -> data.diagnosticShouldBeIgnored(createDiagnostic("CanonicalSpellingKeywords", line)));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "&ИзменениеИКонтроль(\"Метод\")\nПроцедура Расш_Метод() КонецПроцедуры",
    "&ChangeAndValidate(\"Method\")\nFunction Ext_Method() Return 1; EndFunction"
  })
  void testDiagnosticIgnoranceSingleLineExtension(String method) {
    var documentContext = getDocumentContext("""
      Процедура ОбычныйМетод()
      КонецПроцедуры
      &НаКлиенте
      %s
      // После расширения
      Процедура СледующийМетод() КонецПроцедуры
      """.formatted(method));
    var data = new DiagnosticIgnoranceComputer(documentContext).compute();

    assertThat(data.diagnosticShouldBeIgnored(createDiagnostic("RandomDiagnostic", 5))).isTrue();
    assertThat(List.of(1, 2, 3, 4, 6, 7))
      .noneMatch(line -> data.diagnosticShouldBeIgnored(createDiagnostic("RandomDiagnostic", line)));
  }

  @Test
  void testDiagnosticIgnoranceMixedExtensionMethods() {
    var documentContext = getDocumentContext("""
      &ИзменениеИКонтроль("Первый")
      Процедура Расш_Первый()
      КонецПроцедуры
      // Между методами
      &ИзменениеИКонтроль("Второй")
      Процедура Расш_Второй() КонецПроцедуры
      &ИзменениеИКонтроль("Третий")
      Процедура Расш_Третий()
      #Вставка
        Сообщить("Добавленный код");
      #КонецВставки
        Сообщить("Исходный код");
      КонецПроцедуры
      Процедура ОбычныйМетод() КонецПроцедуры
      """);
    var data = new DiagnosticIgnoranceComputer(documentContext).compute();

    assertThat(List.of(2, 3, 6, 8, 9, 11, 12, 13))
      .allMatch(line -> data.diagnosticShouldBeIgnored(createDiagnostic("RandomDiagnostic", line)));
    assertThat(List.of(1, 4, 5, 7, 10, 14))
      .noneMatch(line -> data.diagnosticShouldBeIgnored(createDiagnostic("RandomDiagnostic", line)));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "ПРОЦЕДУРА\nРасш_Метод()\nКОНЕЦПРОЦЕДУРЫ",
    "ФУНКЦИЯ\nРасш_Метод()\nКОНЕЦФУНКЦИИ",
    "АСИНХ\nПРОЦЕДУРА\nРасш_Метод()\nКОНЕЦПРОЦЕДУРЫ",
    "АСИНХ\nФУНКЦИЯ\nРасш_Метод()\nКОНЕЦФУНКЦИИ",
    "PROCEDURE\nExt_Method()\nENDPROCEDURE",
    "FUNCTION\nExt_Method()\nENDFUNCTION",
    "ASYNC\nPROCEDURE\nExt_Method()\nENDPROCEDURE",
    "ASYNC\nFUNCTION\nExt_Method()\nENDFUNCTION",
    "АСИНХ\n\n// Описание\nПРОЦЕДУРА\n\nРасш_Метод()\nКОНЕЦПРОЦЕДУРЫ"
  })
  void testDiagnosticIgnoranceWrappedExtensionDeclaration(String method) {
    var documentContext = getDocumentContext("""
      &НаКлиенте
      &ИзменениеИКонтроль("Метод")
      %s
      // После метода
      """.formatted(method));
    var data = new DiagnosticIgnoranceComputer(documentContext).compute();
    int lastMethodLine = (int) method.lines().count() + 2;

    assertThat(documentContext.getSymbolTree().getMethods()).hasSize(1);
    assertThat(IntStream.rangeClosed(3, lastMethodLine).boxed().toList())
      .allMatch(line -> data.diagnosticShouldBeIgnored(createDiagnostic("RandomDiagnostic", line)));
    assertThat(List.of(1, 2, lastMethodLine + 1))
      .noneMatch(line -> data.diagnosticShouldBeIgnored(createDiagnostic("RandomDiagnostic", line)));
  }

  @Test
  void testInsertDirectivesOutsideExtensionDoNotSuppressDiagnostics() {
    var documentContext = getDocumentContext("""
      Процедура ОбычныйМетод()
      #Вставка
        Сообщить("Обычный код");
      #КонецВставки
      КонецПроцедуры
      &ИзменениеИКонтроль("Первый")
      Процедура Расш_Первый()
      КонецПроцедуры
      Процедура ДругойМетод()
      #Вставка
        Сообщить("Другой код");
      #КонецВставки
      КонецПроцедуры
      &ИзменениеИКонтроль("Второй")
      Процедура Расш_Второй()
      КонецПроцедуры
      Процедура ПоследнийМетод()
      #Вставка
        Сообщить("Последний код");
      #КонецВставки
      КонецПроцедуры
      """);
    var data = new DiagnosticIgnoranceComputer(documentContext).compute();

    assertThat(List.of(7, 8, 15, 16))
      .allMatch(line -> data.diagnosticShouldBeIgnored(createDiagnostic("RandomDiagnostic", line)));
    assertThat(List.of(1, 2, 3, 4, 5, 6, 9, 10, 11, 12, 13, 14, 17, 18, 19, 20, 21))
      .noneMatch(line -> data.diagnosticShouldBeIgnored(createDiagnostic("RandomDiagnostic", line)));
  }

  @Test
  void testUnbalancedInsertDirectivesStayWithinExtensionMethod() {
    var documentContext = getDocumentContext("""
      &ИзменениеИКонтроль("Первый")
      Процедура Расш_Первый()
      #Вставка
        Сообщить("Добавленный код");
      #Вставка
        Сообщить("Добавленный код");
      #КонецВставки
        Сообщить("Исходный код");
      #КонецВставки
        Сообщить("Исходный код");
      КонецПроцедуры
      &ИзменениеИКонтроль("Второй")
      Процедура Расш_Второй()
      #Вставка
        Сообщить("Незавершённая вставка");
      КонецПроцедуры
      &ИзменениеИКонтроль("Третий")
      Процедура Расш_Третий()
      КонецПроцедуры
      """);
    var data = new DiagnosticIgnoranceComputer(documentContext).compute();

    assertThat(List.of(2, 3, 7, 8, 9, 10, 11, 13, 14, 18, 19))
      .allMatch(line -> data.diagnosticShouldBeIgnored(createDiagnostic("RandomDiagnostic", line)));
    assertThat(List.of(1, 4, 5, 6, 12, 15, 16, 17))
      .noneMatch(line -> data.diagnosticShouldBeIgnored(createDiagnostic("RandomDiagnostic", line)));
  }

  @Test
  void testMultipleInsertBlocksLeaveOnlyInsertedCodeChecked() {
    var documentContext = getDocumentContext("""
      &ИзменениеИКонтроль("Метод")
      Процедура Расш_Метод()
        Сообщить("Исходный код");
      #Вставка
        Сообщить("Первая вставка");
      #КонецВставки
        Сообщить("Исходный код");
      #Вставка
        Сообщить("Вторая вставка");
      #КонецВставки
        Сообщить("Исходный код");
      КонецПроцедуры
      """);
    var data = new DiagnosticIgnoranceComputer(documentContext).compute();

    assertThat(List.of(2, 3, 4, 6, 7, 8, 10, 11, 12))
      .allMatch(line -> data.diagnosticShouldBeIgnored(createDiagnostic("RandomDiagnostic", line)));
    assertThat(List.of(1, 5, 9))
      .noneMatch(line -> data.diagnosticShouldBeIgnored(createDiagnostic("RandomDiagnostic", line)));
  }

  @Test
  void testDiagnosticIgnoranceLastLine() {

    // given
    String filePath = "./src/test/resources/context/computer/DiagnosticIgnoranceComputerLastLineTest.bsl";
    final var documentContext = getDocumentContextFromFile(filePath);

    List<Diagnostic> ignoredDiagnostics = new ArrayList<>();
    ignoredDiagnostics.add(createDiagnostic("SpaceAtStartComment", 2));
    ignoredDiagnostics.add(createDiagnostic("SpaceAtStartComment", 8));

    // when
    Computer<DiagnosticIgnoranceComputer.Data> diagnosticIgnoranceComputer =
      new DiagnosticIgnoranceComputer(documentContext);
    DiagnosticIgnoranceComputer.Data data = diagnosticIgnoranceComputer.compute();

    // then
    assertThat(ignoredDiagnostics).allMatch(data::diagnosticShouldBeIgnored);

  }

  @Test
  void testDiagnosticIgnoranceIssue() {

    // given
    String filePath = "./src/test/resources/context/computer/DiagnosticIgnoranceComputerIssueTest.bsl";
    final var documentContext = getDocumentContextFromFile(filePath);

    List<Diagnostic> ignoredDiagnostics = new ArrayList<>();
    ignoredDiagnostics.add(createDiagnostic("SpaceAtStartComment", 2));
    ignoredDiagnostics.add(createDiagnostic("SpaceAtStartComment", 18));

    // when
    Computer<DiagnosticIgnoranceComputer.Data> diagnosticIgnoranceComputer =
      new DiagnosticIgnoranceComputer(documentContext);
    DiagnosticIgnoranceComputer.Data data = diagnosticIgnoranceComputer.compute();

    // then
    assertThat(ignoredDiagnostics).allMatch(data::diagnosticShouldBeIgnored);

  }

  @Test
  void testDiagnosticIgnoranceOnlyComments() {

    // given
    String filePath = "./src/test/resources/context/computer/DiagnosticIgnoranceComputerOnlyCommentsTest.bsl";
    final var documentContext = getDocumentContextFromFile(filePath);

    List<Diagnostic> ignoredDiagnostics = new ArrayList<>();
    ignoredDiagnostics.add(createDiagnostic("SpaceAtStartComment", 2));
    ignoredDiagnostics.add(createDiagnostic("SpaceAtStartComment", 3));

    // when
    Computer<DiagnosticIgnoranceComputer.Data> diagnosticIgnoranceComputer =
      new DiagnosticIgnoranceComputer(documentContext);
    DiagnosticIgnoranceComputer.Data data = diagnosticIgnoranceComputer.compute();

    // then
    assertThat(ignoredDiagnostics).allMatch(data::diagnosticShouldBeIgnored);

  }

  private static Diagnostic createDiagnostic(String code, int line) {
    Diagnostic diagnostic = new Diagnostic();
    diagnostic.setCode(code);
    diagnostic.setRange(Ranges.create(line - 1, 0, line - 1, 0));

    return diagnostic;
  }
}
