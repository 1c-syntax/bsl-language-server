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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.CRITICAL,
  scope = DiagnosticScope.BSL,
  modules = {
    ModuleType.ObjectModule
  },
  minutesToFix = 10,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.BADPRACTICE,
    DiagnosticTag.UNPREDICTABLE
  }

)
public class DataExchangeLoadingDiagnostic extends AbstractVisitorDiagnostic {

  private static final String SUB_NAMES = "^(ПередЗаписью|ПриЗаписи|ПередУдалением|BeforeWrite|BeforeDelete|OnWrite)$";
  private static final String CONDITION =
    "ОбменДанными.Загрузка=Истина|ОбменДанными.Загрузка|DataExchange.Load=True|DataExchange.Load";

  private static final Pattern searchSubNames = Pattern.compile(
    SUB_NAMES,
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern searchCondition = Pattern.compile(
    CONDITION,
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  public DataExchangeLoadingDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  public ParseTree visitProcDeclaration(BSLParser.ProcDeclarationContext ctx) {
    var subName = ctx.subName();
    if (subName != null) {
      if (searchSubNames.matcher(subName.getText()).matches() && needCreateIssue(ctx)) {
        diagnosticStorage.addDiagnostic(ctx);
      }
    }
    return ctx;
  }

  private boolean needCreateIssue(BSLParser.ProcDeclarationContext ctx) {
    BSLParser.ProcedureContext procedureContext = (BSLParser.ProcedureContext) ctx.getParent();
    var statements = procedureContext.subCodeBlock().codeBlock().statement();
    if (statements != null) {
      if (!statements.isEmpty()) {
        return !foundLoadConditionWithReturn(statements.get(0));
      }
    }
    return true;
  }

  private boolean foundLoadConditionWithReturn(BSLParser.StatementContext ctx) {
    var ifStatement = ctx.compoundStatement().ifStatement();
    if (ifStatement != null) {
      var ifBranch = ifStatement.ifBranch();
      var text = ifBranch.expression().getText();
      return searchCondition.matcher(text).find() && foundReturnStatement(ifBranch);
    }
    return false;
  }

  private boolean foundReturnStatement(BSLParser.IfBranchContext ctx) {
    var ifStatements = ctx.codeBlock().statement();
    if (ifStatements != null) {
      var itemStatement = ifStatements.get(0);
      return itemStatement.compoundStatement().returnStatement() != null;
    }
    return false;
  }

}
