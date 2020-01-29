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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ginsberg.junit.exit.ExpectSystemExitWithStatus;
import com.github._1c_syntax.bsl.languageserver.BSLLSPLauncher;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

    var tempDir = Files.createTempDirectory("bslls");
    var tempCfg = Paths.get(tempDir.toAbsolutePath().toString(), "fake-bslls-cong.json").toFile();

    Map<String, Boolean> diagnosticCodes = new HashMap<>();

    // получим все возможные коды диагностик и положим в мапу "выключенным"
    DiagnosticSupplier.getDiagnosticClasses().stream()
      .map(diagnosticClass -> (new DiagnosticInfo(diagnosticClass).getCode()))
      .forEach(diagnosticCode -> diagnosticCodes.put(diagnosticCode, false));

    Map<String, Map<File, Exception>> diagnosticErrors = new HashMap<>();

    var srcDir = "./src/test/resources/";
    var fixtures = FileUtils.listFiles(
      Paths.get(srcDir).toAbsolutePath().toFile(), new String[]{"bsl", "os"}, true);

    // обработаем КАЖДУЮ дианостику отдельно на всех фикстурах
    diagnosticCodes.forEach((key, value) -> {
      // создадим новый конфиг, в котором включена только текущая диагностика
      var diagnosticCodesCopy = new HashMap<>(diagnosticCodes);
      diagnosticCodesCopy.put(key, true);
      Map<String, Map<String, Boolean>> conf = new HashMap<>();
      conf.put("diagnostics", diagnosticCodesCopy);
      var objectMapper = new ObjectMapper();
      try {
        FileUtils.writeStringToFile(tempCfg, objectMapper.writeValueAsString(conf), StandardCharsets.UTF_8);
      } catch (IOException e) {
        // nop
        return;
      }
      var configuration = LanguageServerConfiguration.create(tempCfg);
      var diagnosticSupplier = new DiagnosticSupplier(configuration);

      // для каждой фикстуры расчитаем диагностику
      // если упадет, запомним файл и текст ошибки
      Map<File, Exception> errors = new HashMap<>();
      fixtures.forEach(filePath -> {
        try {
          (new DiagnosticProvider(diagnosticSupplier))
            .computeDiagnostics(TestUtils.getDocumentContextFromFile(filePath.toString()));
        } catch (Exception e) {
          errors.put(filePath, e);
        }
      });
      if (!errors.isEmpty()) {
        diagnosticErrors.put(key, errors);
      }
    });

    FileUtils.deleteDirectory(tempDir.toFile());
    assertThat(diagnosticErrors).isEmpty();
  }

}
