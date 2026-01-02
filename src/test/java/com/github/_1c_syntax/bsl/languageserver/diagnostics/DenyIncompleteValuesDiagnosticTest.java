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
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.mdo.InformationRegister;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.types.MDOType;
import com.github._1c_syntax.bsl.types.MdoReference;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@CleanupContextBeforeClassAndAfterEachTestMethod
class DenyIncompleteValuesDiagnosticTest extends AbstractDiagnosticTest<DenyIncompleteValuesDiagnostic> {
  DenyIncompleteValuesDiagnosticTest() {
    super(DenyIncompleteValuesDiagnostic.class);
  }

  private static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";
  private static final String PATH_TO_MANAGER_MODULE_FILE = "InformationRegisters/РегистрСведений1/Ext/ManagerModule.bsl";
  private static final String PATH_TO_OBJECT_MODULE_FILE = "InformationRegisters/РегистрСведений1/Ext/RecordSetModule.bsl";

  @Test
  void testMdoWithoutModule() {
    checkMockHandler(ModuleType.SessionModule, true);
  }

  @Test
  void testMdoWithOneModule() {
    checkMockHandler(ModuleType.ManagerModule, false);
  }

  @Test
  void testMdoWithModules() {
    var path = Absolute.path(PATH_TO_METADATA);
    context.setConfigurationRoot(path);

    var managerDocumentContext = addDocumentContext(context, PATH_TO_MANAGER_MODULE_FILE);
    var recordSetDocumentContext = addDocumentContext(context, PATH_TO_OBJECT_MODULE_FILE);

    final var diagnostics = getDiagnostics(managerDocumentContext);

    assertThat(diagnostics, true)
      .hasMessageOnRange("Не указан флаг \"Запрет незаполненных значений\" у измерения \"Справочник1\" метаданного \"РегистрСведений.РегистрСведений1\"",
        0, 0, 9)
      .hasSize(1);

    final var diagnostics2 = getDiagnostics(recordSetDocumentContext);
    assertThat(diagnostics2, true).isEmpty();
  }

  private void checkMockHandler(ModuleType type, boolean noneModules) {
    initServerContext(Absolute.path(PATH_TO_METADATA));

    var documentContext = spy(getDocumentContext());
    when(documentContext.getModuleType()).thenReturn(type);

    final var infoReg = spy((InformationRegister) context.getConfiguration().findChild(MdoReference.create(MDOType.INFORMATION_REGISTER,
      "РегистрСведений1")).orElseThrow()
    );

    when(documentContext.getMdObject()).thenReturn(Optional.of(infoReg));

    if (noneModules) {
      when(infoReg.getModules()).thenReturn(Collections.emptyList());

      List<MD> children = List.of(infoReg);

      var configuration = spy(context.getConfiguration());
      when(configuration.getChildren()).thenReturn(children);
      var serverContext = spy(documentContext.getServerContext());
      when(serverContext.getConfiguration()).thenReturn(configuration);
      when(documentContext.getServerContext()).thenReturn(serverContext);
    }

    final var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics, true)
      .hasMessageOnRange("Не указан флаг \"Запрет незаполненных значений\" у измерения \"Справочник1\" метаданного \"РегистрСведений.РегистрСведений1\"",
        0, 0, 9)
      .hasSize(1);
  }

  private DocumentContext addDocumentContext(ServerContext serverContext, String path) {
    var file = new File(PATH_TO_METADATA, path);
    var uri = Absolute.uri(file);
    var documentContext = serverContext.addDocument(uri);
    serverContext.rebuildDocument(documentContext);
    return documentContext;
  }
}
