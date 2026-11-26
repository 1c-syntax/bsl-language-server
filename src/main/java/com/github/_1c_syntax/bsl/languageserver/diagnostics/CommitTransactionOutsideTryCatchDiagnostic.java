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
public class CommitTransactionOutsideTryCatchDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern END_TRANSACTION_PATTERN = CaseInsensitivePattern.compile(
    "ЗафиксироватьТранзакцию|CommitTransaction"
  );

  private @Nullable ParserRuleContext nodeEndTransaction;
  private BSLParser.@Nullable StatementContext nodeEndFile;

  public CommitTransactionOutsideTryCatchDiagnostic() {
    nodeEndTransaction = null;
    nodeEndFile = null;
  }

  private static boolean isGlobalMethodCommitTransaction(BSLParser.StatementContext ctx) {
    if (ctx.getStart().getType() != BSLParser.IDENTIFIER) {
      return false;
    }

    return ctx.getChildCount() > 0
      && ctx.getChild(0).getChildCount() > 0
      && ctx.getChild(0).getChild(0) instanceof BSLParser.GlobalMethodCallContext
      && END_TRANSACTION_PATTERN.matcher(ctx.getText()).find();
  }

  @Override
  public ParseTree visitExceptCodeBlock(BSLParser.ExceptCodeBlockContext ctx) {
    nodeEndTransaction = null;
    return super.visitExceptCodeBlock(ctx);
  }

  @Override
  public ParseTree visitStatement(BSLParser.StatementContext ctx) {

    if (ctx.getStart().getType() == BSLParser.TRY_KEYWORD) {
      if (nodeEndTransaction != null) {
        diagnosticStorage.addDiagnostic(nodeEndTransaction);
      }
      nodeEndTransaction = null;
      return super.visitStatement(ctx);
    }

    // Это код после ЗафиксироватьТранзакцию
    if (nodeEndTransaction != null) {
      diagnosticStorage.addDiagnostic(nodeEndTransaction);
      nodeEndTransaction = null;
    }

    // Ищем только в идентификаторах
    if (isGlobalMethodCommitTransaction(ctx)) {
      nodeEndTransaction = ctx;
    }

    // Если это код в конце модуля, ЗафиксироватьТранзакию был/есть тогда фиксируем
    if (nodeEndFile != null && nodeEndTransaction != null && nodeEndFile.equals(ctx)) {
      diagnosticStorage.addDiagnostic(nodeEndTransaction);
      nodeEndTransaction = null;
    }
    return super.visitStatement(ctx);
  }

  @Override
  public ParseTree visitSubCodeBlock(BSLParser.SubCodeBlockContext ctx) {
    super.visitSubCodeBlock(ctx);
    // ЗафиксироватьТранзакцию в конце метода
    if (nodeEndTransaction != null) {
      diagnosticStorage.addDiagnostic(nodeEndTransaction);
      nodeEndTransaction = null;
    }
    return ctx;
  }

  @Override
  public ParseTree visitFileCodeBlock(BSLParser.FileCodeBlockContext ctx) {
    // Находим последний стейт в модуле и запоминаем его
    Stream<ParseTree> statements = Trees.findAllRuleNodes(ctx, BSLParser.RULE_statement).stream();
    nodeEndFile = (BSLParser.StatementContext) statements.reduce((a, b) -> b).orElse(null);
    return super.visitFileCodeBlock(ctx);
  }
}
