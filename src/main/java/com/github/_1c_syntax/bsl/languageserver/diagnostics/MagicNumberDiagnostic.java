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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.BADPRACTICE
  }
)
@Slf4j
public class MagicNumberDiagnostic extends AbstractVisitorDiagnostic {

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

    var authorizedNumbersValue = configuration.getOrDefault("authorizedNumbers", DEFAULT_AUTHORIZED_NUMBERS);
    
    try {
      if (authorizedNumbersValue instanceof String) {
        // Handle string format: "-1,0,1"
        String authorizedNumbersString = (String) authorizedNumbersValue;
        for (String s : authorizedNumbersString.split(",")) {
          this.authorizedNumbers.add(s.trim());
        }
      } else if (authorizedNumbersValue instanceof List<?>) {
        // Handle list format: ["-1", "0", "1"] 
        List<?> authorizedNumbersList = (List<?>) authorizedNumbersValue;
        for (Object item : authorizedNumbersList) {
          if (item instanceof String) {
            this.authorizedNumbers.add(((String) item).trim());
          } else {
            // Convert to string if possible
            this.authorizedNumbers.add(String.valueOf(item).trim());
          }
        }
      } else {
        // Unsupported type - log warning and use defaults
        LOGGER.warn("Invalid configuration for authorizedNumbers parameter. Expected String or List, got {}. Using default value: {}", 
                    authorizedNumbersValue.getClass().getSimpleName(), DEFAULT_AUTHORIZED_NUMBERS);
        for (String s : DEFAULT_AUTHORIZED_NUMBERS.split(",")) {
          this.authorizedNumbers.add(s.trim());
        }
      }
    } catch (Exception e) {
      // Catch any unexpected errors during configuration and use defaults
      LOGGER.warn("Error configuring authorizedNumbers parameter: {}. Using default value: {}", e.getMessage(), DEFAULT_AUTHORIZED_NUMBERS);
      for (String s : DEFAULT_AUTHORIZED_NUMBERS.split(",")) {
        this.authorizedNumbers.add(s.trim());
      }
    }
  }

  private static Optional<BSLParser.ExpressionContext> getExpression(BSLParserRuleContext ctx) {
    return Optional.of(ctx)
      .filter(context -> context.getChildCount() == 1)
      .map(BSLParserRuleContext::getParent)
      .filter(context -> context.getChildCount() == 1)
      .map(BSLParserRuleContext::getParent)
      .filter(BSLParser.ExpressionContext.class::isInstance)
      .map(BSLParser.ExpressionContext.class::cast);
  }

  private static boolean isNumericExpression(BSLParser.ExpressionContext expression) {
    return (expression.getChildCount() <= 1);
  }

  private static boolean insideCallParam(BSLParser.ExpressionContext expression) {
    return expression.getParent() instanceof BSLParser.CallParamContext;
  }

  @Override
  public ParseTree visitNumeric(BSLParser.NumericContext ctx) {
    String checked = ctx.getText();

    if (checked != null && isAllowed(checked)) {
      final var parent = ctx.getParent();
      if (parent.getParent() instanceof BSLParser.DefaultValueContext || isWrongExpression(parent)) {
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

  private boolean isWrongExpression(BSLParserRuleContext numericContextParent) {
    return getExpression(numericContextParent)
      .filter((BSLParser.ExpressionContext expression) ->
        (!isNumericExpression(expression) || mayBeNumberAccess(expression) || insideCallParam(expression)))
      .isPresent();
  }

  private boolean mayBeNumberAccess(BSLParser.ExpressionContext expression) {
    return !allowMagicIndexes && expression.getParent() instanceof BSLParser.AccessIndexContext;
  }
}
