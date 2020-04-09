/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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

import com.ginsberg.junit.exit.ExpectSystemExitWithStatus;
import com.github._1c_syntax.bsl.languageserver.BSLLSPLauncher;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class SmokyTest {

  @Test
  @ExpectSystemExitWithStatus(0)
  void test() {

    String[] args = new String[]{"--analyze", "--srcDir", "./src/test/resources/diagnostics"};

    BSLLSPLauncher.main(args);

    assertThat(true).isTrue(); // TODO что проверять?
  }

  @Test
  void testIdenticalRanges() {

    var configuration = LanguageServerConfiguration.create();
    var diagnosticSupplier = new DiagnosticSupplier(configuration);
    var srcDir = "./src/test/resources/";
    List<Diagnostic> diagnostics = new ArrayList<>();
    FileUtils.listFiles(Paths.get(srcDir).toAbsolutePath().toFile(), new String[]{"bsl", "os"}, true)
      .forEach(filePath -> {
        var documentContext = TestUtils.getDocumentContextFromFile(filePath.toString());
        var diagnosticProvider = new DiagnosticProvider(diagnosticSupplier);
        diagnosticProvider.computeDiagnostics(documentContext).stream()
          .filter(diagnostic ->
            (diagnostic.getRange() != null
              && diagnostic.getRange().getEnd().equals(diagnostic.getRange().getStart()))
              || (diagnostic.getRelatedInformation() != null
              && diagnostic.getRelatedInformation().stream()
              .anyMatch(relation -> relation.getLocation() != null
                && relation.getLocation().getRange() != null
                && relation.getLocation().getRange().getEnd().equals(relation.getLocation().getRange().getStart())))
          )
          .collect(Collectors.toCollection(() -> diagnostics));
      });

    assertThat(diagnostics).isEmpty();
  }

  @SneakyThrows
  @Test
  void testIAllDiagnostics() {

    // прочитаем все файлы ресурсов
    var srcDir = "./src/test/resources/";
    var fixtures = FileUtils.listFiles(new File(srcDir), new String[]{"bsl", "os"}, true);

    // получим все возможные коды диагностик и положим в мапу "включенным"
    Map<String, Either<Boolean, Map<String, Object>>> diagnostics = DiagnosticSupplier.getDiagnosticClasses().stream()
      .map(diagnosticClass -> (new DiagnosticInfo(diagnosticClass).getCode()))
      .collect(Collectors.toMap(
        diagnosticCode -> diagnosticCode.getStringValue(),
        diagnosticCode -> Either.forLeft(true),
        (a, b) -> b));

    // создадим новый конфиг, в котором включим все диагностики
    var configuration = LanguageServerConfiguration.create();
    configuration.getDiagnosticsOptions().setRules(diagnostics);
    var diagnosticSupplier = new DiagnosticSupplier(configuration);

    // для каждой фикстуры расчитаем диагностики
    // если упадет, запомним файл и текст ошибки
    Map<File, Exception> diagnosticErrors = new HashMap<>();
    var provider = new DiagnosticProvider(diagnosticSupplier);
    fixtures.forEach(filePath -> {
      try {
        provider.computeDiagnostics(TestUtils.getDocumentContextFromFile(filePath.toString()));
      } catch (Exception e) {
        diagnosticErrors.put(filePath, e);
      }
    });

    assertThat(diagnosticErrors).isEmpty();
  }

}
