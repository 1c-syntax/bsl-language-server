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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.utils.CaseInsensitivePattern;

import java.util.Map;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.VULNERABILITY,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.SUSPICIOUS
  },
  scope = DiagnosticScope.BSL

)
public class ExternalAppStartingDiagnostic extends AbstractFindMethodDiagnostic  {
  private static final String DEFAULT_PATTERN_STRING =
    "КомандаСистемы|System|ЗапуститьСистему|RunSystem|ЗапуститьПриложение|RunApp" +
    "|НачатьЗапускПриложения|BeginRunningApplication" +
    "|ЗапуститьПриложениеАсинх|RunAppAsync|ЗапуститьПрограмму|ОткрытьПроводник|ОткрытьФайл";
  private static final String PATTERN_STRING_FOR_NAVI =
    "|ПерейтиПоНавигационнойСсылке|GotoURL|ОткрытьНавигационнуюСсылку";
  private static final Pattern DEFAULT_PATTERN = CaseInsensitivePattern.compile(DEFAULT_PATTERN_STRING);
  private static final boolean CHECK_GOTO_URL = false;

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + CHECK_GOTO_URL
  )
  private boolean checkGotoUrl = CHECK_GOTO_URL;

  @DiagnosticParameter(
    type = String.class,
    defaultValue = DEFAULT_PATTERN_STRING
  )
  private String userPatternString = DEFAULT_PATTERN_STRING;

  public ExternalAppStartingDiagnostic() {
    super(DEFAULT_PATTERN);
  }

  @Override
  public void configure(Map<String, Object> configuration) {
    super.configure(configuration);
    var pattern = userPatternString;
    if (checkGotoUrl){
      pattern += PATTERN_STRING_FOR_NAVI;
    }
    setMethodPattern(CaseInsensitivePattern.compile(pattern));
  }
}
