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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.BSLParser.CallParamContext;
import com.github._1c_syntax.bsl.parser.BSLParser.RaiseStatementContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.List;
import java.util.Set;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.BADPRACTICE
  }
)
public class BadExceptionCategoryDiagnostic extends AbstractVisitorDiagnostic {

  private static final Set<String> FORBIDDEN_CATEGORIES = Set.of(
    "всеошибки", "allerrors",
    "прочаяошибка", "othererror",
    "ошибкакомпиляциивстроенногоязыка", "scriptcompilationerror",
    "ошибкавовремявыполнениявстроенногоязыка", "scriptruntimeerror",
    "исключениевызванноеизвстроенногоязыка", "scriptraisedexception"
  );

  @Override
  public ParseTree visitRaiseStatement(RaiseStatementContext ctx) {
    checkForbiddenCategory(ctx);

    super.visitRaiseStatement(ctx);
    return ctx;
  }

  private void checkForbiddenCategory(RaiseStatementContext ctx) {
    var params = getValidatedCallParams(ctx);
    if (params.size() <= 1) return;

    var categoryParam = params.get(1);
    var expression = categoryParam.expression();
    if (expression == null || expression.member().isEmpty()) return;

    var member = expression.member(0);
    if (member == null) return;

    var complexIdentifier = member.complexIdentifier();
    if (complexIdentifier == null || complexIdentifier.modifier().isEmpty()) return;

    var modifiers = complexIdentifier.modifier();
    var lastModifier = modifiers.getLast();

    var accessProperty = lastModifier.accessProperty();
    if (accessProperty == null) return;

    var identifierNode = accessProperty.IDENTIFIER();
    if (identifierNode == null) return;

    String propertyName = identifierNode.getText().toLowerCase();

    if (FORBIDDEN_CATEGORIES.contains(propertyName)) {
      diagnosticStorage.addDiagnostic(categoryParam);
    }
  }

  private List<? extends CallParamContext> getValidatedCallParams(RaiseStatementContext ctx) {
    var doCall = ctx.doCall();
    if (doCall == null) return List.of();

    var callParamList = doCall.callParamList();
    if (callParamList == null) return List.of();

    return callParamList.callParam();
  }
}