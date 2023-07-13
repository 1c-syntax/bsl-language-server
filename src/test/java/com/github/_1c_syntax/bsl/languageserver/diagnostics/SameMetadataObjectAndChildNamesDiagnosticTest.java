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

import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.mdclasses.mdo.AbstractMDObjectBase;
import com.github._1c_syntax.mdclasses.mdo.AbstractMDObjectComplex;
import com.github._1c_syntax.mdclasses.mdo.attributes.AbstractMDOAttribute;
import com.github._1c_syntax.mdclasses.mdo.attributes.TabularSection;
import com.github._1c_syntax.mdclasses.mdo.metadata.AttributeType;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    var mdObjectBase = spy(context.getConfiguration().getChildren().stream()
      .filter(mdo -> mdo.getMdoReference().getMdoRefRu().equalsIgnoreCase("Справочник.Справочник1"))
      .findFirst()
      .get());

    when(documentContext.getModuleType()).thenReturn(ModuleType.ManagerModule);

    List<AbstractMDOAttribute> attributes = new ArrayList<>();

    var attribute = spy(((AbstractMDObjectComplex) mdObjectBase).getAttributes().stream()
      .filter(mdo -> mdo.getAttributeType() == AttributeType.ATTRIBUTE)
      .findFirst()
      .get());
    when(attribute.getName()).thenReturn("Справочник1");

    attributes.add(attribute);

    var tabularSection = spy(((AbstractMDObjectComplex) mdObjectBase).getAttributes().stream()
      .filter(mdo -> mdo.getAttributeType() == AttributeType.TABULAR_SECTION)
      .map(TabularSection.class::cast)
      .findFirst()
      .get());

    when(tabularSection.getName()).thenReturn("Тара");
    attributes.add(tabularSection);

    var tabAttribute = spy(tabularSection.getAttributes().get(0));
    when(tabAttribute.getName()).thenReturn("Тара");
    when(tabularSection.getAttributes()).thenReturn(List.of(tabAttribute));

    when(((AbstractMDObjectComplex) mdObjectBase).getAttributes()).thenReturn(attributes);
    when(documentContext.getMdObject()).thenReturn(Optional.of(mdObjectBase));

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

    Set<AbstractMDObjectBase> children = new HashSet<>();

    context.getConfiguration().getChildren().forEach(mdo -> {
      if (mdo instanceof AbstractMDObjectComplex) {
        var spyMDO = spy(mdo);
        List<AbstractMDOAttribute> attributes = new ArrayList<>();
        ((AbstractMDObjectComplex) mdo).getAttributes().forEach(mdoAttribute -> {
          var attribute = spy(mdoAttribute);
          when(attribute.getName()).thenReturn(mdo.getName());
          attributes.add(attribute);
        });
        when(((AbstractMDObjectComplex) spyMDO).getAttributes()).thenReturn(attributes);

        if (!mdo.getName().equalsIgnoreCase("Справочник1")) {
          when(((AbstractMDObjectComplex) spyMDO).getModules()).thenReturn(Collections.emptyList());
        }

        children.add(spyMDO);
      }
    });

    var configuration = spy(context.getConfiguration());
    when(configuration.getChildren()).thenReturn(children);
    var context = spy(documentContext.getServerContext());
    when(context.getConfiguration()).thenReturn(configuration);
    when(documentContext.getServerContext()).thenReturn(context);
    when(documentContext.getModuleType()).thenReturn(ModuleType.SessionModule);

    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    assertThat(diagnostics)
      .hasSize(5)
      .noneMatch(diagnostic -> diagnostic.getMessage().contains("имя `Справочник.Справочник1"))
      .anyMatch(diagnostic -> diagnostic.getMessage().contains("имя `Документ.Документ1.Реквизит"))
      .anyMatch(diagnostic -> diagnostic.getMessage().contains("имя `Документ.Документ1.ТабличнаяЧасть"))
      .anyMatch(diagnostic -> diagnostic.getMessage().contains("имя `РегистрСведений.РегистрСведений1.Измерение"))
    ;
  }
}
