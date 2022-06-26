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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.mdclasses.common.ConfigurationSource;
import com.github._1c_syntax.mdclasses.mdo.AbstractMDObjectBSL;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.BLOCKER,
  scope = DiagnosticScope.BSL,
  minutesToFix = 1
)

public class MissingMethodOfCommonModuleDiagnostic extends AbstractVisitorDiagnostic {

  @Value
  @AllArgsConstructor
  private static class CallData {
    String moduleName;
    String methodName;
    Range moduleMethodRange;
  }

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {
    if (documentContext.getServerContext().getConfiguration().getConfigurationSource() == ConfigurationSource.EMPTY){
      return ctx;
    }
    return super.visitFile(ctx);
  }

  @Override
  public ParseTree visitCallStatement(BSLParser.CallStatementContext ctx) {
    final var moduleNameNode = ctx.IDENTIFIER();
    if (moduleNameNode != null){
      final var modifier = Optional.ofNullable(ctx.modifier());
      var accessCallContext = modifier
        .filter(List::isEmpty)
        .map(modifierContexts -> ctx.accessCall());
      if (accessCallContext.isEmpty()){
        accessCallContext = getAccessCallContext(modifier);
      }
      checkCombination(moduleNameNode, accessCallContext)
        .ifPresent(this::fireIssue);
    }
    return super.visitCallStatement(ctx);
  }

  @Override
  public ParseTree visitMember(BSLParser.MemberContext ctx) {
    final var complexIdentifierContext = Optional.ofNullable(ctx.complexIdentifier());
    final var moduleNameNode = complexIdentifierContext
      .map(BSLParser.ComplexIdentifierContext::IDENTIFIER);
    if (moduleNameNode.isPresent()){
      final var modifierContexts = complexIdentifierContext
        .map(BSLParser.ComplexIdentifierContext::modifier);
      checkModifiers(moduleNameNode.orElseThrow(), modifierContexts)
        .ifPresent(this::fireIssue);
    }

    return super.visitMember(ctx);
  }

  @Override
  public ParseTree visitLValue(BSLParser.LValueContext ctx) {
    final var moduleNameNode = Optional.ofNullable(ctx.IDENTIFIER());
    if (moduleNameNode.isPresent()){
      final var modifierContexts = Optional.ofNullable(ctx.acceptor())
        .map(BSLParser.AcceptorContext::modifier);
      checkModifiers(moduleNameNode.orElseThrow(), modifierContexts)
        .ifPresent(this::fireIssue);
    }

    return super.visitLValue(ctx);
  }

  private static Optional<BSLParser.AccessCallContext> getAccessCallContext(
    Optional<? extends List<? extends BSLParser.ModifierContext>> modifierContexts) {

    return modifierContexts
      .filter(list -> !list.isEmpty())
      .map(list -> list.get(0).accessCall());
  }

  private Optional<CallData> checkModifiers(TerminalNode moduleNameNode,
                                            Optional<? extends List<? extends BSLParser.ModifierContext>> modifierContexts) {
    final var accessCallContext = getAccessCallContext(modifierContexts);
    return checkCombination(moduleNameNode, accessCallContext);
  }

  private Optional<CallData> checkCombination(TerminalNode moduleNameNode,
                                              Optional<BSLParser.AccessCallContext> accessCallContext) {
    return accessCallContext
      .map(BSLParser.AccessCallContext::methodCall)
      .map(BSLParser.MethodCallContext::methodName)
      .map(methodNameContext1 -> checkNodeCombination(moduleNameNode, methodNameContext1));
  }

  @Nullable
  private CallData checkNodeCombination(TerminalNode moduleNameNode, BSLParser.MethodNameContext methodNameContext) {
    final var moduleName = moduleNameNode.getText();
    final var methodName = methodNameContext.getText();
    if (!isVariableInCurrentModule(moduleName, moduleNameNode) && notExistModuleMethod(moduleName, methodName)){
      return new CallData(moduleName, methodName, getRange(moduleNameNode, methodNameContext));
    }
    return null;
  }

  private boolean isVariableInCurrentModule(String name, TerminalNode moduleNameNode) {
    return documentContext.getSymbolTree().getMethods().stream()
      .filter(methodSymbol -> Ranges.containsRange(methodSymbol.getRange(), Ranges.create(moduleNameNode)))
      .anyMatch(methodSymbol -> documentContext.getSymbolTree().getVariableSymbol(name, methodSymbol).isPresent());
  }

  private boolean notExistModuleMethod(String moduleName, String methodName) {
    final var serverContext = documentContext.getServerContext();
    final var commonModule = serverContext.getConfiguration().getCommonModule(moduleName);
    if (commonModule.isEmpty()) {
      return false;
    }
    return commonModule
      .map(AbstractMDObjectBSL::getModules)
      .filter(mdoModules -> !mdoModules.isEmpty())
      .map(mdoModules -> mdoModules.get(0).getUri())
      .map(serverContext::getDocument)
      .map(DocumentContext::getSymbolTree)
      .flatMap(symbolTree -> symbolTree.getMethodSymbol(methodName))
      .filter(MethodSymbol::isExport) // TODO нужно сделать отдельное сообщение про обращение к приватному методу
      .isEmpty();
  }

  private static Range getRange(TerminalNode moduleNameNode, BSLParser.MethodNameContext methodNameContext) {
    return Ranges.create(moduleNameNode, methodNameContext.IDENTIFIER());
  }

  private void fireIssue(CallData callData) {
    final var message = info.getMessage(callData.methodName, callData.moduleName);
    diagnosticStorage.addDiagnostic(callData.moduleMethodRange, message);
  }
}
