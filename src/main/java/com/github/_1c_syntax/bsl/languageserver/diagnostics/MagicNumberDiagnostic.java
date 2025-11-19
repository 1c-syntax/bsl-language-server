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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.tree.ParseTree;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.BADPRACTICE
  }
)
public class MagicNumberDiagnostic extends AbstractMagicValueDiagnostic {

  private static final String DEFAULT_AUTHORIZED_NUMBERS = "-1,0,1";
  private static final boolean DEFAULT_ALLOW_MAGIC_NUMBER = true;

  @DiagnosticParameter(
    type = String.class,
    defaultValue = DEFAULT_AUTHORIZED_NUMBERS
  )
  private final List<String> authorizedNumbers = new ArrayList<>(Arrays.asList(DEFAULT_AUTHORIZED_NUMBERS.split(",")));

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + DEFAULT_ALLOW_MAGIC_NUMBER
  )
  private boolean allowMagicIndexes = DEFAULT_ALLOW_MAGIC_NUMBER;

  @Override
  public void configure(Map<String, Object> configuration) {
    DiagnosticHelper.configureDiagnostic(this, configuration, "allowMagicIndexes");

    this.authorizedNumbers.clear();

    var authorizedNumbersString =
      (String) configuration.getOrDefault("authorizedNumbers", DEFAULT_AUTHORIZED_NUMBERS);
    for (String s : authorizedNumbersString.split(",")) {
      this.authorizedNumbers.add(s.trim());
    }
  }

  private static boolean isNumericExpression(BSLParser.ExpressionContext expression) {
    return (expression.getChildCount() <= 1);
  }

  private static boolean insideCallParam(BSLParser.ExpressionContext expression) {
    return expression.getParent() instanceof BSLParser.CallParamContext;
  }

  @Override
  public ParseTree visitNumeric(BSLParser.NumericContext ctx) {
    var checked = ctx.getText();

    if (checked != null && isAllowed(checked)) {
      var current = ctx.getParent();
      var isDefaultValue = false;
      while (current != null) {
        if (current instanceof BSLParser.DefaultValueContext) {
          isDefaultValue = true;
          break;
        }
        current = current.getParent();
      }
      
      if (!isDefaultValue && isWrongExpression(ctx, ctx.getParent())) {
        diagnosticStorage.addDiagnostic(ctx.stop, info.getMessage(checked));
      }
    }
    return defaultResult();
  }

  private boolean isAllowed(String s) {
    for (String elem : this.authorizedNumbers) {
      if (s.compareTo(elem) == 0) {
        return false;
      }
    }
    return true;
  }

  private boolean isWrongExpression(BSLParser.NumericContext ctx, BSLParserRuleContext numericContextParent) {
    if (mayBeNumberAccess(ctx)) {
      return true;
    }
    
    var expression = getExpression(numericContextParent);
    if (expression.isPresent() && insideStructureOrCorrespondence(expression.get())) {
      return false;
    }
    if (expression.isPresent() && insideReturnStatement(expression.get())) {
      return true;
    }
    return expression
      .filter((BSLParser.ExpressionContext expr) ->
        (!isNumericExpression(expr) || insideCallParam(expr)))
      .isPresent();
  }

  private boolean mayBeNumberAccess(BSLParser.NumericContext ctx) {
    if (allowMagicIndexes) {
      return false;
    }
    BSLParserRuleContext current = ctx.getParent();
    while (current != null) {
      if (current instanceof BSLParser.AccessIndexContext) {
        return true;
      }
      current = current.getParent();
    }
    return false;
  }
}
