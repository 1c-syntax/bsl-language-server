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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCompatibilityMode;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.support.CompatibilityMode;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

@DirtiesContext
class TimeoutsInExternalResourcesDiagnosticTest extends AbstractDiagnosticTest<TimeoutsInExternalResourcesDiagnostic> {
  private static final File CONFIGURATION_FILE_PATH = Paths.get("./src/test/resources/metadata/designer/Configuration.xml").toFile();
  private Path tempDir;

  TimeoutsInExternalResourcesDiagnosticTest() {
    super(TimeoutsInExternalResourcesDiagnostic.class);
  }

  @Test
  void test() {

    // when
    List<Diagnostic> diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(9);

    // check ranges
    assertThat(diagnostics, true)
      .hasRange(3, 20, 3, 75)
      .hasRange(5, 20, 5, 92)
      .hasRange(9, 18, 9, 72)
      .hasRange(13, 16, 13, 80)
      .hasRange(21, 21, 21, 65)
      .hasRange(34, 14, 34, 43)
      .hasRange(71, 26, 71, 114)
      .hasRange(78, 10, 78, 39)
      .hasRange(80, 47, 80, 76)
    ;

  }

  @SneakyThrows
  @BeforeEach
  void createTmpDir() {
    tempDir = Files.createTempDirectory("bslls");
  }

  @SneakyThrows
  @AfterEach
  void deleteTmpDir() {
    System.gc();
    FileUtils.deleteDirectory(tempDir.toFile());
  }

  @SneakyThrows
  @Test
  void testCompatibilityMode8310() {

    // when
    Path testFile = Paths.get("./src/test/resources/diagnostics/TimeoutsInExternalResourcesDiagnostic.bsl").toAbsolutePath();
    initServerContext(Paths.get("./src/test/resources/metadata/designer").toAbsolutePath());
    DocumentContext newDocumentContext = TestUtils.getDocumentContext(
      testFile.toUri(),
      FileUtils.readFileToString(testFile.toFile(), StandardCharsets.UTF_8),
      context
    );

    List<Diagnostic> diagnostics = getDiagnostics(newDocumentContext);

    // then
    assertThat(newDocumentContext.getServerContext().getConfiguration().getCompatibilityMode()).isNotNull();
    assertThat(CompatibilityMode.compareTo(
      newDocumentContext.getServerContext().getConfiguration().getCompatibilityMode(),
      DiagnosticCompatibilityMode.COMPATIBILITY_MODE_8_3_10.getCompatibilityMode())).isZero();

    assertThat(diagnostics).hasSize(9);

    // check ranges
    assertThat(diagnostics, true)
      .hasRange(3, 20, 3, 75)
      .hasRange(5, 20, 5, 92)
      .hasRange(9, 18, 9, 72)
      .hasRange(13, 16, 13, 80)
      .hasRange(21, 21, 21, 65)
      .hasRange(34, 14, 34, 43)
      .hasRange(71, 26, 71, 114)
      .hasRange(78, 10, 78, 39)
      .hasRange(80, 47, 80, 76)
    ;

  }

  @SneakyThrows
  @Test
  void testCompatibilityMode836() {

    // when
    FileUtils.writeStringToFile(
      Paths.get(tempDir.toAbsolutePath().toString(), "Configuration.xml").toFile(),
      FileUtils.readFileToString(CONFIGURATION_FILE_PATH, StandardCharsets.UTF_8)
        .replace("Version8_3_10", "Version8_3_6"),
      StandardCharsets.UTF_8);

    Path testFile = Paths.get("./src/test/resources/diagnostics/TimeoutsInExternalResourcesDiagnostic836.bsl").toAbsolutePath();
    initServerContext(tempDir.toAbsolutePath());
    DocumentContext newDocumentContext = TestUtils.getDocumentContext(
      testFile.toUri(),
      FileUtils.readFileToString(testFile.toFile(), StandardCharsets.UTF_8),
      context
    );

    List<Diagnostic> diagnostics = getDiagnostics(newDocumentContext);

    // then
    assertThat(newDocumentContext.getServerContext().getConfiguration().getCompatibilityMode()).isNotNull();
    assertThat(CompatibilityMode.compareTo(
      newDocumentContext.getServerContext().getConfiguration().getCompatibilityMode(),
      DiagnosticCompatibilityMode.COMPATIBILITY_MODE_8_3_6.getCompatibilityMode())).isZero();

    assertThat(diagnostics).hasSize(9);

    // check ranges
    assertThat(diagnostics, true)
      .hasRange(3, 20, 3, 75)
      .hasRange(5, 20, 5, 92)
      .hasRange(9, 18, 9, 72)
      .hasRange(13, 16, 13, 80)
      .hasRange(21, 21, 21, 65)
      .hasRange(34, 14, 34, 43)
      .hasRange(71, 26, 71, 114)
      .hasRange(78, 10, 78, 39)
      .hasRange(80, 47, 80, 76)
    ;

  }

  @SneakyThrows
  @Test
  void testCompatibilityMode837() {

    // when
    FileUtils.writeStringToFile(
      Paths.get(tempDir.toAbsolutePath().toString(), "Configuration.xml").toFile(),
      FileUtils.readFileToString(CONFIGURATION_FILE_PATH, StandardCharsets.UTF_8)
        .replace("Version8_3_10", "Version8_3_7"),
      StandardCharsets.UTF_8);

    Path testFile = Paths.get("./src/test/resources/diagnostics/TimeoutsInExternalResourcesDiagnostic837.bsl").toAbsolutePath();
    initServerContext(tempDir.toAbsolutePath());
    DocumentContext newDocumentContext = TestUtils.getDocumentContext(
      testFile.toUri(),
      FileUtils.readFileToString(testFile.toFile(), StandardCharsets.UTF_8),
      context
    );

    List<Diagnostic> diagnostics = getDiagnostics(newDocumentContext);

    // then
    assertThat(newDocumentContext.getServerContext().getConfiguration().getCompatibilityMode()).isNotNull();
    assertThat(CompatibilityMode.compareTo(
      newDocumentContext.getServerContext().getConfiguration().getCompatibilityMode(),
      DiagnosticCompatibilityMode.COMPATIBILITY_MODE_8_3_7.getCompatibilityMode())).isZero();

    assertThat(diagnostics).hasSize(9);

    // check ranges
    assertThat(diagnostics, true)
      .hasRange(3, 20, 3, 75)
      .hasRange(5, 20, 5, 92)
      .hasRange(9, 18, 9, 72)
      .hasRange(13, 16, 13, 80)
      .hasRange(21, 21, 21, 65)
      .hasRange(34, 14, 34, 43)
      .hasRange(71, 26, 71, 114)
      .hasRange(78, 10, 78, 39)
      .hasRange(80, 47, 80, 76)
    ;

  }

  @Test
  void testConfigure() {

    Map<String, Object> configuration = diagnosticInstance.info.getDefaultConfiguration();
    configuration.put("analyzeInternetMailProfileZeroTimeout", false);
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(6);

    // check ranges
    assertThat(diagnostics, true)
      .hasRange(3, 20, 3, 75)
      .hasRange(5, 20, 5, 92)
      .hasRange(9, 18, 9, 72)
      .hasRange(13, 16, 13, 80)
      .hasRange(21, 21, 21, 65)
      .hasRange(71, 26, 71, 114)
    ;

  }
}
