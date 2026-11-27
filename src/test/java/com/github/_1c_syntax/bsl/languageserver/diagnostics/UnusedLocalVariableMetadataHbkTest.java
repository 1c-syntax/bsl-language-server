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

import com.github._1c_syntax.bsl.languageserver.configuration.diagnostics.Mode;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.mdo.Form;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

@TestPropertySource(properties = "app.platform-context.enabled=true")
@EnabledIfEnvironmentVariable(named = "BSL_LANGUAGE_SERVER_RUN_HBK_TESTS", matches = "true",
  disabledReason = "Requires the installed 1C syntax helper")
class UnusedLocalVariableMetadataHbkTest extends AbstractServerContextAwareTest {
  private static final Path ROOT = Path.of("src/test/resources/metadata/designer");
  private static final Path FIXTURES =
    Path.of("src/test/resources/diagnostics/UnusedLocalVariable/module-unused-local-variable");
  @Autowired
  private TypeService types;

  @ParameterizedTest
  @CsvSource({
    "ObjectModule, Catalogs/Справочник1/Ext/ObjectModule.bsl, 1, 2, 10",
    "FormModule, Catalogs/Справочник1/Forms/ФормаЭлемента/Ext/Form/Module.bsl, 6, 2, 9",
    "RecordSetModule, InformationRegisters/РегистрСведений1/Ext/RecordSetModule.bsl, 3, 2, 13"
  })
  void detectsUnusedAndAcceptsFixedVariant(ModuleType type, String file, int line, int start, int end)
    throws IOException {
    initServerContext(ROOT, false);
    context.getConfiguration();
    enableRule();
    var source = Files.readString(FIXTURES.resolve(type.name() + ".bsl"));
    var document = TestUtils.getDocumentContext(ROOT.resolve(file).toUri(), source, context);
    assertThat(document.getModuleType()).isEqualTo(type);
    var diagnostics = document.getDiagnostics();
    assertThat(diagnostics, true).hasRange(line, start, end);
    if (type == ModuleType.RecordSetModule) {
      assertThat(types.isBareSelfProperty(document, "Filter")).isTrue();
      assertThat(types.isBareSelfProperty(document, "Справочник1")).isFalse();
      assertThat(diagnostics, true).hasRange(18, 11, 21);
      assertThat(diagnostics).hasSize(2);
    } else {
      assertThat(diagnostics).hasSize(1);
    }
  }

  @Test
  void missingFormContentDoesNotProveAbsenceOfAnAttribute() {
    var root = Path.of("src/test/resources/metadata/reportSelfMembers");
    initServerContext(root, false);
    context.getConfiguration();
    enableRule();
    var file = root.resolve("Reports/Отчет1/Forms/ДопФорма/Ext/Form/Module.bsl");
    var document = TestUtils.getDocumentContext(file.toUri(), """
      &НаСервере
      Процедура Проверка()
        Перем Явная;
        НеизвестныйРеквизит = 1;
      КонецПроцедуры
      """, context);
    assertThat(document.getModuleType()).isEqualTo(ModuleType.FormModule);
    var form = (Form) document.getMdObject().orElseThrow();
    assertThat(form.getData().isEmpty()).isTrue();
    assertThat(document.getDiagnostics()).hasSize(1);
    assertThat(document.getDiagnostics(), true).hasRange(2, 8, 13);
  }

  private void enableRule() {
    var options = context.getLanguageServerConfiguration().getDiagnosticsOptions();
    options.setMode(Mode.ONLY);
    options.setParameters(Map.of("UnusedLocalVariable", Either.forLeft(true)));
  }
}
