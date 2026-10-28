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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class UnreachableCodePreprocessorTest extends AbstractDiagnosticTest<UnreachableCodeDiagnostic> {
  UnreachableCodePreprocessorTest() {
    super(UnreachableCodeDiagnostic.class);
  }

  @ParameterizedTest
  @CsvSource({
    "НаКлиенте, Сервер", "НаКлиенте, Сервер И Клиент", "НаСервере, Клиент",
    "НаКлиентеНаСервереБезКонтекста, Сервер И Клиент"
  })
  void methodDirectiveExcludesImpossibleBranches(String directive, String condition) {
    var diagnostics = formDiagnostics("""
      &%s
      Процедура ПодразделениеПриИзменении(Элемент)
          #Если %s Тогда
              // Этот блок не компилируется ни в одном контексте метода.
              А = "Привет";
          #Иначе
              А = 1;
          #КонецЕсли
          Сообщить(А);
      КонецПроцедуры
      """.formatted(directive, condition));

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true).hasRange(3, 8, 4, 21);
    assertThat(diagnostics.getFirst().getTags()).contains(DiagnosticTag.Unnecessary);
  }

  @Test
  void clientBranchExcludesElseAndItsNestedCode() {
    var diagnostics = formDiagnostics("""
      &НаКлиенте
      Процедура Пример()
          #Если Клиент Тогда
              А = 1;
          #Иначе
              #Если Windows Тогда
                  А = "Привет";
              #КонецЕсли
          #КонецЕсли
      КонецПроцедуры
      """);

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true).hasRange(5, 8, 7, 18);
  }

  private List<Diagnostic> formDiagnostics(String source) {
    var root = Path.of("src/test/resources/metadata/commonModuleCalls");
    initServerContext(root);
    return getDiagnostics(TestUtils.getDocumentContext(
      root.resolve("CommonForms/Тест/Ext/Form/Module.bsl").toUri(), source, context));
  }

  @ParameterizedTest
  @CsvSource({"RU, BSL, Server", "EN, BSL, Server", "RU, OS, Windows", "EN, OS, Windows"})
  void englishPreprocessorUsesTheSelectedDiagnosticLanguage(Language language, FileType fileType, String symbol) {
    configuration.setLanguage(language);
    diagnosticInstance.getInfo().refresh();
    var diagnostics = diagnostics("#If " + symbol + " And Not " + symbol + " Then\nDead();\n#EndIf", fileType);

    assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
      assertThat(diagnostic.getMessage().getLeft()).isEqualTo(language == Language.EN
        ? "Correct the algorithm because this code will never be executed"
        : "Исправьте алгоритм, т.к. этот код никогда не будет исполнен");
      assertThat(diagnostic.getTags()).contains(DiagnosticTag.Unnecessary);
    });
  }

  @Test
  void contradictoryBranchIsOneUnusedRange() {
    var diagnostics = diagnostics("""
      Процедура Пример()
          #Если Сервер И НЕ Сервер Тогда
              Первый();
              Второй();
          #КонецЕсли
      КонецПроцедуры
      """, FileType.BSL);

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true).hasRange(2, 8, 3, 17);
    assertThat(diagnostics.getFirst().getTags()).contains(DiagnosticTag.Unnecessary);
  }

@Test
  void excludesWholeMethodsAndMergesNestedMessages() {
    var diagnostics = diagnostics("""
      #Если Сервер И НЕ Сервер Тогда
      // Недостижимый комментарий
      #Область Блок
      Процедура Пример()
          Возврат;
          Первый();
          #Если Клиент Тогда
              Второй();
          #КонецЕсли
      КонецПроцедуры
      #КонецОбласти
      #КонецЕсли
      """, FileType.BSL);

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true).hasRange(1, 0, 10, 13);
    assertThat(diagnostics.getFirst().getTags()).contains(DiagnosticTag.Unnecessary);
  }

  @Test
  void findsImpossibleAlternativesInOneScript() {
    var diagnostics = diagnostics("""
      #Если Windows Тогда
          Первый();
      #ИначеЕсли Windows Тогда
          Второй();
      #ИначеЕсли НЕ Windows Тогда
          Третий();
      #Иначе
          Четвертый();
      #КонецЕсли
      """, FileType.OS);

    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics, true).hasRange(3, 4, 13).hasRange(7, 4, 16);
    assertThat(diagnostics).allSatisfy(diagnostic -> assertThat(diagnostic.getTags())
      .contains(DiagnosticTag.Unnecessary));
  }

  @ParameterizedTest
  @EnumSource(FileType.class)
  void unknownOperatingSystemKeepsBothBranches(FileType fileType) {
    assertThat(diagnostics("""
      #Если Windows Тогда
          Первый();
      #Иначе
          Второй();
      #КонецЕсли
      """, fileType)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void combinedContextRequiresFileModeSupport(boolean enabled) {
    assertThat(diagnostics("""
      #Если Сервер Тогда
          Первый();
      #КонецЕсли
      #Если Сервер И Клиент Тогда
          Второй();
      #КонецЕсли
      """, FileType.BSL, enabled)).hasSize(enabled ? 0 : 1);
  }

  @Test
  void emptyAndCommentOnlyBranchesAreNotReported() {
    assertThat(diagnostics("""
      #Если Сервер И НЕ Сервер Тогда
      #КонецЕсли
      #Если Сервер И НЕ Сервер Тогда
          // Только комментарий
          #Область Пустая
          #КонецОбласти
      #КонецЕсли
      """, FileType.BSL)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "#Если Windows И Тогда\nПервый();\n#КонецЕсли",
    "#Если Сервер И НЕ Сервер Тогда\nПервый();",
    "#Иначе\nПервый();\n#КонецЕсли",
    "#Если Windows Тогда\nПервый();\n#Иначе\nВторой();\n#Иначе\nТретий();\n#КонецЕсли"
  })
  void incompletePreprocessorDoesNotInventUnreachableCode(String source) {
    assertThat(diagnostics(source, FileType.OS)).isEmpty();
  }

  @Test
  void clearsPreprocessorRangesBetweenDocuments() {
    assertThat(diagnostics("#Если Сервер И НЕ Сервер Тогда\nПервый();\n#КонецЕсли", FileType.BSL))
      .hasSize(1);
    assertThat(diagnostics("Первый();", FileType.BSL)).isEmpty();
  }

  private List<Diagnostic> diagnostics(String source, FileType fileType) {
    return diagnostics(source, fileType, false);
  }

  private List<Diagnostic> diagnostics(String source, FileType fileType, boolean fileMode) {
    initServerContext();
    context.getLanguageServerConfiguration().setSupportThickClientFileMode(fileMode);
    var uri = URI.create("file:///unreachable-preprocessor." + (fileType == FileType.BSL ? "bsl" : "os"));
    return getDiagnostics(TestUtils.getDocumentContext(uri, source, context));
  }
}
