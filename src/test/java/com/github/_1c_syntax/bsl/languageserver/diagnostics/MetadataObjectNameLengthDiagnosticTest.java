/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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
import com.github._1c_syntax.bsl.mdo.CommonModule;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.children.ObjectModule;
import com.github._1c_syntax.bsl.types.ModuleType;
import lombok.SneakyThrows;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@DirtiesContext
class MetadataObjectNameLengthDiagnosticTest extends AbstractDiagnosticTest<MetadataObjectNameLengthDiagnostic> {

  private static final String LONG_NAME = "ОченьДлинноеИмяОбъектаКотороеВызываетПроблемыВРаботеАТакжеОшибкиВыгрузкиКонфигурации";
  private static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";

  private MD module;
  private DocumentContext documentContext;

  MetadataObjectNameLengthDiagnosticTest() {
    super(MetadataObjectNameLengthDiagnostic.class);
  }

  @Test
  void testConfigure() {

    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("maxMetadataObjectNameLength", 10);
    diagnosticInstance.configure(configuration);

    getDocumentContextFromFile("CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl", "A");

    // when
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    //then
    assertThat(diagnostics).hasSize(1);
  }

  @Test
  void testConfigureNegative() {

    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("maxMetadataObjectNameLength", 90);
    diagnosticInstance.configure(configuration);

    getDocumentContextFromFile("CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl", " ");

    // given
    when(module.getName()).thenReturn(LONG_NAME);

    // when
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    //then
    assertThat(diagnostics).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("contentProvider")
  void testNotEmptyModule(String content, int count) {

    getDocumentContextFromFile("CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl", content);

    // given
    when(module.getName()).thenReturn(LONG_NAME);
    when(documentContext.getMdObject()).thenReturn(Optional.of(module));

    // when
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    //then
    assertThat(diagnostics).hasSize(count);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "Catalogs/Справочник1/Ext/ManagerModule.bsl",
    "Catalogs/Справочник1/Forms/ФормаВыбора/Ext/Form/Module.bsl",
    "CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl"
  })
  void test(String modulePath) {

    getDocumentContextFromFile(modulePath, "A");

    // given
    when(module.getName()).thenReturn(LONG_NAME);

    when(documentContext.getMdObject()).thenReturn(Optional.of(module));

    // when
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    //then
    assertThat(diagnostics).hasSize(1);
  }

  @Test
  void testNegative() {

    getDocumentContextFromFile("CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl", "A");

    // given
    when(module.getName()).thenReturn("Short");

    // when
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    //then
    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testWithoutModules() {

    initServerContext(PATH_TO_METADATA);

    documentContext = spy(getDocumentContext());

    List<MD> children = new ArrayList<>();
    List.of("CommandGroup.ГруппаКоманд1",
      "EventSubscription.ВерсионированиеПриЗаписи",
      "Role.ПолныеПрава").forEach((String mdoName) -> {

      var spyMdo = spy(context.getConfiguration().getChildren().stream()
        .filter(mdo -> mdo.getMdoReference().getMdoRef().equalsIgnoreCase(mdoName))
        .findFirst()
        .get());

      // given
      when(spyMdo.getName()).thenReturn(LONG_NAME);
      children.add(spyMdo);
    });

    var configuration = spy(context.getConfiguration());
    when(configuration.getChildren()).thenReturn(children);
    var context = spy(documentContext.getServerContext());
    when(context.getConfiguration()).thenReturn(configuration);
    when(documentContext.getServerContext()).thenReturn(context);
    when(documentContext.getModuleType()).thenReturn(ModuleType.SessionModule);

    // when
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    // then
    assertThat(diagnostics).hasSize(3);
  }

  @SneakyThrows
  void getDocumentContextFromFile(String modulePath, String content) {
    initServerContext(PATH_TO_METADATA);
    var testFile = new File(PATH_TO_METADATA, modulePath).getAbsoluteFile();
    documentContext = spy(TestUtils.getDocumentContext(testFile.toURI(), content, context));
    var moduleByUri = Objects.requireNonNull(context).getConfiguration()
      .getModuleByUri(documentContext.getUri()).get();
    if (moduleByUri instanceof CommonModule) {
      module = spy((CommonModule) moduleByUri);
    } else {
      module = spy(Objects.requireNonNull(context).getConfiguration().findChild(((ObjectModule) moduleByUri).getOwner())
        .get());
    }
  }

  static Stream<Arguments> contentProvider() {
    return Stream.of(
      Arguments.of("", 0),
      Arguments.of("\n", 0),
      Arguments.of("//", 1),
      Arguments.of(" ", 0),
      Arguments.of("FF", 1),
      Arguments.of("\nA", 1),
      Arguments.of("#\n", 1),
      Arguments.of("\n#", 1),
      Arguments.of("&", 1)
    );
  }
}
