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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;

import java.util.regex.Pattern;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.CRITICAL,
  minutesToFix = 1,
  scope = DiagnosticScope.BSL,
  tags = {
    DiagnosticTag.STANDARD
  }

)
public class WrongUseOfRollbackTransactionMethodDiagnostic extends AbstractFindMethodDiagnostic {

  private static final Pattern MESSAGE_PATTERN = CaseInsensitivePattern.compile(
    "ОтменитьТранзакцию|RollbackTransaction"
  );

  public WrongUseOfRollbackTransactionMethodDiagnostic() {
    super(MESSAGE_PATTERN);
  }

  @Override
  protected boolean checkGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {

    var parentNode = Trees.getAncestorByRuleIndex(ctx, BSLParser.RULE_exceptCodeBlock);

    if (parentNode == null) {
      return MESSAGE_PATTERN.matcher(ctx.methodName().getText()).matches();
    }

    var methodsList = Trees.findAllRuleNodes(parentNode, BSLParser.RULE_globalMethodCall).stream()
      .map(BSLParser.GlobalMethodCallContext.class::cast)
      .map(e -> e.methodName().getText())
      .collect(Collectors.toList());

    if (MESSAGE_PATTERN.matcher(ctx.methodName().getText()).matches()) {
      return methodsList.indexOf(ctx.methodName().getText()) != 0;
    }

    return false;
  }

  @Override
  protected boolean checkMethodCall(BSLParser.MethodCallContext ctx) {
    return false;
  }
}
