/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.ERROR
  }

)
@RequiredArgsConstructor
public class MissedRequiredParameterDiagnostic extends AbstractVisitorDiagnostic {

  private final ReferenceIndex referenceIndex;
  private final Map<Range, MethodCall> calls = new HashMap<>();

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {
    super.visitFile(ctx);
    for (var reference : referenceIndex.getReferencesFrom(documentContext.getUri(), SymbolKind.Method)) {
      var call = calls.get(reference.getSelectionRange());
      if (call != null) {
        checkMethod((MethodSymbol) reference.getSymbol(), call);
      }
    }
    return ctx;
  }

  @Override
  public ParseTree visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
    var methodName = ctx.methodName().IDENTIFIER().getText();
    if (documentContext.getSymbolTree().getMethodSymbol(methodName).isPresent()) {
      appendMethodCall(ctx.methodName().getStart(), ctx.doCall(), ctx);
    }

    return super.visitGlobalMethodCall(ctx);
  }

  @Override
  public ParseTree visitMethodCall(BSLParser.MethodCallContext ctx) {
    appendMethodCall(ctx.methodName().getStart(), ctx.doCall(), ctx);
    return super.visitMethodCall(ctx);
  }

  private void appendMethodCall(Token methodName, BSLParser.DoCallContext doCallContext, BSLParserRuleContext node) {
    var parameters = doCallContext.callParamList().callParam();
    var methodCall = new MethodCall();
    methodCall.parameters = new Boolean[parameters.size()];

    for (var i = 0; i < methodCall.parameters.length; i++) {
      methodCall.parameters[i] = parameters.get(i).expression() != null;
    }

    methodCall.range = Ranges.create(node);
    calls.put(Ranges.create(methodName), methodCall);
  }

  private void checkMethod(MethodSymbol methodDefinition, MethodCall callInfo) {
    var callParametersCount = callInfo.parameters.length;

    var missedParameters = new ArrayList<String>();
    for (var i = 0; i < methodDefinition.getParameters().size(); i++) {
      var methodParameter = methodDefinition.getParameters().get(i);
      if (methodParameter.isOptional()) {
        continue;
      }

      if (callParametersCount <= i || !callInfo.parameters[i]) {
        missedParameters.add(methodParameter.getName());
      }
    }
    if (!missedParameters.isEmpty()) {
      var message = info.getMessage(String.format("'%s'", String.join("', '", missedParameters)));
      diagnosticStorage.addDiagnostic(callInfo.range, message);
    }
  }

  private static class MethodCall {
    Boolean[] parameters;
    Range range;
  }
}