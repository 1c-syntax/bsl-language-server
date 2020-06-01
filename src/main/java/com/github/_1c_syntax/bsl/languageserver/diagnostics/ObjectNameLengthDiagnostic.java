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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.mdclasses.mdo.MDObjectBase;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.STANDARD
  }

)
public class ObjectNameLengthDiagnostic extends AbstractDiagnostic {

  private static final int MAX_OBJECT_NAME_LENGTH = 80;

  @DiagnosticParameter(
    type = Integer.class,
    defaultValue = "" + MAX_OBJECT_NAME_LENGTH
  )
  private int maxObjectNameLength = MAX_OBJECT_NAME_LENGTH;

  public ObjectNameLengthDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  protected void check() {
    documentContext
      .getMdObject()
      .map(MDObjectBase::getName)
      .filter(this::checkName)
      .ifPresent(objectName -> diagnosticStorage.addDiagnostic(
        documentContext.getTokensFromDefaultChannel().get(0),
        info.getMessage(maxObjectNameLength)
      ));
  }

  private boolean checkName(String name) {
    return name.length() > maxObjectNameLength;
  }
}
