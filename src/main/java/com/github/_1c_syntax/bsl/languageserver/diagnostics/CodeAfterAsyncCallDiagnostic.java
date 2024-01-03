/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.github._1c_syntax.bsl.parser.BSLParser.RULE_codeBlock;
import static com.github._1c_syntax.bsl.parser.BSLParser.RULE_statement;
import static com.github._1c_syntax.bsl.parser.BSLParser.RULE_subCodeBlock;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 10,
  tags = {
    DiagnosticTag.SUSPICIOUS
  },
  activatedByDefault = false,
  scope = DiagnosticScope.BSL,
  modules = {
    ModuleType.CommandModule,
    ModuleType.FormModule,
    ModuleType.ManagedApplicationModule
  }

)
public class CodeAfterAsyncCallDiagnostic extends AbstractVisitorDiagnostic {
  private static final Pattern ASYNC_METHODS = CaseInsensitivePattern.compile(
    "ПОКАЗАТЬВОПРОС|SHOWQUERYBOX|ПОКАЗАТЬЗНАЧЕНИЕ|SHOWVALUE" +
      "|ПОКАЗАТЬПРЕДУПРЕЖДЕНИЕ|SHOWMESSAGEBOX|ПОКАЗАТЬВВОДДАТЫ|SHOWINPUTDATE|ПОКАЗАТЬВВОДЗНАЧЕНИЯ|SHOWINPUTVALUE" +
      "|ПОКАЗАТЬВВОДСТРОКИ|SHOWINPUTSTRING|ПОКАЗАТЬВВОДЧИСЛА|SHOWINPUTNUMBER" +
      "|НАЧАТЬУСТАНОВКУВНЕШНЕЙКОМПОНЕНТЫ|BEGININSTALLADDIN" +
      "|НАЧАТЬУСТАНОВКУРАСШИРЕНИЯРАБОТЫСФАЙЛАМИ|BEGININSTALLFILESYSTEMEXTENSION" +
      "|НАЧАТЬУСТАНОВКУРАСШИРЕНИЯРАБОТЫСКРИПТОГРАФИЕЙ|BEGININSTALLCRYPTOEXTENSION" +
      "|НАЧАТЬПОДКЛЮЧЕНИЕРАСШИРЕНИЯРАБОТЫСКРИПТОГРАФИЕЙ|BEGINATTACHINGCRYPTOEXTENSION" +
      "|НАЧАТЬПОДКЛЮЧЕНИЕРАСШИРЕНИЯРАБОТЫСФАЙЛАМИ|BEGINATTACHINGFILESYSTEMEXTENSION" +
      "|НАЧАТЬПОМЕЩЕНИЕФАЙЛА|BEGINPUTFILE|НАЧАТЬКОПИРОВАНИЕФАЙЛА|BEGINCOPYINGFILE" +
      "|НАЧАТЬПЕРЕМЕЩЕНИЕФАЙЛА|BEGINMOVINGFILE|НАЧАТЬПОИСКФАЙЛОВ|BEGINFINDINGFILES" +
      "|НАЧАТЬУДАЛЕНИЕФАЙЛОВ|BEGINDELETINGFILES" +
      "|НАЧАТЬСОЗДАНИЕКАТАЛОГА|BEGINCREATINGDIRECTORY|НАЧАТЬПОЛУЧЕНИЕКАТАЛОГАВРЕМЕННЫХФАЙЛОВ|BEGINGETTINGTEMPFILESDIR" +
      "|НАЧАТЬПОЛУЧЕНИЕКАТАЛОГАДОКУМЕНТОВ|BEGINGETTINGDOCUMENTSDIR" +
      "|НАЧАТЬПОЛУЧЕНИЕРАБОЧЕГОКАТАЛОГАДАННЫХПОЛЬЗОВАТЕЛЯ|BEGINGETTINGUSERDATAWORKDIR" +
      "|НАЧАТЬПОЛУЧЕНИЕФАЙЛОВ|BEGINGETTINGFILES|НАЧАТЬПОМЕЩЕНИЕФАЙЛОВ|BEGINPUTTINGFILES" +
      "|НАЧАТЬЗАПРОСРАЗРЕШЕНИЯПОЛЬЗОВАТЕЛЯ|BEGINREQUESTINGUSERPERMISSION" +
      "|НАЧАТЬЗАПУСКПРИЛОЖЕНИЯ|BEGINRUNNINGAPPLICATION");

  private static final List<Integer> ROOT_INDEXES = Arrays.asList(RULE_statement, RULE_subCodeBlock);
  private Optional<BSLParser.CodeBlockContext> subCodeBlockContext = Optional.empty();

  private static boolean checkNextBlocks(BSLParser.StatementContext statement) {
    final var codeBlock = (BSLParser.CodeBlockContext) Trees.getAncestorByRuleIndex(statement, RULE_codeBlock);
    if (codeBlock == null || codeBlock.statement().isEmpty()) {
      return false;
    }
    final var asyncLine = statement.getStop().getLine();
    final var statements = codeBlock.statement().stream()
      .filter(statementContext -> statementContext != statement &&
        statementContext.getStart().getLine() > asyncLine)
      .collect(Collectors.toList());
    final var compoundCtx = statements.stream()
      .findFirst()
      .map(BSLParser.StatementContext::compoundStatement);
    final var returnAfterAsync = compoundCtx
      .map(BSLParser.CompoundStatementContext::returnStatement)
      .isPresent();
    if (returnAfterAsync){
      return false;
    }
    final var breakAfterAsync = compoundCtx
      .map(BSLParser.CompoundStatementContext::breakStatement)
      .isPresent();
    return (!breakAfterAsync && !statements.isEmpty()) || checkParentBlock(codeBlock);
  }

  private static boolean checkParentBlock(BSLParser.CodeBlockContext codeBlock) {
    final var rootStatement = Trees.getRootParent(codeBlock, ROOT_INDEXES);
    if (rootStatement != null && rootStatement.getRuleIndex() == RULE_statement) {
      return checkNextBlocks((BSLParser.StatementContext) rootStatement);
    }
    return false;
  }

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {
    subCodeBlockContext = Optional.empty();
    return super.visitFile(ctx);
  }

  @Override
  public ParseTree visitSubCodeBlock(BSLParser.SubCodeBlockContext ctx) {
    subCodeBlockContext = Optional.of(ctx.codeBlock());
    return super.visitSubCodeBlock(ctx);
  }

  @Override
  public ParseTree visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
    var nonFileCodeBlock = subCodeBlockContext.isPresent();
    if (nonFileCodeBlock) {
      var methodName = ctx.methodName().getText();
      if (ASYNC_METHODS.matcher(methodName).matches()) {

        final var statement = (BSLParser.StatementContext) Trees.getAncestorByRuleIndex(ctx, RULE_statement);
        if (statement != null && checkNextBlocks(statement)) {
          diagnosticStorage.addDiagnostic(ctx,
            info.getMessage(methodName));
        }
      }
    }
    return super.visitGlobalMethodCall(ctx);
  }

}
