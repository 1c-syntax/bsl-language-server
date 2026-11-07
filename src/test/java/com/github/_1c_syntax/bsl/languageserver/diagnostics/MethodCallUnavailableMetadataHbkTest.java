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

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Path;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "app.platform-context.enabled=true")
@EnabledIfEnvironmentVariable(named = "BSL_LANGUAGE_SERVER_RUN_HBK_TESTS", matches = "true",
  disabledReason = "Requires the installed 1C syntax helper")
class MethodCallUnavailableMetadataHbkTest extends AbstractDiagnosticTest<MethodCallUnavailableDiagnostic> {
  private static final Path ROOT = Path.of("src/test/resources/metadata/designer");

  @Autowired
  private TypeService types;
  @Autowired
  private TypeRegistry registry;

  MethodCallUnavailableMetadataHbkTest() {
    super(MethodCallUnavailableDiagnostic.class);
  }

  @ParameterizedTest
  @CsvSource({"Метаданные, НайтиПоТипу, ПолноеИмя, true", "Metadata, FindByType, FullName, true",
    "Метаданные, НайтиПоТипу, Имя, false", "Metadata, FindByType, Name, false"})
  void metadataFoundByTypeMembersAreAvailableOnServer(String metadata, String find, String name, boolean method) {
    initServerContext(ROOT);
    var line = "  Name = %s.%s(ValueType).%s%s;".formatted(metadata, find, name, method ? "()" : "");
    var document = TestUtils.getDocumentContext(
      ROOT.resolve("Catalogs/Справочник1/Forms/ФормаСписка/Ext/Form/Module.bsl").toUri(),
      "&AtServer\nProcedure Caller(ValueType)\n" + line + "\nEndProcedure\n", context);
    var options = new HashMap<>(diagnosticInstance.getInfo().getDefaultConfiguration());
    options.replaceAll((option, value) -> "off");
    options.put("checkServer", "on");
    diagnosticInstance.configure(options);

    var targets = types.membersAt(document, new Position(2, line.lastIndexOf(name)));
    assertThat(targets).isNotEmpty();
    var details = targets.stream().map(target -> target.owner() + ": member="
      + target.descriptor().metadata().availabilities() + ", type="
      + (target.owner() == null ? "unknown" : registry.getTypeMetadata(target.owner(), FileType.BSL).availabilities()))
      .toList();

    assertThat(getDiagnostics(document)).as("targets=%s", details).isEmpty();
  }
}
