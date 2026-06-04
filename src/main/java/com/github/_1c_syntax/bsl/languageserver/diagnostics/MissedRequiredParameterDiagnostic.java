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

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.types.symbol.ConstructorCallSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
  private final ReferenceResolver referenceResolver;
  private final Map<Range, MethodCall> calls = new HashMap<>();

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {
    super.visitFile(ctx);
    for (var reference : referenceIndex.getReferencesFrom(documentContext.getUri(), SymbolKind.Method)) {
      var call = calls.get(reference.selectionRange());
      if (call != null) {
        checkMethod((MethodSymbol) reference.symbol(), call);
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

  @Override
  public ParseTree visitNewExpression(BSLParser.NewExpressionContext ctx) {
    checkConstructorCall(ctx);
    return super.visitNewExpression(ctx);
  }

  private void appendMethodCall(Token methodName, BSLParser.@Nullable DoCallContext doCallContext, ParserRuleContext node) {
    var methodCall = new MethodCall();
    methodCall.arguments = arguments(doCallContext);
    methodCall.range = Ranges.create(node);
    calls.put(Ranges.create(methodName), methodCall);
  }

  private void checkConstructorCall(BSLParser.NewExpressionContext ctx) {
    var typeName = ctx.typeName();
    if (typeName == null || typeName.IDENTIFIER() == null) {
      return;
    }

    var signatures = referenceResolver.findReference(documentContext.getUri(), typeNamePosition(typeName))
      .map(reference -> parameterSignatures(reference.symbol()))
      .orElseGet(List::of);
    if (signatures.isEmpty()) {
      return;
    }

    var arguments = arguments(ctx.doCall());

    List<String> bestMissedParameters = null;
    for (var signature : signatures) {
      var missedParameters = missedParameters(signature, arguments);
      if (missedParameters.isEmpty()) {
        return;
      }
      if (bestMissedParameters == null || missedParameters.size() < bestMissedParameters.size()) {
        bestMissedParameters = missedParameters;
      }
    }

    addDiagnostic(Ranges.create(ctx), bestMissedParameters);
  }

  private void checkMethod(MethodSymbol methodDefinition, MethodCall callInfo) {
    var signature = signature(methodDefinition);
    var missedParameters = missedParameters(signature, callInfo.arguments);
    if (!missedParameters.isEmpty()) {
      addDiagnostic(callInfo.range, missedParameters);
    }
  }

  private void addDiagnostic(Range range, List<String> missedParameters) {
    var message = info.getMessage("'%s'".formatted(String.join("', '", missedParameters)));
    diagnosticStorage.addDiagnostic(range, message);
  }

  private static List<String> missedParameters(List<Parameter> signature, Boolean[] arguments) {
    var missedParameters = new ArrayList<String>();
    for (var i = 0; i < signature.size(); i++) {
      var parameter = signature.get(i);
      if (parameter.optional()) {
        continue;
      }
      if (arguments.length <= i || !arguments[i]) {
        missedParameters.add(parameter.name());
      }
    }
    return missedParameters;
  }

  private static List<List<Parameter>> parameterSignatures(Symbol symbol) {
    if (symbol instanceof MethodSymbol methodSymbol) {
      return List.of(signature(methodSymbol));
    }
    if (symbol instanceof ConstructorCallSymbol constructorCall) {
      return constructorCall.getConstructors().stream()
        .map(constructor -> constructor.parameters().stream()
          .map(parameter -> new Parameter(parameter.name(), parameter.optional()))
          .toList())
        .toList();
    }
    return List.of();
  }

  private static List<Parameter> signature(MethodSymbol methodSymbol) {
    return methodSymbol.getParameters().stream()
      .map(parameter -> new Parameter(parameter.getName(), parameter.isOptional()))
      .toList();
  }

  private static Boolean[] arguments(BSLParser.@Nullable DoCallContext doCallContext) {
    if (doCallContext == null) {
      return new Boolean[0];
    }
    var parameters = doCallContext.callParamList().callParam();
    var arguments = new Boolean[parameters.size()];
    for (var i = 0; i < arguments.length; i++) {
      arguments[i] = parameters.get(i).expression() != null;
    }
    return arguments;
  }

  private static Position typeNamePosition(BSLParser.TypeNameContext typeName) {
    var token = typeName.IDENTIFIER().getSymbol();
    // Token start is sufficient: the resolver looks for a position within the identifier.
    return new Position(token.getLine() - 1, token.getCharPositionInLine());
  }

  private static class MethodCall {
    Boolean[] arguments;
    Range range;
  }

  private record Parameter(String name, boolean optional) {
  }
}
