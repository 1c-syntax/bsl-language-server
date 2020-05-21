/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodDescription;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.MdoRefBuilder;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 3,
  tags = {
    DiagnosticTag.DEPRECATED,
    DiagnosticTag.DESIGN
  }
)
public class DeprecatedMethodCallDiagnostic extends AbstractVisitorDiagnostic {
  private static final Set<ModuleType> DEFAULT_MODULE_TYPES =
    EnumSet.of(ModuleType.ManagerModule, ModuleType.CommonModule);

  public DeprecatedMethodCallDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  public ParseTree visitCallStatement(BSLParser.CallStatementContext ctx) {

    if (currentMethodIsDeprecated(ctx, documentContext)) {
      return super.visitCallStatement(ctx);
    }

    String mdoRef = MdoRefBuilder.getMdoRef(documentContext, ctx);
    if (mdoRef.isEmpty()) {
      return super.visitCallStatement(ctx);
    }

    getMethodName(ctx).ifPresent(methodName -> checkDeprecatedCall(mdoRef, methodName));

    return super.visitCallStatement(ctx);
  }

  @Override
  public ParseTree visitComplexIdentifier(BSLParser.ComplexIdentifierContext ctx) {

    if (currentMethodIsDeprecated(ctx, documentContext)) {
      return super.visitComplexIdentifier(ctx);
    }

    String mdoRef = MdoRefBuilder.getMdoRef(documentContext, ctx);
    if (mdoRef.isEmpty()) {
      return super.visitComplexIdentifier(ctx);
    }

    getMethodName(ctx).ifPresent(methodName -> checkDeprecatedCall(mdoRef, methodName));

    return super.visitComplexIdentifier(ctx);
  }

  @Override
  public ParseTree visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
    if (currentMethodIsDeprecated(ctx, documentContext)) {
      return super.visitGlobalMethodCall(ctx);
    }

    var methodName = ctx.methodName().getStart();
    var methodNameText = methodName.getText();

    documentContext.getSymbolTree().getMethods().stream()
      .filter(methodSymbol -> methodSymbol.isDeprecated()
        && methodSymbol.getName().equalsIgnoreCase(methodNameText))
      .findAny()
      .ifPresent(methodSymbol -> fireIssue(methodSymbol, methodName));

    return super.visitGlobalMethodCall(ctx);
  }

  private static boolean currentMethodIsDeprecated(BSLParserRuleContext ctx, DocumentContext documentContext) {
    return Optional.ofNullable(Trees.getRootParent(ctx, BSLParser.RULE_sub))
      .flatMap(sub -> documentContext.getSymbolTree().getMethodSymbol(sub))
      .map(MethodSymbol::isDeprecated)
      .orElse(false);
  }

  private void checkDeprecatedCall(String mdoRef, Token methodName) {
    var documentContexts = documentContext.getServerContext().getDocuments(mdoRef);
    String methodNameText = methodName.getText();

    documentContexts.entrySet().stream()
      .filter(entry -> DEFAULT_MODULE_TYPES.contains(entry.getKey()))
      .map(Map.Entry::getValue)
      .map(DocumentContext::getSymbolTree)
      .flatMap(symbolTree -> symbolTree.getMethods().stream())
      .filter(methodSymbol -> methodSymbol.isDeprecated()
        && methodSymbol.getName().equalsIgnoreCase(methodNameText))
      .findAny()
      .ifPresent(methodSymbol -> fireIssue(methodSymbol, methodName));
  }

  private void fireIssue(MethodSymbol methodSymbol, Token methodName) {
    var methodNameText = methodName.getText();

    var deprecationInfo = methodSymbol.getDescription()
      .map(MethodDescription::getDeprecationInfo)
      .orElse("");

    diagnosticStorage.addDiagnostic(methodName, info.getMessage(methodNameText, deprecationInfo));
  }

  private static Optional<Token> getMethodName(BSLParser.CallStatementContext ctx) {
    var modifiers = ctx.modifier();
    var methodName = getMethodName(ctx.accessCall());

    if (modifiers.isEmpty()) {
      return methodName;
    } else {
      return getMethodName(modifiers).or(() -> methodName);
    }
  }

  private static Optional<Token> getMethodName(BSLParser.AccessCallContext ctx) {
    return Optional.of(ctx.methodCall().methodName().getStart());
  }

  private static Optional<Token> getMethodName(BSLParser.ComplexIdentifierContext ctx) {
    return getMethodName(ctx.modifier());
  }

  private static Optional<Token> getMethodName(List<? extends BSLParser.ModifierContext> modifiers) {
    return modifiers.stream()
      .map(BSLParser.ModifierContext::accessCall)
      .filter(Objects::nonNull)
      .map(DeprecatedMethodCallDiagnostic::getMethodName)
      .findFirst()
      .orElse(Optional.empty());
  }
}
