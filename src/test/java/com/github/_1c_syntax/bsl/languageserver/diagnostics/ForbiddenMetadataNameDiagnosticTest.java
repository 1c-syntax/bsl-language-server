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

import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.ModuleOwner;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class ForbiddenMetadataNameDiagnosticTest extends AbstractDiagnosticTest<ForbiddenMetadataNameDiagnostic> {
  ForbiddenMetadataNameDiagnosticTest() {
    super(ForbiddenMetadataNameDiagnostic.class);
  }

  private static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";

  @BeforeEach
  void beforeTest() {
    initServerContext(Absolute.path(PATH_TO_METADATA));
  }

  @Test
  void testCatalog() {
    var documentContext = spy(getDocumentContext());

    var spyCatalog = spy(context.getConfiguration()
      .findCatalog(catalog -> catalog.getName().equalsIgnoreCase("Справочник1"))
      .get());

    when(documentContext.getModuleType()).thenReturn(ModuleType.ManagerModule);
    when(spyCatalog.getName()).thenReturn("Справочник");

    List<MD> children = new ArrayList<>();
    spyCatalog.getMDOPlainChildren()
      .forEach(mdo -> {
        var spyMDO = spy(mdo);
        when(spyMDO.getName()).thenReturn("РегистрСведений");
        var mdoRef = spy(mdo.getMdoReference());
        when(mdoRef.getMdoRefRu())
          .thenReturn(mdo.getMdoReference().getMdoRefRu().replace("." + mdo.getName(), ".РегистрСведений"));
        when(spyMDO.getMdoReference()).thenReturn(mdoRef);
        when(spyMDO.getName()).thenReturn("РегистрСведений");
        children.add(spyMDO);
      });

    when(spyCatalog.getMDOPlainChildren()).thenReturn(children);
    when(documentContext.getMdObject()).thenReturn(Optional.of(spyCatalog));

    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    assertThat(diagnostics).hasSize(7);
    assertThat(diagnostics, true)
      .hasMessageOnRange("Запрещено использовать имя `Справочник` для `Справочник.Справочник1`", 0, 0, 9)
      .hasMessageOnRange(
        "Запрещено использовать имя `РегистрСведений` для `Справочник.Справочник1.Реквизит.РегистрСведений`",
        0, 0, 9)
      .hasMessageOnRange(
        "Запрещено использовать имя `РегистрСведений` для `Справочник.Справочник1.ТабличнаяЧасть.РегистрСведений`",
        0, 0, 9)
    ;
  }

  @Test
  void testOtherMDO() {
    var documentContext = spy(getDocumentContext());

    List<MD> children = new ArrayList<>();

    context.getConfiguration().getChildren().forEach(mdo -> {
      var spyMDO = spy(mdo);
      when(spyMDO.getName()).thenReturn("РегистрСведений");
      var mdoRef = spy(mdo.getMdoReference());
      when(mdoRef.getMdoRefRu())
        .thenReturn(mdo.getMdoReference().getMdoRefRu().replace("." + mdo.getName(), ".РегистрСведений"));
      when(spyMDO.getMdoReference()).thenReturn(mdoRef);
      when(spyMDO.getName()).thenReturn("РегистрСведений");
      children.add(spyMDO);
    });

    var configuration = spy(context.getConfiguration());
    when(configuration.getChildren()).thenReturn(children);
    var context = spy(documentContext.getServerContext());
    when(context.getConfiguration()).thenReturn(configuration);
    when(documentContext.getServerContext()).thenReturn(context);
    when(documentContext.getModuleType()).thenReturn(ModuleType.SessionModule);

    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    // должен отфильтроваться справочник, т.к. модули у него есть
    assertThat(diagnostics)
      .hasSize(1)
      .noneMatch(diagnostic -> diagnostic.getMessage().contains("для `Справочник.Справочник1"));
  }

  @Test
  void testAllMDO() {
    var documentContext = spy(getDocumentContext());

    List<MD> children = new ArrayList<>();

    context.getConfiguration().getChildren().forEach(mdo -> {
      var spyMDO = spy(mdo);
      when(spyMDO.getName()).thenReturn("РегистрСведений");
      var mdoRef = spy(mdo.getMdoReference());
      when(mdoRef.getMdoRefRu())
        .thenReturn(mdo.getMdoReference().getMdoRefRu().replace("." + mdo.getName(), ".РегистрСведений"));
      when(spyMDO.getMdoReference()).thenReturn(mdoRef);
      when(spyMDO.getName()).thenReturn("РегистрСведений");
      if (mdo instanceof ModuleOwner) {
        when(((ModuleOwner) spyMDO).getModules()).thenReturn(Collections.emptyList());
      }
      children.add(spyMDO);
    });

    var configuration = spy(context.getConfiguration());
    when(configuration.getChildren()).thenReturn(children);
    var context = spy(documentContext.getServerContext());
    when(context.getConfiguration()).thenReturn(configuration);
    when(documentContext.getServerContext()).thenReturn(context);
    when(documentContext.getModuleType()).thenReturn(ModuleType.SessionModule);

    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    // должен отфильтроваться справочник, т.к. модули у него есть
    assertThat(diagnostics)
      .hasSize(3)
      .allMatch(diagnostic -> diagnostic.getMessage().contains("Запрещено использовать имя `РегистрСведений` для"))
      .anyMatch(diagnostic -> diagnostic.getMessage().contains("для `Справочник.РегистрСведений"))
      .anyMatch(diagnostic -> diagnostic.getMessage().contains("для `Документ.РегистрСведений"))
      .anyMatch(diagnostic -> diagnostic.getMessage().contains("для `РегистрСведений.РегистрСведений"))
    ;
  }
}