/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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

import com.github._1c_syntax.bsl.mdo.Attribute;
import com.github._1c_syntax.bsl.mdo.AttributeOwner;
import com.github._1c_syntax.bsl.mdo.Catalog;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.ModuleOwner;
import com.github._1c_syntax.bsl.mdo.TabularSection;
import com.github._1c_syntax.bsl.mdo.TabularSectionOwner;
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

class SameMetadataObjectAndChildNamesDiagnosticTest extends AbstractDiagnosticTest<SameMetadataObjectAndChildNamesDiagnostic> {
  SameMetadataObjectAndChildNamesDiagnosticTest() {
    super(SameMetadataObjectAndChildNamesDiagnostic.class);
  }

  private static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";

  @BeforeEach
  void beforeTest() {
    initServerContext(Absolute.path(PATH_TO_METADATA));
  }

  @Test
  void testCatalog() {
    var documentContext = spy(getDocumentContext());

    var spyMDO = (Catalog) spy(context.getConfiguration().getChildren().stream()
      .filter(mdo -> mdo.getMdoReference().getMdoRefRu().equalsIgnoreCase("Справочник.Справочник1"))
      .findFirst()
      .get());

    when(documentContext.getModuleType()).thenReturn(ModuleType.ManagerModule);

    List<Attribute> attributes = new ArrayList<>();
    List<TabularSection> tabularSections = new ArrayList<>();

    var attribute = spy(spyMDO.getAllAttributes().stream()
      .findFirst()
      .get());
    when(attribute.getName()).thenReturn("Справочник1");

    attributes.add(attribute);

    var tabularSection = spy(spyMDO.getTabularSections().stream()
      .findFirst()
      .get());

    when(tabularSection.getName()).thenReturn("Тара");
    tabularSections.add(tabularSection);

    var tabAttribute = spy(tabularSection.getAttributes().get(0));
    when(tabAttribute.getName()).thenReturn("Тара");
    when(tabularSection.getAttributes()).thenReturn(List.of(tabAttribute));

    when(spyMDO.getAllAttributes()).thenReturn(attributes);
    when(spyMDO.getTabularSections()).thenReturn(tabularSections);
    when(documentContext.getMdObject()).thenReturn(Optional.of(spyMDO));

    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics, true)
      .anyMatch(diagnostic -> diagnostic.getMessage().contains("Справочник1.Реквизит"))
      .anyMatch(diagnostic -> diagnostic.getMessage().contains("Справочник1.ТабличнаяЧасть"))
    ;
  }

  @Test
  void testOtherMDO() {
    var documentContext = spy(getDocumentContext());

    List<MD> children = new ArrayList<>();

    context.getConfiguration().getChildren().forEach(mdo -> {
      var spyMDO = spy(mdo);

      if (!(mdo instanceof ModuleOwner || mdo instanceof AttributeOwner || mdo instanceof TabularSectionOwner)
        || mdo.getName().equalsIgnoreCase("Справочник1")
      ) {
        return;
      }

      if (mdo instanceof ModuleOwner) {
        when(((ModuleOwner) spyMDO).getModules()).thenReturn(Collections.emptyList());
      }

      mockAttributes(mdo, spyMDO, mdo.getName());

      if (mdo instanceof TabularSectionOwner tabularSectionOwner) {
        List<TabularSection> tabularSections = new ArrayList<>();
        tabularSectionOwner.getTabularSections().forEach(tabularSection -> {
          var spyTabularSection = spy(tabularSection);
          when(spyTabularSection.getName()).thenReturn(mdo.getName());
          mockAttributes(tabularSection, spyTabularSection, mdo.getName());
          tabularSections.add(spyTabularSection);
        });
        when(((TabularSectionOwner) spyMDO).getTabularSections()).thenReturn(tabularSections);
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

    assertThat(diagnostics)
      .hasSize(6)
      .noneMatch(diagnostic -> diagnostic.getMessage().contains("имя `Справочник.Справочник1"))
      .anyMatch(diagnostic -> diagnostic.getMessage().contains("имя `Документ.Документ1.Реквизит"))
      .anyMatch(diagnostic -> diagnostic.getMessage().contains("имя `Документ.Документ1.ТабличнаяЧасть"))
      .anyMatch(diagnostic -> diagnostic.getMessage().contains("имя `РегистрСведений.РегистрСведений1.Измерение"))
    ;
  }

  private static void mockAttributes(MD mdo, MD spyMDO, String parentName) {
    if (mdo instanceof AttributeOwner attributeOwner) {
      List<Attribute> attributes = new ArrayList<>();
      attributeOwner.getAllAttributes().forEach(attribute -> {
        var spyAttribute = spy(attribute);
        when(spyAttribute.getName()).thenReturn(parentName);
        attributes.add(spyAttribute);
      });
      when(((AttributeOwner) spyMDO).getAllAttributes()).thenReturn(attributes);
    }
  }
}
