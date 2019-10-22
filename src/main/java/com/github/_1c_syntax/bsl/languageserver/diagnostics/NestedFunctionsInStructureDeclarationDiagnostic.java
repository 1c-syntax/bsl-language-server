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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import com.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Leon Chagelishvili <lChagelishvily@gmail.com>
 */
@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  scope = DiagnosticScope.ALL,
  minutesToFix = 10,
  tags = {
    DiagnosticTag.BADPRACTICE
  }
)
public class NestedFunctionsInStructureDeclarationDiagnostic extends AbstractVisitorDiagnostic {

  private static final int MAX_VALUES_COUNT = 3;
  private Collection<ParseTree> nestedFunctionContext = new ArrayList<>();
  private final String relatedMessage = getResourceString("nestedFunctionRelatedMessage");
  private final ArrayList<Integer> methodCallRules = new ArrayList<>();

  @DiagnosticParameter(
    type = Integer.class,
    defaultValue = "" + MAX_VALUES_COUNT,
    description = "Допустимое количество параметров функции, используемой при инициализации структуры"
  )
  private int maxValuesCount = MAX_VALUES_COUNT;

  @Override
  public void configure(Map<String, Object> configuration) {
    if (configuration == null) {
      return;
    }
    maxValuesCount = (Integer) configuration.get("maxValuesCount");
  }

  public NestedFunctionsInStructureDeclarationDiagnostic() {
    methodCallRules.add(BSLParser.RULE_globalMethodCall);
    methodCallRules.add(BSLParser.RULE_methodCall);
  }

  @Override
  public ParseTree visitNewExpression(BSLParser.NewExpressionContext ctx) {

    nestedFunctionContext.clear();

    if (!(DiagnosticHelper.isStructureConstructor(ctx) || (DiagnosticHelper.isFixedStructureConstructor(ctx)))) {
      return ctx;
    }

    BSLParser.DoCallContext structureDoCallContext = ctx.doCall();
    if (structureDoCallContext == null) {
      return ctx;
    }

    List<BSLParser.CallParamContext> paramContexts = structureDoCallContext.callParamList().callParam();

    for (BSLParser.CallParamContext parameter : paramContexts) {
      BSLParserRuleContext methodCall = DiagnosticHelper.findFirstRuleNode(parameter, methodCallRules);
      if (!isEqualContexts(parameter, methodCall) || countOfMethodParameters(methodCall) <= maxValuesCount) {
        continue;
      }

      nestedFunctionContext.add(methodCall);

    }

    if (nestedFunctionContext.isEmpty()) {
      return ctx;
    }

    List<DiagnosticRelatedInformation> relatedInformation = new ArrayList<>();

    relatedInformation.add(RangeHelper.createRelatedInformation(
      documentContext.getUri(),
      RangeHelper.newRange(ctx),
      relatedMessage
    ));

    for (ParseTree methodContext : nestedFunctionContext) {

      if (methodContext instanceof BSLParser.MethodCallContext) {
        relatedInformation.add(RangeHelper.createRelatedInformation(
          documentContext.getUri(),
          RangeHelper.newRange((BSLParser.MethodCallContext) methodContext),
          relatedMessage
        ));
      }

      if (methodContext instanceof BSLParser.GlobalMethodCallContext) {
        relatedInformation.add(RangeHelper.createRelatedInformation(
          documentContext.getUri(),
          RangeHelper.newRange((BSLParser.GlobalMethodCallContext) methodContext),
          relatedMessage
        ));
      }
    }

    diagnosticStorage.addDiagnostic(ctx, relatedInformation);

    return super.visitNewExpression(ctx);
  }

  private boolean isEqualContexts(BSLParserRuleContext ctx1, BSLParserRuleContext ctx2) {

    if (ctx1 == null || ctx2 == null)
      return false;

    return ctx1.getTokens().equals(ctx2.getTokens());

  }

  private int countOfMethodParameters(BSLParserRuleContext methodContext) {

    int result = 0;

    if (methodContext instanceof BSLParser.MethodCallContext) {
      result = ((BSLParser.MethodCallContext) methodContext).doCall().callParamList().callParam().size();
    }

    if (methodContext instanceof BSLParser.GlobalMethodCallContext) {
      result = ((BSLParser.GlobalMethodCallContext) methodContext).doCall().callParamList().callParam().size();
    }

    return result;

  }
}

