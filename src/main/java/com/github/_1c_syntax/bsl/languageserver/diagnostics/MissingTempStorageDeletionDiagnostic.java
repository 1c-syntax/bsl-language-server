/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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
import com.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParser.FileCodeBlockBeforeSubContext;
import com.github._1c_syntax.bsl.parser.BSLParser.FileCodeBlockContext;
import com.github._1c_syntax.bsl.parser.BSLParser.StatementContext;
import com.github._1c_syntax.bsl.parser.BSLParser.SubContext;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
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
  SubContext currentSub;
  private Collection<? extends ParseTree> subStatements = Collections.emptyList();

  private @Nullable
  FileCodeBlockContext fileCodeBlock;
  private Collection<? extends ParseTree> fileCodeBlockStatements = Collections.emptyList();

  private @Nullable
  FileCodeBlockBeforeSubContext fileCodeBlockBeforeSub;
  private Collection<? extends ParseTree> fileCodeBlockBeforeSubStatements = Collections.emptyList();

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
    fileCodeBlockBeforeSub = null;

    subStatements.clear();
    fileCodeBlockStatements.clear();
    fileCodeBlockBeforeSubStatements.clear();

    return result;
  }

  @Override
  public ParseTree visitFileCodeBlockBeforeSub(FileCodeBlockBeforeSubContext ctx) {
    fileCodeBlockBeforeSub = ctx;
    fileCodeBlock = null;
    currentSub = null;

    return super.visitFileCodeBlockBeforeSub(ctx);
  }

  @Override
  public ParseTree visitFileCodeBlock(FileCodeBlockContext ctx) {
    fileCodeBlock = ctx;
    currentSub = null;
    fileCodeBlockBeforeSub = null;

    return super.visitFileCodeBlock(ctx);
  }

  @Override
  public ParseTree visitSub(SubContext ctx) {
    currentSub = ctx;
    fileCodeBlock = null;
    fileCodeBlockBeforeSub = null;
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
    final var isInsideFileCodeBlockBeforeSub = fileCodeBlockBeforeSub != null;

    if (!super.checkGlobalMethodCall(ctx) ||
      (!isInsideSub && !isInsideFileCodeBlock && !isInsideFileCodeBlockBeforeSub)) {
      return;
    }
    // чтобы не перевычислять, если в большом методе несколько вызовов ПолучитьИзВременногоХранилища
    final var statements = getStatements();

    final var sourceCallContext = ctx.doCall();

    final var line = ctx.getStop().getLine();
    if (statements.stream()
      .filter(StatementContext.class::isInstance)
      .map(StatementContext.class::cast)
      .filter(statement -> greaterOrEqual(statement, line))
      .noneMatch(statement -> haveDeleteFromTempStorageCall(statement, sourceCallContext))) {

      diagnosticStorage.addDiagnostic(ctx);
    }
  }

  private Collection<? extends ParseTree> getStatements() {
    if (currentSub != null) {
      if (subStatements.isEmpty()) {
        subStatements = calcSubStatements();
      }
      return subStatements;
    }
    if (fileCodeBlock != null) {
      if (fileCodeBlockStatements.isEmpty()) {
        fileCodeBlockStatements = findAllStatementsInside(fileCodeBlock.codeBlock());
      }
      return fileCodeBlockStatements;
    }
    if (fileCodeBlockBeforeSub != null) {
      if (fileCodeBlockBeforeSubStatements.isEmpty()) {
        fileCodeBlockBeforeSubStatements = findAllStatementsInside(fileCodeBlockBeforeSub.codeBlock());
      }
      return fileCodeBlockBeforeSubStatements;
    }

    throw new IllegalStateException();
  }

  private Collection<? extends ParseTree> calcSubStatements() {
    final var subCodeBlock = getSubCodeBlockContext();
    return findAllStatementsInside(subCodeBlock.codeBlock());
  }

  private BSLParser.SubCodeBlockContext getSubCodeBlockContext() {
    Objects.requireNonNull(currentSub);
    BSLParser.ProcedureContext method = currentSub.procedure();
    if (method == null) {
      return currentSub.function().subCodeBlock();
    }
    return method.subCodeBlock();
  }

  private static Collection<? extends ParseTree> findAllStatementsInside(BSLParser.CodeBlockContext codeBlockContext) {
    return Trees.findAllRuleNodes(codeBlockContext, BSLParser.RULE_statement);
  }

  private static boolean greaterOrEqual(StatementContext statement, int line) {
    return statement.getStart().getLine() > line;
  }

  private static boolean haveDeleteFromTempStorageCall(StatementContext statement,
                                                       BSLParser.DoCallContext sourceCallCtx) {
    return Trees.findAllRuleNodes(statement, BSLParser.RULE_globalMethodCall).stream()
      .map(parseTree -> (BSLParser.GlobalMethodCallContext) parseTree)
      .filter(globalMethodCall -> DELETE_FROM_TEMP_STORAGE_PATTERN.matcher(globalMethodCall.methodName().getText())
        .matches())
      .anyMatch(globalMethodCall -> DiagnosticHelper.equalNodes(sourceCallCtx, globalMethodCall.doCall()));
  }
}
