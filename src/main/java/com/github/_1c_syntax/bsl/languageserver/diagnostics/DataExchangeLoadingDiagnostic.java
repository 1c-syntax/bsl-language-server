/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
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
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.mdclasses.mdo.support.ModuleType;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.CRITICAL,
  scope = DiagnosticScope.BSL,
  modules = {
    ModuleType.ObjectModule,
    ModuleType.RecordSetModule,
    ModuleType.ValueManagerModule
  },
  minutesToFix = 5,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.BADPRACTICE,
    DiagnosticTag.UNPREDICTABLE
  }

)
public class DataExchangeLoadingDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern searchSubNames = CaseInsensitivePattern.compile(
    "^(ПередЗаписью|ПриЗаписи|ПередУдалением|BeforeWrite|BeforeDelete|OnWrite)$"
  );
  private static final Pattern searchCondition = CaseInsensitivePattern.compile(
    "ОбменДанными\\.Загрузка|DataExchange\\.Load"
  );

  private static final boolean FIND_FIRST = false;

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + FIND_FIRST
  )
  private boolean findFirst = FIND_FIRST;

  @Override
  public ParseTree visitProcDeclaration(BSLParser.ProcDeclarationContext ctx) {
    Optional.of(ctx)
      .map(BSLParser.ProcDeclarationContext::subName)
      .filter(subName ->
        searchSubNames.matcher(subName.getText()).find()
          && !checkPassed(ctx)
      )
      .flatMap(context ->
        Optional.of(documentContext.getSymbolTree())
          .flatMap(symbolTree -> symbolTree.getMethodSymbol((BSLParser.SubContext) getSubContext(ctx))))
      .ifPresent(methodSymbol -> diagnosticStorage.addDiagnostic(methodSymbol.getSubNameRange()));
    return ctx;
  }

  private boolean checkPassed(BSLParser.ProcDeclarationContext ctx) {
    AtomicInteger orderStatement = new AtomicInteger();
    return Optional.of(ctx)
      .map(BSLParser.ProcDeclarationContext::getParent)
      .map(BSLParser.ProcedureContext.class::cast)
      .map(BSLParser.ProcedureContext::subCodeBlock)
      .map(BSLParser.SubCodeBlockContext::codeBlock)
      .map(BSLParser.CodeBlockContext::statement)
      // FIXME: сделай этот фрагмент кода проще и лучше
      .flatMap(context -> context.stream()
        .filter((BSLParser.StatementContext statement) -> {
          orderStatement.getAndIncrement();
          if (findFirst && orderStatement.get() > 1) {
            return false;
          }
          return foundLoadConditionWithReturn(statement);
        })
        .findFirst())
      .isPresent();
  }

  private static boolean foundLoadConditionWithReturn(BSLParser.StatementContext ctx) {
    return Optional.of(ctx)
      .map(BSLParser.StatementContext::compoundStatement)
      .map(BSLParser.CompoundStatementContext::ifStatement)
      .map(BSLParser.IfStatementContext::ifBranch)
      .filter(context ->
        searchCondition.matcher(context.expression().getText()).find()
          && foundReturnStatement(context))
      .isPresent();
  }

  private static boolean foundReturnStatement(BSLParser.IfBranchContext ifBranch) {

    return Optional.ofNullable(ifBranch.codeBlock())
      .map(codeBlockContext -> Trees.findAllRuleNodes(codeBlockContext, BSLParser.RULE_returnStatement))
      .map(list -> !list.isEmpty())
      .orElse(false);
  }

  private static ParserRuleContext getSubContext(BSLParser.ProcDeclarationContext ctx) {
    return Trees.getAncestorByRuleIndex((ParserRuleContext) ctx.getRuleContext(), BSLParser.RULE_sub);
  }
}
