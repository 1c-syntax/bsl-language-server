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

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.diagnostics.infrastructure.DiagnosticInfosFactory;
import com.github._1c_syntax.bsl.languageserver.diagnostics.infrastructure.DiagnosticObjectProvider;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(properties = "sentry.enabled=false")
class PreprocessorTypingDiagnosticTest extends AbstractServerContextAwareTest {
  @Autowired
  private DiagnosticInfosFactory diagnosticInfosFactory;
  @Autowired
  private DiagnosticObjectProvider diagnosticObjectProvider;

  @ParameterizedTest
  @MethodSource("unfinishedMethods")
  void allDiagnosticsTolerateUnfinishedPreprocessor(FileType fileType, String source) {
    var root = Path.of("src/test/resources/metadata/designer");
    initServerContext(root);
    var uri = fileType == FileType.BSL
      ? root.resolve("Catalogs/Справочник1/Forms/ФормаСписка/Ext/Form/Module.bsl").toUri()
      : URI.create("file:///preprocessor-typing.os");
    var document = TestUtils.getDocumentContext(uri, source, context);

    assertAll(diagnosticInfosFactory.getDiagnosticClasses().stream().map(diagnosticClass -> () -> {
      try {
        diagnosticObjectProvider.get(diagnosticClass).getDiagnostics(document);
      } catch (RuntimeException exception) {
        throw new AssertionError(diagnosticClass.getSimpleName(), exception);
      }
    }));
  }

  @Test
  void diagnosticsResumeAfterCompletingTheDirective() {
    initServerContext();
    var document = TestUtils.getDocumentContext("Процедура Пример(Параметр)\n#\nКонецПроцедуры", context);
    var methodSize = diagnosticObjectProvider.get(MethodSizeDiagnostic.class);
    methodSize.configure(Map.of("maxMethodSize", 1));
    var diagnostics = List.of(methodSize,
      diagnosticObjectProvider.get(UnreachableCodeDiagnostic.class),
      diagnosticObjectProvider.get(CodeAfterAsyncCallDiagnostic.class),
      diagnosticObjectProvider.get(UnusedParametersDiagnostic.class));

    diagnostics.forEach(diagnostic -> assertThat(diagnostic.getDiagnostics(document)).isEmpty());

    context.rebuildDocument(document, """
      Процедура Пример(Параметр)
      #Если Windows Тогда
        ПоказатьВопрос();
        Сообщить(1);
        Возврат;
        Сообщить(2);
      #КонецЕсли
      КонецПроцедуры
      """, 1);

    diagnostics.forEach(diagnostic -> assertThat(diagnostic.getDiagnostics(document))
      .as(diagnostic.getClass().getSimpleName()).hasSize(1));
  }

  private static Stream<Arguments> unfinishedMethods() {
    var instructions = List.of("#", "#Е", "#Если", "#Если ", "#Если Сервер", "#Если Сервер И",
      "#Если Сервер Тогда", "#If", "#If Server", "#If Server Then",
      "#Если Сервер Тогда\nА = 1;\n#И", "#Если Сервер Тогда\nА = 1;\n#ИначеЕсли",
      "#Если Сервер Тогда\nА = 1;\n#Конец", "#Если Сервер Тогда\nА = 1;\n#КонецЕсли");
    return Stream.of(FileType.BSL, FileType.OS).flatMap(fileType ->
      instructions.stream().flatMap(instruction -> Stream.of(
        Arguments.of(fileType, "Процедура Пример(Параметр)\n" + instruction + "\nКонецПроцедуры"),
        Arguments.of(fileType, "Function Example(Parameter)\n" + instruction + "\nReturn 1;\nEndFunction")
      )));
  }
}
