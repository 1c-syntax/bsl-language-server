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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.types.MDOType;
import com.github._1c_syntax.bsl.types.ModuleType;

import java.util.List;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 10,
  scope = DiagnosticScope.BSL,
  tags = {
    DiagnosticTag.STANDARD
  },
  canLocateOnProject = true
)
public class MetadataObjectNameLengthDiagnostic extends AbstractMetadataDiagnostic {

  private static final int MAX_METADATA_OBJECT_NAME_LENGTH = 80;

  private final LanguageServerConfiguration serverConfiguration;

  @DiagnosticParameter(
    type = Integer.class,
    defaultValue = "" + MAX_METADATA_OBJECT_NAME_LENGTH
  )
  private int maxMetadataObjectNameLength = MAX_METADATA_OBJECT_NAME_LENGTH;

  MetadataObjectNameLengthDiagnostic(LanguageServerConfiguration serverConfiguration) {
    super(List.of(MDOType.values()));
    this.serverConfiguration = serverConfiguration;
  }

  @Override
  protected void checkMetadata(MD mdo) {
    if (mdo.getName().length() > maxMetadataObjectNameLength) {
      addAttributeDiagnostic(mdo);
    }
  }

  @Override
  protected void check() {
    if (documentContext.getModuleType() == ModuleType.CommandModule
      || documentContext.getModuleType() == ModuleType.FormModule) {

      if (computeDiagnosticRange()) {
        documentContext.getMdObject().ifPresent(this::checkMetadata);
      }
    } else {
      super.check();
    }
  }

  private void addAttributeDiagnostic(MD attribute) {
    String mdoRef;
    if (serverConfiguration.getLanguage() == Language.RU) {
      mdoRef = attribute.getMdoReference().getMdoRefRu();
    } else {
      mdoRef = attribute.getMdoReference().getMdoRef();
    }
    addDiagnostic(info.getMessage(mdoRef, maxMetadataObjectNameLength));
  }
}
