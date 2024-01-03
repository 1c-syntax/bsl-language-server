/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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

import com.github._1c_syntax.bsl.languageserver.BSLLSPLauncher;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import mockit.Mock;
import mockit.MockUp;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@SpringBootTest
@Slf4j
@CleanupContextBeforeClassAndAfterEachTestMethod
class SmokyTest {

  @Autowired
  private LanguageServerConfiguration configuration;

  @Autowired
  private Collection<DiagnosticInfo> diagnosticInfos;

  @BeforeEach
  void setUpStreams() {
    new MockUp<System>() {
      @Mock
      public void exit(int value) {
        throw new RuntimeException(String.valueOf(value));
      }
    };
  }

  @Test
  void test() throws Exception {

    // given
    String[] args = new String[]{"--analyze", "--srcDir", "./src/test/resources/diagnostics"};

    // when-then
    assertThatThrownBy(() -> BSLLSPLauncher.main(args))
      .isInstanceOf(RuntimeException.class)
      .hasMessage("0");

  }

  @Test
  void testIdenticalRanges() {

    var srcDir = "./src/test/resources/";
    List<Diagnostic> diagnostics = new ArrayList<>();
    FileUtils.listFiles(Paths.get(srcDir).toAbsolutePath().toFile(), new String[]{"bsl", "os"}, true)
      .forEach(filePath -> {
        LOGGER.info(filePath.toString());
        var documentContext = TestUtils.getDocumentContextFromFile(filePath.toString());
        documentContext.getDiagnostics().stream()
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
    Map<String, Either<Boolean, Map<String, Object>>> diagnostics = diagnosticInfos.stream()
      .map(DiagnosticInfo::getCode)
      .collect(Collectors.toMap(
        diagnosticCode -> diagnosticCode.getStringValue(),
        diagnosticCode -> Either.forLeft(true),
        (a, b) -> b));

    // создадим новый конфиг, в котором включим все диагностики
    configuration.getDiagnosticsOptions().setParameters(diagnostics);

    // для каждой фикстуры расчитаем диагностики
    // если упадет, запомним файл и текст ошибки
    Map<File, Exception> diagnosticErrors = new HashMap<>();
    fixtures.forEach(filePath -> {
      try {
        var documentContext = TestUtils.getDocumentContextFromFile(filePath.toString());
        documentContext.getDiagnostics();
      } catch (Exception e) {
        diagnosticErrors.put(filePath, e);
      }
    });

    assertThat(diagnosticErrors).isEmpty();
  }

}
