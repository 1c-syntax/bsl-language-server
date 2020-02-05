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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class NonStandardRegionDiagnosticTest extends AbstractDiagnosticTest<NonStandardRegionDiagnostic> {
  private static final File CONFIGURATION_FILE_PATH = Paths.get("./src/test/resources/metadata/Configuration.xml").toFile();
  private final Path testFile = Paths.get("./src/test/resources/diagnostics/NonStandardRegionDiagnosticTemplate.bsl").toAbsolutePath();
  private final Map<ModuleType, String> pathByModuleType = new HashMap<>();
  private Path tempDir;

  NonStandardRegionDiagnosticTest() {
    super(NonStandardRegionDiagnostic.class);
    pathByModuleType.put(ModuleType.CommandModule, "CommandModule.bsl");
    pathByModuleType.put(ModuleType.ObjectModule, "ObjectModule.bsl");
    pathByModuleType.put(ModuleType.ManagerModule, "ManagerModule.bsl");
    pathByModuleType.put(ModuleType.ManagedApplicationModule, "ManagedApplicationModule.bsl");
    pathByModuleType.put(ModuleType.SessionModule, "SessionModule.bsl");
    pathByModuleType.put(ModuleType.ExternalConnectionModule, "ExternalConnectionModule.bsl");
    pathByModuleType.put(ModuleType.FormModule, "Form/Module.bsl");
    pathByModuleType.put(ModuleType.CommonModule, "Module.bsl");
    pathByModuleType.put(ModuleType.RecordSetModule, "RecordSetModule.bsl");
  }

  @Test
  void testUnknown() {

    // для неизвестного модуля
    List<Diagnostic> diagnostics = getDiagnostics();
    assertThat(diagnostics).isEmpty();
  }

  @SneakyThrows
  @BeforeEach
  void createTmpDir() {
    tempDir = Files.createTempDirectory("bslls");
  }

  @SneakyThrows
  @AfterEach
  void deleteTmpDir() {
    FileUtils.deleteDirectory(tempDir.toFile());
  }

  @Test
  void testFormModule() throws IOException {

    List<Diagnostic> diagnostics = getDiagnostics(getFixtureDocumentContextByModuleType(ModuleType.FormModule));

    assertThat(diagnostics).hasSize(5);
    assertThat(diagnostics, true)
      .hasRange(7, 1, 7, 29)
      .hasRange(11, 1, 11, 38)
      .hasRange(20, 1, 20, 21)
      .hasRange(24, 1, 24, 16)
      .hasRange(44, 1, 44, 27)
    ;
  }

  @Test
  void testObjectModule() throws IOException {

    List<Diagnostic> diagnostics = getDiagnostics(getFixtureDocumentContextByModuleType(ModuleType.ObjectModule));

    assertThat(diagnostics).hasSize(4);
    assertThat(diagnostics, true)
      .hasRange(28, 1, 32)
      .hasRange(32, 1, 46)
      .hasRange(36, 1, 63)
      .hasRange(40, 1, 31)
    ;
  }

  @Test
  void testManagerModule() throws IOException {

    List<Diagnostic> diagnostics = getDiagnostics(getFixtureDocumentContextByModuleType(ModuleType.ManagerModule));

    assertThat(diagnostics).hasSize(6);
    assertThat(diagnostics, true)
      .hasRange(0, 1, 27)
      .hasRange(28, 1, 32)
      .hasRange(32, 1, 46)
      .hasRange(36, 1, 63)
      .hasRange(40, 1, 31)
      .hasRange(52, 1, 18)
    ;
  }

  @Test
  void testSessionModule() throws IOException {

    List<Diagnostic> diagnostics = getDiagnostics(getFixtureDocumentContextByModuleType(ModuleType.SessionModule));

    assertThat(diagnostics).hasSize(9);
    assertThat(diagnostics, true)
      .hasRange(0, 1, 27)
      .hasRange(7, 1, 29)
      .hasRange(11, 1, 38)
      .hasRange(24, 1, 16)
      .hasRange(28, 1, 32)
      .hasRange(32, 1, 46)
      .hasRange(36, 1, 63)
      .hasRange(40, 1, 31)
      .hasRange(52, 1, 18)
    ;
  }

  @Test
  void testCommonModule() throws IOException {

    List<Diagnostic> diagnostics = getDiagnostics(getFixtureDocumentContextByModuleType(ModuleType.CommonModule));

    assertThat(diagnostics).hasSize(8);
    assertThat(diagnostics, true)
      .hasRange(0, 1, 27)
      .hasRange(20, 1, 21)
      .hasRange(28, 1, 32)
      .hasRange(32, 1, 46)
      .hasRange(36, 1, 63)
      .hasRange(40, 1, 31)
      .hasRange(44, 1, 27)
      .hasRange(52, 1, 18)
    ;
  }

  @Test
  void testExternalConnectionModule() throws IOException {

    List<Diagnostic> diagnostics = getDiagnostics(getFixtureDocumentContextByModuleType(ModuleType.ExternalConnectionModule));

    assertThat(diagnostics).hasSize(8);
    assertThat(diagnostics, true)
      .hasRange(0, 1, 27)
      .hasRange(11, 1, 38)
      .hasRange(24, 1, 16)
      .hasRange(28, 1, 32)
      .hasRange(32, 1, 46)
      .hasRange(36, 1, 63)
      .hasRange(40, 1, 31)
      .hasRange(52, 1, 18)
    ;
  }

  @Test
  void testManagedApplicationModule() throws IOException {

    List<Diagnostic> diagnostics = getDiagnostics(getFixtureDocumentContextByModuleType(ModuleType.ManagedApplicationModule));

    assertThat(diagnostics).hasSize(7);
    assertThat(diagnostics, true)
      .hasRange(11, 1, 38)
      .hasRange(24, 1, 16)
      .hasRange(28, 1, 32)
      .hasRange(32, 1, 46)
      .hasRange(36, 1, 63)
      .hasRange(40, 1, 31)
      .hasRange(52, 1, 18)
    ;
  }

  @Test
  void testCommandModule() throws IOException {

    List<Diagnostic> diagnostics = getDiagnostics(getFixtureDocumentContextByModuleType(ModuleType.CommandModule));

    assertThat(diagnostics).hasSize(9);
    assertThat(diagnostics, true)
      .hasRange(0, 1, 27)
      .hasRange(7, 1, 29)
      .hasRange(11, 1, 38)
      .hasRange(24, 1, 16)
      .hasRange(28, 1, 32)
      .hasRange(32, 1, 46)
      .hasRange(36, 1, 63)
      .hasRange(40, 1, 31)
      .hasRange(52, 1, 18)
    ;
  }

  @Test
  void testRecordSetModule() throws IOException {

    List<Diagnostic> diagnostics = getDiagnostics(getFixtureDocumentContextByModuleType(ModuleType.RecordSetModule));

    assertThat(diagnostics).hasSize(4);
    assertThat(diagnostics, true)
      .hasRange(28, 1, 32)
      .hasRange(32, 1, 46)
      .hasRange(36, 1, 63)
      .hasRange(40, 1, 31)
    ;
  }

  private DocumentContext getFixtureDocumentContextByModuleType(ModuleType moduleType) throws IOException {
    Path tempFile = Paths.get(
      tempDir.toAbsolutePath().toString(),
      "fake",
      pathByModuleType.getOrDefault(moduleType, "Module.bsl")
    );

    FileUtils.copyFile(testFile.toFile(), tempFile.toFile());
    FileUtils.copyFile(
      CONFIGURATION_FILE_PATH,
      Paths.get(tempDir.toAbsolutePath().toString(), "Configuration.xml").toFile()
    );

    return new DocumentContext(
      tempFile.toUri(),
      FileUtils.readFileToString(tempFile.toFile(), StandardCharsets.UTF_8),
      new ServerContext(tempDir.toRealPath())
    );
  }
}
