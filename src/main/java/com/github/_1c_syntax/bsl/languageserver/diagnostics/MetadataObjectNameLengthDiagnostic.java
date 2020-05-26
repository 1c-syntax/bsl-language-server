/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.mdclasses.mdo.MDObjectBase;
import org.eclipse.lsp4j.Range;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 10,
  scope = DiagnosticScope.BSL,
  tags = {
    DiagnosticTag.STANDARD
  }

)
public class MetadataObjectNameLengthDiagnostic extends AbstractDiagnostic {

  private static final int MAX_METADATA_OBJECT_NAME_LENGTH = 80;

  @DiagnosticParameter(
    type = Integer.class,
    defaultValue = "" + MAX_METADATA_OBJECT_NAME_LENGTH
  )
  private int maxMetadataObjectNameLength = MAX_METADATA_OBJECT_NAME_LENGTH;

  public MetadataObjectNameLengthDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  protected void check() {
    documentContext
      .getMdObject()
      .map(MDObjectBase::getName)
      .filter(this::checkName)
      .ifPresent(this::addDiagnostic);
  }

  private boolean checkName(String name) {
    return name.length() > maxMetadataObjectNameLength;
  }

  private void addDiagnostic(String name) {

    var tokens = documentContext.getTokens();
    String message = info.getMessage(maxMetadataObjectNameLength);
    if (tokens.isEmpty()) {
      diagnosticStorage.addDiagnostic(new Range(), message);
    } else {
      diagnosticStorage.addDiagnostic(tokens.get(0), message);
    }
  }
}
