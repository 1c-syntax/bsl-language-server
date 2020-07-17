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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.variable.scope.CodeFlowType;
import com.github._1c_syntax.bsl.languageserver.utils.variable.scope.ProgramScope;
import com.github._1c_syntax.bsl.languageserver.utils.variable.scope.VariableDefinition;
import com.github._1c_syntax.bsl.languageserver.utils.variable.types.V8Type;
import com.github._1c_syntax.bsl.languageserver.utils.variable.types.V8TypeFromPresentationSupplier;
import com.github._1c_syntax.bsl.languageserver.utils.variable.types.V8TypeHelper;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParser.AssignmentContext;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.CRITICAL,
  minutesToFix = 20,
  tags = {
    DiagnosticTag.PERFORMANCE
  }
)
public class CreateQueryInCycleDiagnostic extends AbstractVisitorDiagnostic {
  private static final Pattern EXECUTE_CALL_PATTERN = CaseInsensitivePattern.compile("Выполнить|Execute");
  private static final Pattern QUERY_BUILDER_PATTERN =
    CaseInsensitivePattern.compile("ПостроительЗапроса|QueryBuilder");
  private static final Pattern REPORT_BUILDER_PATTERN =
    CaseInsensitivePattern.compile("ПостроительОтчета|ReportBuilder");
  private static final Pattern QUERY_PATTERN = CaseInsensitivePattern.compile("Запрос|Query");

  private static final String GLOBAL_SCOPE = "GLOBAL_SCOPE";
  private static final String MODULE_SCOPE = "MODULE_SCOPE";

  private ProgramScope programScope = new ProgramScope();

  public CreateQueryInCycleDiagnostic(DiagnosticInfo info) {
    super(info);
    programScope.getTypeSuppliers().add(getTypeSupplier());
  }

  public static V8TypeFromPresentationSupplier getTypeSupplier() {
    return (typeName) -> {
      if (QUERY_PATTERN.matcher(typeName).matches()) {
        return Optional.of(NecessaryTypes.QUERY_TYPE);
      } else if (REPORT_BUILDER_PATTERN.matcher(typeName).matches()) {
        return Optional.of(NecessaryTypes.REPORT_BUILDER_TYPE);
      } else if (QUERY_BUILDER_PATTERN.matcher(typeName).matches()) {
        return Optional.of(NecessaryTypes.QUERY_BUILDER_TYPE);
      }
      return Optional.empty();
    };
  }

  private static BSLParserRuleContext getProperErrorContext(BSLParser.AccessCallContext ctx) {
    BSLParserRuleContext errorContext = null;
    BSLParserRuleContext parent = (BSLParserRuleContext) ctx.getParent();
    if (parent instanceof BSLParser.CallStatementContext) {
      errorContext = parent;
    } else if (parent instanceof BSLParser.ModifierContext) {
      BSLParser.ModifierContext callModifier = (BSLParser.ModifierContext) parent;
      errorContext = (BSLParserRuleContext) callModifier.getParent();
    }
    return errorContext;
  }

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {
    programScope.enterScope(GLOBAL_SCOPE);
    ParseTree result = super.visitFile(ctx);
    programScope = null;
    return result;
  }

  @Override
  public ParseTree visitFileCodeBlock(BSLParser.FileCodeBlockContext ctx) {
    programScope.enterScope(MODULE_SCOPE);
    ParseTree result = super.visitFileCodeBlock(ctx);
    programScope.leaveScope();
    return result;
  }

  @Override
  public ParseTree visitProcedure(BSLParser.ProcedureContext ctx) {
    programScope.enterScope(ctx.procDeclaration().subName().getText());
    ParseTree result = super.visitProcedure(ctx);
    programScope.leaveScope();
    return result;
  }

  @Override
  public ParseTree visitFunction(BSLParser.FunctionContext ctx) {
    programScope.enterScope(ctx.funcDeclaration().subName().getText());
    ParseTree result = super.visitFunction(ctx);
    programScope.leaveScope();
    return result;
  }

  @Override
  public ParseTree visitIfStatement(BSLParser.IfStatementContext ctx) {
    if (ctx.ifBranch() != null) {
      visitExpression(ctx.ifBranch().expression());
      programScope.enterFlowScope(CodeFlowType.CONDITIONAL);
      visitCodeBlock(ctx.ifBranch().codeBlock());
      programScope.leaveFlowScope();
    }
    for (var branch : ctx.elsifBranch()) {
      visitExpression(branch.expression());
      programScope.enterFlowScope(CodeFlowType.CONDITIONAL);
      visitCodeBlock(branch.codeBlock());
      programScope.leaveFlowScope();
    }
    if (ctx.elseBranch() != null) {
      programScope.enterFlowScope(CodeFlowType.CONDITIONAL);
      visitCodeBlock(ctx.elseBranch().codeBlock());
      programScope.leaveFlowScope();
    }

    return null;
  }

  @Override
  public ParseTree visitAssignment(AssignmentContext ctx) {
    if (ctx.expression() == null) {
      return super.visitAssignment(ctx);
    }

    Set<V8Type> types = V8TypeHelper.getTypesFromExpressionContext(ctx.expression(), programScope);
    if (types == null) {
      return super.visitAssignment(ctx);
    }

    String variableName = ctx.lValue().getText();

    VariableDefinition currentVariable = programScope.getVariableByName(variableName)
      .orElseGet(() -> programScope.addVariable(VariableDefinition.fromLValue(ctx.lValue())));

    if (programScope.codeFlowInConditionalBlock()) {
      currentVariable.addAll(types);
    } else {
      currentVariable.replaceAll(types);
    }
    return super.visitAssignment(ctx);
  }

  @Override
  public ParseTree visitCodeBlock(BSLParser.CodeBlockContext ctx) {
    return Optional.ofNullable(ctx)
      .map(super::visitCodeBlock).orElse(null);
  }

  @Override
  public ParseTree visitExpression(BSLParser.ExpressionContext ctx) {
    return Optional.ofNullable(ctx)
      .map(super::visitExpression).orElse(null);
  }

  @Override
  public ParseTree visitAccessCall(BSLParser.AccessCallContext ctx) {
    if (!EXECUTE_CALL_PATTERN.matcher(ctx.methodCall().methodName().getText()).matches()) {
      return super.visitAccessCall(ctx);
    }
    if (!programScope.codeFlowInCycle()) {
      return super.visitAccessCall(ctx);
    }

    BSLParserRuleContext errorContext = getProperErrorContext(ctx);
    if (errorContext != null) {
      String variableName = V8TypeHelper.getVariableNameFromAccessCallContext(ctx);
      programScope.getVariableByName(variableName)
        .filter(Predicate.not((VariableDefinition definition) -> Collections.disjoint(definition.getTypes(), NecessaryTypes.FULL_COLLECTION)))
        .ifPresent(e -> diagnosticStorage.addDiagnostic(errorContext));
    }
    return super.visitAccessCall(ctx);
  }

  @Override
  public ParseTree visitForEachStatement(BSLParser.ForEachStatementContext ctx) {
    visitExpression(ctx.expression());
    programScope.enterFlowScope(CodeFlowType.CYCLE);
    visitCodeBlock(ctx.codeBlock());
    programScope.leaveFlowScope();
    return ctx;
  }

  @Override
  public ParseTree visitWhileStatement(BSLParser.WhileStatementContext ctx) {
    programScope.enterFlowScope(CodeFlowType.CYCLE);
    ParseTree result = super.visitWhileStatement(ctx);
    programScope.leaveFlowScope();
    return result;
  }

  @Override
  public ParseTree visitForStatement(BSLParser.ForStatementContext ctx) {

    ctx.expression()
      .forEach(this::visitExpression);
    programScope.enterFlowScope(CodeFlowType.CYCLE);
    visitCodeBlock(ctx.codeBlock());
    programScope.leaveFlowScope();
    return ctx;
  }

  enum NecessaryTypes implements V8Type {
    REPORT_BUILDER_TYPE("ReportBuilder"), QUERY_BUILDER_TYPE("QueryBuilder"), QUERY_TYPE("Query");
    public static Set<NecessaryTypes> FULL_COLLECTION = Set.of(QUERY_TYPE, REPORT_BUILDER_TYPE, QUERY_BUILDER_TYPE);
    String name;

    NecessaryTypes(String name) {
      this.name = name;
    }

    @Override
    public String getName() {
      return this.name;
    }
  }

}
