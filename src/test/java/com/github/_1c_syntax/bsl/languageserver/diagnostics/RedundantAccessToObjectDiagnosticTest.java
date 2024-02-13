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
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.mdo.CommonModule;
import com.github._1c_syntax.bsl.mdo.support.ReturnValueReuse;
import com.github._1c_syntax.utils.Absolute;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@CleanupContextBeforeClassAndAfterEachTestMethod
class RedundantAccessToObjectDiagnosticTest extends AbstractDiagnosticTest<RedundantAccessToObjectDiagnostic> {
  RedundantAccessToObjectDiagnosticTest() {
    super(RedundantAccessToObjectDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(9);
    assertThat(diagnostics, true)
      .hasRange(2, 4, 2, 14)
      .hasRange(3, 4, 3, 14)
      .hasRange(6, 18, 6, 28)
      .hasRange(8, 18, 8, 28)
      .hasRange(10, 4, 10, 14)
      .hasRange(16, 4, 16, 14)
      .hasRange(17, 4, 17, 14)
      .hasRange(20, 15, 20, 25)
      .hasRange(22, 4, 22, 14);

  }

  @Test
  void testCommonModule() {
    var documentContext = createDocumentContextFromFile(
      "src/test/resources/metadata/designer/CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl"
    );
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);
    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(78, 4, 78, 21);
  }

  @Test
  void testCommonModuleCached() {
    var documentContext = createDocumentContextFromFile(
      "src/test/resources/metadata/designer/CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl"
    );

    var configuration = context.getConfiguration();
    var module = spy((CommonModule) configuration.findChild(documentContext.getUri()).get());

    when(module.getReturnValuesReuse()).thenReturn(ReturnValueReuse.DURING_SESSION);
    when(documentContext.getMdObject()).thenReturn(Optional.of(module));

    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);
    assertThat(diagnostics).isEmpty();

  }

  @SneakyThrows
  DocumentContext createDocumentContextFromFile(String pathToFile) {
    Path path = Absolute.path("src/test/resources/metadata/designer");
    Path testFile = Paths.get(pathToFile).toAbsolutePath();

    initServerContext(path);
    return spy(TestUtils.getDocumentContext(
      testFile.toUri(),
      FileUtils.readFileToString(testFile.toFile(), StandardCharsets.UTF_8),
      context
    ));
  }

  @Test
  void testCatalogsManagerModule() {
    var documentContext = createDocumentContextFromFile(
      "src/test/resources/metadata/designer/Catalogs/Справочник1/Ext/ManagerModule.bsl"
    );
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);
    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(19, 4, 19, 15);
  }

  @Test
  void testCatalogsObjectModule() {
    var documentContext = createDocumentContextFromFile(
      "src/test/resources/metadata/designer/Catalogs/Справочник1/Ext/ObjectModule.bsl"
    );
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);
    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(56, 0, 10);
  }

  @Test
  void testCatalogsObjectModuleWithConfig() {
    var documentContext = createDocumentContextFromFile(
      "src/test/resources/metadata/designer/Catalogs/Справочник1/Ext/ObjectModule.bsl"
    );

    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("checkObjectModule", false);
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);
    assertThat(diagnostics).hasSize(0);
  }

  @Test
  void testCatalogsFormModule() {
    var documentContext = createDocumentContextFromFile(
      "src/test/resources/metadata/designer/Catalogs/Справочник1/Forms/ФормаЭлемента/Ext/Form/Module.bsl"
    );
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);
    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(64, 0, 10);
  }

  @Test
  void testCatalogsFormModuleWithConfig() {
    var documentContext = createDocumentContextFromFile(
      "src/test/resources/metadata/designer/Catalogs/Справочник1/Forms/ФормаЭлемента/Ext/Form/Module.bsl"
    );

    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("checkFormModule", false);
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);
    assertThat(diagnostics).hasSize(0);
  }

  @Test
  void testInformationRegistersManagerModule() {
    var documentContext = createDocumentContextFromFile(
      "src/test/resources/metadata/designer/InformationRegisters/РегистрСведений1/Ext/ManagerModule.bsl"
    );
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);
    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(18, 4, 18, 20);
  }

  @Test
  void testInformationRegistersRecordSetModule() {
    var documentContext = createDocumentContextFromFile(
      "src/test/resources/metadata/designer/InformationRegisters/РегистрСведений1/Ext/RecordSetModule.bsl"
    );
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);
    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(56, 0, 10);
  }

  @Test
  void testInformationRegistersRecordSetModuleWithConfig() {
    var documentContext = createDocumentContextFromFile(
      "src/test/resources/metadata/designer/InformationRegisters/РегистрСведений1/Ext/RecordSetModule.bsl"
    );

    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("checkRecordSetModule", false);
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);
    assertThat(diagnostics).hasSize(0);
  }
}
