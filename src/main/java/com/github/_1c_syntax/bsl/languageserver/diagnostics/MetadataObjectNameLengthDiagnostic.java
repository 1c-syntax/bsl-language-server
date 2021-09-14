/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.mdo.MDObject;
import org.eclipse.lsp4j.Range;

import java.util.Optional;

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

  @Override
  protected void check() {

    Optional<Range> range = Ranges.getFirstSignificantTokenRange(documentContext.getTokens());
    if (range.isEmpty()) {
      return;
    }

    documentContext
      .getMdObject()
      .map(MDObject::getName)
      .filter(this::checkName)
      .ifPresent(name -> diagnosticStorage.addDiagnostic(
        range.get(),
        info.getMessage(maxMetadataObjectNameLength))
      );
  }

  private boolean checkName(String name) {
    return name.length() > maxMetadataObjectNameLength;
  }
}
