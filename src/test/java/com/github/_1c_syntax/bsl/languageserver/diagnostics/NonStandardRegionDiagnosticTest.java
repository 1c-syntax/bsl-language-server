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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

@DirtiesContext
class NonStandardRegionDiagnosticTest extends AbstractDiagnosticTest<NonStandardRegionDiagnostic> {
  private static final Path CONFIGURATION_PATH = Path.of("src/test/resources/metadata/designer");
  private final Map<ModuleType, String> pathByModuleType = new HashMap<>();

  NonStandardRegionDiagnosticTest() {
    super(NonStandardRegionDiagnostic.class);
    pathByModuleType.put(ModuleType.CommandModule, "Catalogs/Справочник1/Commands/Команда1/Ext/CommandModule.bsl");
    pathByModuleType.put(ModuleType.ObjectModule, "Catalogs/Справочник1/Ext/ObjectModule.bsl");
    pathByModuleType.put(ModuleType.ManagerModule, "Catalogs/Справочник1/Ext/ManagerModule.bsl");
    pathByModuleType.put(ModuleType.ManagedApplicationModule, "Ext/ManagedApplicationModule.bsl");
    pathByModuleType.put(ModuleType.SessionModule, "Ext/SessionModule.bsl");
    pathByModuleType.put(ModuleType.ExternalConnectionModule, "Ext/ExternalConnectionModule.bsl");
    pathByModuleType.put(ModuleType.FormModule, "Catalogs/Справочник1/Forms/ФормаЭлемента/Ext/Form/Module.bsl");
    pathByModuleType.put(ModuleType.CommonModule, "CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl");
    pathByModuleType.put(ModuleType.RecordSetModule, "InformationRegisters/РегистрСведений1/Ext/RecordSetModule.bsl");
    pathByModuleType.put(ModuleType.HTTPServiceModule, "HTTPServices/HTTPСервис1/Ext/Module.bsl");
    pathByModuleType.put(ModuleType.WEBServiceModule, "WebServices/WebСервис1/Ext/Module.bsl");
  }

  @Test
  void testUnknown() {

    // для неизвестного модуля
    List<Diagnostic> diagnostics = getDiagnostics();
    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testFormModule() throws IOException {

    List<Diagnostic> diagnostics = getDiagnostics(getFixtureDocumentContextByModuleType(ModuleType.FormModule));

    assertThat(diagnostics).hasSize(5);
    assertThat(diagnostics, true)
      .hasRange(15, 1, 29)
      .hasRange(19, 1, 38)
      .hasRange(28, 1, 21)
      .hasRange(32, 1, 16)
      .hasRange(52, 1, 27)
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

    assertThat(diagnostics).hasSize(5);
    assertThat(diagnostics, true)
      .hasRange(0, 1, 27)
      .hasRange(39, 1, 32)
      .hasRange(43, 1, 46)
      .hasRange(47, 1, 63)
      .hasRange(51, 1, 31)
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
      .hasRange(5, 1, 27)
      .hasRange(25, 1, 21)
      .hasRange(33, 1, 32)
      .hasRange(37, 1, 46)
      .hasRange(41, 1, 63)
      .hasRange(45, 1, 31)
      .hasRange(49, 1, 27)
      .hasRange(91, 1, 18)
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
      .hasRange(13, 1, 38)
      .hasRange(26, 1, 16)
      .hasRange(30, 1, 32)
      .hasRange(34, 1, 46)
      .hasRange(38, 1, 63)
      .hasRange(42, 1, 31)
      .hasRange(54, 1, 18)
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

  @Test
  void testHTTPServiceModule() throws IOException {

    List<Diagnostic> diagnostics = getDiagnostics(getFixtureDocumentContextByModuleType(ModuleType.HTTPServiceModule));

    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics, true)
      .hasRange(0, 1, 29)
      .hasRange(4, 1, 38)
    ;
  }

  @Test
  void testWEBServiceModule() throws IOException {

    List<Diagnostic> diagnostics = getDiagnostics(getFixtureDocumentContextByModuleType(ModuleType.WEBServiceModule));

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(20, 1, 22)
    ;
  }

  private DocumentContext getFixtureDocumentContextByModuleType(ModuleType moduleType) throws IOException {
    Path tempFile = Path.of(CONFIGURATION_PATH.toString(),
      pathByModuleType.getOrDefault(moduleType, "Module.bsl")
    );

    initServerContext(CONFIGURATION_PATH.toRealPath());
    return TestUtils.getDocumentContext(
      tempFile.toRealPath().toUri(),
      FileUtils.readFileToString(tempFile.toFile(), StandardCharsets.UTF_8),
      context
    );
  }
}
