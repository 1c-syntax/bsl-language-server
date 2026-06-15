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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParser.CallParamContext;
import com.github._1c_syntax.bsl.parser.BSLParser.RaiseStatementContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 5,
  scope = DiagnosticScope.BSL,
  tags = {DiagnosticTag.BADPRACTICE}
)
public class BadExceptionCategoryDiagnostic extends AbstractVisitorDiagnostic {

  private final TypeService typeService;

  public BadExceptionCategoryDiagnostic(TypeService typeService) {
    this.typeService = typeService;
  }

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
    Optional.ofNullable(ctx.doCall())
      .map(BSLParser.DoCallContext::callParamList)
      .map(BSLParser.CallParamListContext::callParam)
      .filter(params -> params.size() > 1)
      .map(params -> params.get(1))
      .filter(this::isForbiddenCategoryParam)
      .ifPresent(diagnosticStorage::addDiagnostic);
  }

  private boolean isForbiddenCategoryParam(CallParamContext param) {
    String rawText = param.getText();
    String[] parts = rawText.split("\\.");
    if (parts.length != 2) {
      return false;
    }

    String ownerName = parts[0].trim();
    String memberName = parts[1].trim().toLowerCase(Locale.ROOT);
    if (!FORBIDDEN_CATEGORIES.contains(memberName)) {
      return false;
    }

    return typeService.resolve(ownerName, documentContext.getFileType())
      .filter(typeRef -> {
        String qName = typeRef.qualifiedName();
        return "КатегорияОшибки".equalsIgnoreCase(qName) || "ErrorCategory".equalsIgnoreCase(qName);
      })
      .isPresent();
  }
}