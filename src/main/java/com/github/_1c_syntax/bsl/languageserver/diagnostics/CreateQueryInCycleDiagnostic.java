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
import com.github._1c_syntax.bsl.languageserver.utils.V8BasicType;
import com.github._1c_syntax.bsl.languageserver.utils.V8Type;
import com.github._1c_syntax.bsl.languageserver.utils.V8TypeFromPresentationSupplier;
import com.github._1c_syntax.bsl.languageserver.utils.V8TypeFromVariableSupplier;
import com.github._1c_syntax.bsl.languageserver.utils.V8TypeHelper;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParser.AssignmentContext;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import lombok.ToString;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.CRITICAL,
  minutesToFix = 20,
  tags = {
    DiagnosticTag.PERFORMANCE
  }
)
public class CreateQueryInCycleDiagnostic extends AbstractVisitorDiagnostic implements V8TypeFromPresentationSupplier, V8TypeFromVariableSupplier {
  private static final Pattern EXECUTE_CALL_PATTERN = CaseInsensitivePattern.compile("Выполнить|Execute");
  private static final Pattern QUERY_BUILDER_PATTERN =
    CaseInsensitivePattern.compile("ПостроительЗапроса|QueryBuilder");
  private static final Pattern REPORT_BUILDER_PATTERN =
    CaseInsensitivePattern.compile("ПостроительОтчета|ReportBuilder");
  private static final Pattern QUERY_PATTERN = CaseInsensitivePattern.compile("Запрос|Query");

  private static final String GLOBAL_SCOPE = "GLOBAL_SCOPE";
  private static final String MODULE_SCOPE = "MODULE_SCOPE";

  private VariableScope currentScope;

  public CreateQueryInCycleDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  private static V8TypeFromPresentationSupplier getTypeSupplier() {
    return (typeName) -> {
      if (QUERY_BUILDER_PATTERN.matcher(typeName).matches()) {
        return Optional.of(NecessaryTypes.QUERY_BUILDER_TYPE);
      } else if (REPORT_BUILDER_PATTERN.matcher(typeName).matches()) {
        return Optional.of(NecessaryTypes.REPORT_BUILDER_TYPE);
      } else if (QUERY_PATTERN.matcher(typeName).matches()) {
        return Optional.of(NecessaryTypes.QUERY_TYPE);
      }
      return Optional.empty();
    };
  }

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {
    currentScope = new VariableScope();
    currentScope.enterScope(GLOBAL_SCOPE);
    ParseTree result = super.visitFile(ctx);
    currentScope = null;
    return result;
  }

  @Override
  public ParseTree visitFileCodeBlock(BSLParser.FileCodeBlockContext ctx) {
    currentScope.enterScope(MODULE_SCOPE);
    ParseTree result = super.visitFileCodeBlock(ctx);
    currentScope.leaveScope();
    return result;
  }

  @Override
  public ParseTree visitProcedure(BSLParser.ProcedureContext ctx) {
    currentScope.enterScope(ctx.procDeclaration().subName().getText());
    ParseTree result = super.visitProcedure(ctx);
    currentScope.leaveScope();
    return result;
  }

  @Override
  public ParseTree visitFunction(BSLParser.FunctionContext ctx) {
    currentScope.enterScope(ctx.funcDeclaration().subName().getText());
    ParseTree result = super.visitFunction(ctx);
    currentScope.leaveScope();
    return result;
  }

  @Override
  public ParseTree visitAssignment(AssignmentContext ctx) {
    if (ctx.expression() == null) {
      return super.visitAssignment(ctx);
    }

    BSLParser.MemberContext firstMember = ctx.expression().member(0);
    if (firstMember == null) {
      return super.visitAssignment(ctx);
    }
    String variableName = ctx.lValue().getText();
    VariableDefinition currentVariable = new VariableDefinition(variableName);
    currentVariable.addDeclaration(ctx.lValue());

    if (firstMember.complexIdentifier() != null) {
      currentVariable.types.addAll(V8TypeHelper.getTypesFromComplexIdentifier(firstMember.complexIdentifier(), this));
    } else if (firstMember.constValue() != null) {
      currentVariable.types.add(V8TypeHelper.getConstValueType(firstMember.constValue()));
    } else {
      currentVariable.addType(V8BasicType.UNDEFINED_TYPE);
    }

    currentScope.addVariable(currentVariable);
    return super.visitAssignment(ctx);
  }

  private void visitDescendantCodeBlock(BSLParser.CodeBlockContext ctx) {
    Optional.ofNullable(ctx)
      .map(e -> e.children)
      .stream()
      .flatMap(Collection::stream)
      .forEach(t -> t.accept(this));
  }

  @Override
  public ParseTree visitAccessCall(BSLParser.AccessCallContext ctx) {
    if (!EXECUTE_CALL_PATTERN.matcher(ctx.methodCall().methodName().getText()).matches()) {
      return super.visitAccessCall(ctx);
    }
    if (!currentScope.codeFlowInCycle()) {
      return super.visitAccessCall(ctx);
    }

    String variableName = null;
    BSLParserRuleContext errorContext = null;
    BSLParserRuleContext parent = (BSLParserRuleContext) ctx.getParent();
    if (parent instanceof BSLParser.CallStatementContext) {
      errorContext = parent;
      variableName = V8TypeHelper.getVariableNameFromCallStatementContext((BSLParser.CallStatementContext) parent, ctx);
    } else if (parent instanceof BSLParser.ModifierContext) {
      BSLParser.ModifierContext callModifier = (BSLParser.ModifierContext) parent;
      errorContext = (BSLParserRuleContext) callModifier.getParent();
      variableName = V8TypeHelper.getVariableNameFromModifierContext(callModifier);
    }
    Optional<VariableDefinition> variableDefinition = currentScope.getVariableByName(variableName);
    BSLParserRuleContext finalErrorContext = errorContext;
    if (finalErrorContext != null) {
      variableDefinition.ifPresent((VariableDefinition definition) -> {

        if (definition.types.contains(NecessaryTypes.QUERY_BUILDER_TYPE)
          || definition.types.contains(NecessaryTypes.REPORT_BUILDER_TYPE)
          || definition.types.contains(NecessaryTypes.QUERY_TYPE)) {
          diagnosticStorage.addDiagnostic(finalErrorContext);
        }

      });
    }

    return super.visitAccessCall(ctx);
  }

  @Override
  public ParseTree visitForEachStatement(BSLParser.ForEachStatementContext ctx) {
    boolean alreadyInCycle = currentScope.codeFlowInCycle();
    currentScope.flowMode.push(CodeFlowType.CYCLE);
    if (alreadyInCycle) {
      Optional.ofNullable(ctx.expression())
        .ifPresent(e -> e.accept(this));
    }
    visitDescendantCodeBlock(ctx.codeBlock());
    currentScope.flowMode.pop();
    return ctx;
  }

  @Override
  public ParseTree visitWhileStatement(BSLParser.WhileStatementContext ctx) {
    currentScope.flowMode.push(CodeFlowType.CYCLE);
    ParseTree result = super.visitWhileStatement(ctx);
    currentScope.flowMode.pop();
    return result;
  }

  @Override
  public ParseTree visitForStatement(BSLParser.ForStatementContext ctx) {
    boolean alreadyInCycle = currentScope.codeFlowInCycle();
    currentScope.flowMode.push(CodeFlowType.CYCLE);
    if (alreadyInCycle) {
      ctx.expression()
        .forEach(e -> e.accept(this));
    }
    visitDescendantCodeBlock(ctx.codeBlock());
    currentScope.flowMode.pop();
    return ctx;
  }

  @Override
  public Optional<V8Type> getTypeFromPresentation(String presentation) {
    return getTypeSupplier().getTypeFromPresentation(presentation);
  }

  @Override
  public Optional<Set<V8Type>> getTypesFromVariable(String variableName) {
    return currentScope.getVariableByName(variableName)
      .map(variableDefinition -> variableDefinition.types);
  }

  enum NecessaryTypes implements V8Type {
    REPORT_BUILDER_TYPE("ReportBuilder"), QUERY_BUILDER_TYPE("QueryBuilder"), QUERY_TYPE("Query");

    String name;

    NecessaryTypes(String name) {
      this.name = name;
    }

    @Override
    public String presentation() {
      return this.name;
    }
  }

  public enum CodeFlowType {
    LINEAR, CYCLE
  }

  @ToString
  public static class VariableDefinition {
    private final String variableName;
    private final Set<V8Type> types = new HashSet<>();
    private ParseTree firstDeclaration;

    VariableDefinition(String variableName) {
      this.variableName = variableName;
    }

    public void addType(V8Type type) {
      this.types.add(type);
    }

    public void addDeclaration(ParseTree firstDeclaration) {
      if (this.firstDeclaration == null) {
        this.firstDeclaration = firstDeclaration;
      }
    }
  }

  private static class Scope {
    private final String name;

    private final HashMap<String, VariableDefinition> variables = new HashMap<>();

    public Scope(String name) {
      this.name = name;
    }

    public void addVariable(VariableDefinition variableDefinition, boolean typesMerge) {
      this.variables.merge(
        variableDefinition.variableName,
        variableDefinition,
        (VariableDefinition key, VariableDefinition value) -> {
          if (!typesMerge) {
            key.types.clear();
          }
          key.types.addAll(value.types);

          return key;
        });
    }

    public String getName() {
      return name;
    }
  }

  private static class VariableScope extends ArrayDeque<Scope> {
    private final Deque<CodeFlowType> flowMode = new ArrayDeque<>();

    public boolean codeFlowInCycle() {
      final CodeFlowType flowType = flowMode.peek();
      if (flowType == null) {
        return false;
      }
      return flowType == CodeFlowType.CYCLE;
    }

    public Optional<VariableDefinition> getVariableByName(String variableName) {
      return Optional.ofNullable(current().variables.get(variableName));
    }

    public void addVariable(VariableDefinition variableDefinition) {
      final CodeFlowType flowType = flowMode.peek();
      if (flowType == null) {
        return;
      }
      this.current().addVariable(variableDefinition, flowType == CodeFlowType.CYCLE);
    }

    public void enterScope(String name) {
      Scope newScope = new Scope(name);
      if (!this.isEmpty()) {
        Scope prevScope = this.peek();
        newScope.variables.putAll(prevScope.variables);
      }
      this.push(newScope);
      flowMode.push(CodeFlowType.LINEAR);
    }

    public void leaveScope() {
      this.pop();
      flowMode.pop();
    }

    public Scope current() {
      return this.peek();
    }

  }
}
