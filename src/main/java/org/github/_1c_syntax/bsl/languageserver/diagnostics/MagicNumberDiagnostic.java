/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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
package org.github._1c_syntax.bsl.languageserver.diagnostics;

import org.antlr.v4.runtime.tree.ParseTree;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import org.github._1c_syntax.bsl.parser.BSLParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 1
)
public class MagicNumberDiagnostic extends AbstractVisitorDiagnostic {

  private static final String DEFAULT_AUTHORIZED_NUMBERS = "-1,0,1";

  @DiagnosticParameter(
    type = String.class,
    defaultValue = "" + DEFAULT_AUTHORIZED_NUMBERS,
    description = "Список разрешенных чисел через запятую. Например: -1,0,1,60"
  )
  private List<String> authorizedNumbers = new ArrayList<>(Arrays.asList(DEFAULT_AUTHORIZED_NUMBERS.split(",")));

  private boolean isExcluded(String s) {
    for (String elem : this.authorizedNumbers) {
      if (s.compareTo(elem) == 0) {
        return true;
      }
    }

    return false;
  }

  private boolean isNumericExpression(BSLParser.ExpressionContext expression) {
    return (expression.getChildCount() <= 1);
  }

  @Override
  public void configure(Map<String, Object> configuration) {
    if (configuration == null) {
      return;
    }

    String authorizedNumbersString = (String) configuration.get("authorizedNumbers");
    for (String s : authorizedNumbersString.split(",")) {
      this.authorizedNumbers.add(s.trim());
    }
  }

  public ParseTree visitNumeric(BSLParser.NumericContext ctx) {
    String checked = ctx.getText();

    if(checked == null || isExcluded(checked)) {
      return super.visitChildren(ctx);
    }

    BSLParser.ExpressionContext expression = (BSLParser.ExpressionContext) ctx.getParent().getParent().getParent();

    if (isNumericExpression(expression)) {
      return super.visitChildren(ctx);
    }

    diagnosticStorage.addDiagnostic(ctx.stop, getDiagnosticMessage(checked));
    return super.visitChildren(ctx);
  }

}
