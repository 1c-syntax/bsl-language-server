/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.CRITICAL,
  minutesToFix = 3,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.PERFORMANCE,
    DiagnosticTag.BADPRACTICE
  },
  activatedByDefault = false
)
public class MissingTempStorageDeletionDiagnostic extends AbstractFindMethodDiagnostic {

  private static final Pattern GET_FROM_TEMP_STORAGE_PATTERN = CaseInsensitivePattern.compile(
    "получитьизвременногохранилища|getfromtempstorage"
  );
  private static final Pattern DELETE_FROM_TEMP_STORAGE_PATTERN = CaseInsensitivePattern.compile(
    "удалитьизвременногохранилища|deletefromtempstorage"
  );

  private @Nullable
  BSLParser.SubContext currentSub;
  private List<? extends BSLParser.StatementContext> subStatements = Collections.emptyList();

  private @Nullable
  BSLParser.FileCodeBlockContext fileCodeBlock;
  private List<? extends BSLParser.StatementContext> fileCodeBlockStatements = Collections.emptyList();

  public MissingTempStorageDeletionDiagnostic() {
    super(GET_FROM_TEMP_STORAGE_PATTERN);
  }

  @Override
  protected boolean checkMethodCall(BSLParser.MethodCallContext ctx) {
    return false;
  }

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {

    final var result = super.visitFile(ctx);

    currentSub = null;
    fileCodeBlock = null;
    subStatements.clear();
    fileCodeBlockStatements.clear();

    return result;
  }

  @Override
  public ParseTree visitFileCodeBlock(BSLParser.FileCodeBlockContext ctx) {
    fileCodeBlock = ctx;
    currentSub = null;

    return super.visitFileCodeBlock(ctx);
  }

  @Override
  public ParseTree visitSub(BSLParser.SubContext ctx) {
    currentSub = ctx;
    fileCodeBlock = null;
    subStatements.clear();

    return super.visitSub(ctx);
  }

  @Override
  protected boolean checkGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
    check(ctx);
    return false;
  }

  private void check(BSLParser.GlobalMethodCallContext ctx) {

    final var isInsideSub = currentSub != null;
    final var isInsideFileCodeBlock = fileCodeBlock != null;

    if (!super.checkGlobalMethodCall(ctx) || (!isInsideSub && !isInsideFileCodeBlock)) {
      return;
    }
    // чтобы не перевычислять, если в большом методе несколько вызовов ПолучитьИзВременногоХранилища
    final var statements = getStatements(isInsideSub, isInsideFileCodeBlock);
    final var sourceCallContext = ctx.doCall();

    final var line = ctx.getStop().getLine();
    if (statements.stream()
      .filter(statement -> greaterOrEqual(statement, line))
      .noneMatch(statement -> haveDeleteFromTempStorageCall(statement, sourceCallContext))) {

      diagnosticStorage.addDiagnostic(ctx);
    }
  }

  private List<? extends BSLParser.StatementContext> getStatements(boolean isInsideSub, boolean isInsideFileCodeBlock) {
    if (isInsideSub) {
      if (subStatements.isEmpty()) {
        subStatements = calcSubStatements();
      }
      return subStatements;
    }
    if (isInsideFileCodeBlock) {
      if (fileCodeBlockStatements.isEmpty()) {
        fileCodeBlockStatements = calcFileCodeBlockStatements();
      }
      return fileCodeBlockStatements;
    }

    throw new IllegalStateException();
  }

  private List<? extends BSLParser.StatementContext> calcSubStatements() {
    if (currentSub == null) {
      return Collections.emptyList();
    }
    final BSLParser.SubCodeBlockContext subCodeBlock;
    BSLParser.ProcedureContext method = currentSub.procedure();
    if (method == null) {
      subCodeBlock = currentSub.function().subCodeBlock();
    } else {
      subCodeBlock = method.subCodeBlock();
    }
    return subCodeBlock.codeBlock().statement();
  }

  private List<? extends BSLParser.StatementContext> calcFileCodeBlockStatements() {
    if (fileCodeBlock == null) {
      return Collections.emptyList();
    }
    return fileCodeBlock.codeBlock().statement();
  }

  private static boolean greaterOrEqual(BSLParser.StatementContext statement, int line) {
    return statement.getStart().getLine() > line;
  }

  private static boolean haveDeleteFromTempStorageCall(BSLParser.StatementContext statement,
                                                       BSLParser.DoCallContext sourceCallCtx) {
    return Trees.findAllRuleNodes(statement, BSLParser.RULE_globalMethodCall).stream()
      .map(parseTree -> (BSLParser.GlobalMethodCallContext) parseTree)
      .filter(globalMethodCall -> DELETE_FROM_TEMP_STORAGE_PATTERN.matcher(globalMethodCall.methodName().getText())
        .matches())
      .anyMatch(globalMethodCall -> DiagnosticHelper.equalNodes(sourceCallCtx, globalMethodCall.doCall()));
  }
}
