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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 2,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.BRAINOVERLOAD,
    DiagnosticTag.BADPRACTICE
  }
)
public class NestedFunctionInParametersDiagnostic extends AbstractVisitorDiagnostic {

  @Override
  public ParseTree visitMethodCall(BSLParser.MethodCallContext ctx) {
    checkMethodCall(ctx, ctx.doCall(), ctx.methodName());
    return super.visitMethodCall(ctx);
  }

  @Override
  public ParseTree visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
    checkMethodCall(ctx, ctx.doCall(), ctx.methodName());
    return super.visitGlobalMethodCall(ctx);
  }

  @Override
  public ParseTree visitNewExpression(BSLParser.NewExpressionContext ctx) {

    if (findNestedCall(ctx, ctx.doCall())) {
      if (ctx.typeName() != null) {
        diagnosticStorage.addDiagnostic(
          ctx.typeName(),
          info.getMessage(
            info.getResourceString("diagnosticMessageConstructor"),
            ctx.typeName().getText()));
      } else { // для констукторов через параметр New
        diagnosticStorage.addDiagnostic(
          ctx.getStart(),
          info.getResourceString("diagnosticMessageWithoutName",
            info.getResourceString("diagnosticMessageConstructor")));
      }
    }

    return super.visitNewExpression(ctx);
  }

  private static boolean findNestedCall(BSLParserRuleContext ctx, BSLParser.DoCallContext ctxDoCall) {
    if (ctxDoCall == null
      || ctxDoCall.callParamList() == null) {
      return false;
    }

    // если есть параметры и вызов не в одной строке, то найдем вызовы методов
    return !ctxDoCall.callParamList().isEmpty()
      && ctx.getStart().getLine() != ctx.getStop().getLine()
      && Trees.nodeContains(ctx, ctxDoCall, BSLParser.RULE_doCall);
  }

  private void checkMethodCall(BSLParserRuleContext ctx,
                               BSLParser.DoCallContext ctxDoCall,
                               BSLParser.MethodNameContext ctxMethodName) {
    if (findNestedCall(ctx, ctxDoCall)) {
      diagnosticStorage.addDiagnostic(ctxMethodName,
        info.getMessage(
          info.getResourceString("diagnosticMessageMethod"),
          ctxMethodName.getText()));
    }
  }
}
