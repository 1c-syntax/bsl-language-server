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
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 10,
  tags = {
    DiagnosticTag.STANDARD
  }
)
public class BeginTransactionBeforeTryCatchDiagnostic extends AbstractVisitorDiagnostic {
  private static final Pattern BEGIN_TRANSACTION_PATTERN = CaseInsensitivePattern.compile(
    "^НачатьТранзакцию$|^BeginTransaction$"
  );

  private @Nullable ParserRuleContext nodeBeginTransaction;
  private BSLParser.@Nullable StatementContext nodeEndFile;

  public BeginTransactionBeforeTryCatchDiagnostic() {
    nodeBeginTransaction = null;
    nodeEndFile = null;
  }

  private static boolean isGlobalMethodBeginTransaction(BSLParser.StatementContext ctx) {
    if (ctx.getStart().getType() != BSLParser.IDENTIFIER) {
      return false;
    }
    return Optional.of(ctx)
      .map(BSLParser.StatementContext::callStatement)
      .map(BSLParser.CallStatementContext::globalMethodCall)
      .map(BSLParser.GlobalMethodCallContext::methodName)
      .map(methodNameContext -> BEGIN_TRANSACTION_PATTERN.matcher(methodNameContext.getText()).matches())
      .orElse(false);
  }

  @Override
  public ParseTree visitStatement(BSLParser.StatementContext ctx) {

    if (ctx.getStart().getType() == BSLParser.TRY_KEYWORD) {
      nodeBeginTransaction = null;
      return super.visitStatement(ctx);
    }

    // Это код после НачатьТранзакцию
    if (nodeBeginTransaction != null) {
      diagnosticStorage.addDiagnostic(nodeBeginTransaction);
      nodeBeginTransaction = null;
    }

    // Ищем только в идентификаторах
    if (isGlobalMethodBeginTransaction(ctx)) {
      nodeBeginTransaction = ctx;
    }

    // Если это код в конце модуля, НачатьТранзакию был/есть тогда фиксируем
    if (nodeEndFile != null && nodeBeginTransaction != null && nodeEndFile.equals(ctx)) {
      diagnosticStorage.addDiagnostic(nodeBeginTransaction);
      nodeBeginTransaction = null;
    }
    return super.visitStatement(ctx);
  }

  @Override
  public ParseTree visitFileCodeBlock(BSLParser.FileCodeBlockContext ctx) {
    // Находим последний стейт в модуле и запоминаем его
    Stream<ParseTree> statements = Trees.findAllRuleNodes(ctx, BSLParser.RULE_statement).stream();
    nodeEndFile = (BSLParser.StatementContext) statements.reduce((a, b) -> b).orElse(null);
    return super.visitFileCodeBlock(ctx);
  }
}
