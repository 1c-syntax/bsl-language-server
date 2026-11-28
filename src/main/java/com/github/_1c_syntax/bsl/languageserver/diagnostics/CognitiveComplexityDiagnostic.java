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

import com.github._1c_syntax.bsl.languageserver.context.computer.ComplexityData;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.CRITICAL,
  minutesToFix = 15,
  tags = {
    DiagnosticTag.BRAINOVERLOAD
  },
  extraMinForComplexity = 1
)
public class CognitiveComplexityDiagnostic extends AbstractComplexityDiagnostic {

  private static final int COMPLEXITY_THRESHOLD = 15;
  private static final boolean CHECK_MODULE_BODY = true;

  @DiagnosticParameter(
    type = Integer.class,
    defaultValue = "" + COMPLEXITY_THRESHOLD
  )
  private int complexityThreshold = COMPLEXITY_THRESHOLD;

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + CHECK_MODULE_BODY
  )
  private boolean checkModuleBody = CHECK_MODULE_BODY;

  @Override
  protected ComplexityData complexityData() {
    return documentContext.getCognitiveComplexityData();
  }

  @Override
  protected int complexityThreshold() {
    return complexityThreshold;
  }

  @Override
  protected boolean checkModuleBody() {
    return checkModuleBody;
  }

  @Override
  protected boolean relateMethodName() {
    return true;
  }
}
