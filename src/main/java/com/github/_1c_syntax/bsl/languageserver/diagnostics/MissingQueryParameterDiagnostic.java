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
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParser.CodeBlockContext;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.bsl.parser.SDBLParser;
import com.github._1c_syntax.bsl.parser.SDBLParser.ParameterContext;
import com.github._1c_syntax.bsl.parser.SDBLParser.QueryPackageContext;
import com.github._1c_syntax.bsl.parser.Tokenizer;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.SUSPICIOUS
  }

)
public class MissingQueryParameterDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern QUERY_PATTERN = CaseInsensitivePattern.compile(
    "Запрос|Query");
  private static final Pattern QUERY_TEXT_PATTERN = CaseInsensitivePattern.compile(
    "Текст|Text");
//  private static final Pattern EXECUTE_PATTERN = CaseInsensitivePattern.compile(
//    "Выполнить|Execute");
  private static final Pattern SET_PARAMETER_PATTERN = CaseInsensitivePattern.compile(
    "УстановитьПараметр|SetParameter");
  public static final int SET_PARAMETER_PARAMS_COUNT = 2;

  private Collection<CodeBlockContext> codeBlocks = Collections.emptyList();

  @Override
  public ParseTree visitFile(BSLParser.FileContext file) {

    final var queriesWithParams = documentContext.getQueries().stream()
      .map(Tokenizer::getAst)
      .map(ctx -> Pair.of(ctx, getParams(ctx)))
      .collect(Collectors.toList());

    if (!queriesWithParams.isEmpty()) {
      codeBlocks = getCodeBlocks();

      queriesWithParams.forEach(query -> visitQuery(query.getLeft(), query.getRight()));

      codeBlocks.clear();

    }

    return super.visitFile(file);
  }

  private Collection<CodeBlockContext> getCodeBlocks() {
    final var ast = documentContext.getAst();
    var blocks = getSubBlocks(ast);
    final var fileCodeBlock = Optional.ofNullable(ast.fileCodeBlock()).map(BSLParser.FileCodeBlockContext::codeBlock);
    fileCodeBlock.ifPresent(blocks::add);
    final var fileCodeBlockBeforeSub =
      Optional.ofNullable(ast.fileCodeBlockBeforeSub())
      .map(BSLParser.FileCodeBlockBeforeSubContext::codeBlock);
    fileCodeBlockBeforeSub.ifPresent(blocks::add);
    return blocks;
  }

  private void visitQuery(QueryPackageContext queryPackage, List<ParameterContext> params) {
    final var codeBlockByQuery = getCodeBlockByQuery(queryPackage);
    codeBlockByQuery.ifPresent(block -> checkQueriesInsideBlock(block, queryPackage, params));
  }

  private static Collection<CodeBlockContext> getSubBlocks(BSLParser.FileContext ast) {
    if (ast.subs() == null) {
      return new ArrayList<>();
    }
    return ast.subs().sub().stream().map((BSLParser.SubContext sub) -> {
      if (sub.procedure() == null) {
        return sub.function().subCodeBlock().codeBlock();
      } else {
        return sub.procedure().subCodeBlock().codeBlock();
      }
    }).collect(Collectors.toList());
  }

  private static List<ParameterContext> getParams(QueryPackageContext queryPackage) {
    return Trees.findAllRuleNodes(queryPackage, SDBLParser.RULE_parameter).stream()
      .filter(ParameterContext.class::isInstance)
      .map(ParameterContext.class::cast)
      .collect(Collectors.toList());
  }

  private Optional<CodeBlockContext> getCodeBlockByQuery(QueryPackageContext key) {
    return codeBlocks.stream()
      .filter(block -> Ranges.containsRange(Ranges.create(block), Ranges.create(key)))
      .findFirst();
  }

  private void checkQueriesInsideBlock(CodeBlockContext codeBlock, QueryPackageContext queryPackage,
                                       List<ParameterContext> params) {
    // todo вычислять единожды сразу с отбором Новый Запрос или ХХХ.Текст
    final var assignments = Trees.findAllRuleNodes(codeBlock, BSLParser.RULE_assignment);

    final var range = Ranges.create(queryPackage);
    assignments.stream()
      .filter(BSLParser.AssignmentContext.class::isInstance)
      .map(BSLParser.AssignmentContext.class::cast)
      .filter(assignment -> Ranges.containsRange(Ranges.create(assignment.expression()), range))
      .forEach(assignment -> checkAssignment(codeBlock, queryPackage, params, assignment));
  }

  private void checkAssignment(CodeBlockContext codeBlock, QueryPackageContext queryPackage,
                               List<ParameterContext> params, BSLParser.AssignmentContext assignment) {

    var callStatements = getCallStatements(codeBlock);    // todo вычислять единожды
    if (!callStatements.isEmpty()) {

      final var queryRange = Ranges.create(queryPackage);
      final var queryVarName = computeQueryVarName(assignment);
      final var usedParams = callStatements.stream()
        .filter(callStatement -> Ranges.containsRange(Ranges.create(assignment.expression()), queryRange))
        .map(callStatementContext -> validateSetParameterCallForName(callStatementContext, queryVarName, params))
        .flatMap(Optional::stream)
        .collect(Collectors.toList());
      params.removeAll(usedParams);
    }
    params.forEach(diagnosticStorage::addDiagnostic);
  }

  private static List<BSLParser.CallStatementContext> getCallStatements(CodeBlockContext codeBlock) {
    return Trees.findAllRuleNodes(codeBlock, BSLParser.RULE_callStatement).stream()
      .filter(BSLParser.CallStatementContext.class::isInstance)
      .map(BSLParser.CallStatementContext.class::cast)
      .filter(MissingQueryParameterDiagnostic::isSetParameterCall)
      .collect(Collectors.toList());
  }

  private static boolean isSetParameterCall(BSLParser.CallStatementContext callStatement) {
    return Optional.of(callStatement)
      .map(BSLParser.CallStatementContext::accessCall)
      .map(BSLParser.AccessCallContext::methodCall)
      .map(BSLParser.MethodCallContext::methodName)
      .filter(ctx -> SET_PARAMETER_PATTERN.matcher(ctx.getText()).matches())
      .isPresent();
  }

  private static String computeQueryVarName(BSLParser.AssignmentContext assignment) {
    if (isNewQueryExpr(assignment)) {
      return assignment.lValue().getText();
    }
    return computeObjectQueryFromLValue(assignment.lValue());
  }

  private static boolean isNewQueryExpr(BSLParser.AssignmentContext assignment) {
    return Trees.findAllRuleNodes(assignment, BSLParser.RULE_newExpression).stream()
      .filter(BSLParser.NewExpressionContext.class::isInstance)
      .map(BSLParser.NewExpressionContext.class::cast)
      .anyMatch(ctx -> QUERY_PATTERN.matcher(ctx.typeName().getText()).matches());
  }

  private static String computeObjectQueryFromLValue(BSLParser.LValueContext lValue) {
    if (lValue.acceptor() == null) {
      return lValue.getText();
    }
    return Optional.of(lValue.acceptor())
      .map(BSLParser.AcceptorContext::accessProperty)
      .map(BSLParser.AccessPropertyContext::IDENTIFIER)
      .map(ParseTree::getText)
      .filter(s -> QUERY_TEXT_PATTERN.matcher(s).matches())
      .orElse("");
  }

  private static Optional<ParameterContext> validateSetParameterCallForName(BSLParser.CallStatementContext callStatementContext,
                                                                     String queryVarName, List<ParameterContext> params) {
    final var ctx = Optional.of(callStatementContext);
    final var used = ctx
      .map(BSLParser.CallStatementContext::IDENTIFIER)
      .map(ParseTree::getText)
      .filter(queryVarName::equalsIgnoreCase)
      .isPresent();
    if (!used) {
      return Optional.empty();
    }
    return ctx.map(BSLParser.CallStatementContext::accessCall)
      .map(BSLParser.AccessCallContext::methodCall)
      .map(BSLParser.MethodCallContext::doCall)
      .map(BSLParser.DoCallContext::callParamList)
      .map(BSLParser.CallParamListContext::callParam)
      .filter(callParamContexts -> callParamContexts.size() == SET_PARAMETER_PARAMS_COUNT)
      .map(callParamContexts -> callParamContexts.get(0))
      .map(BSLParser.CallParamContext::expression)
      .map(BSLParser.ExpressionContext::member)
      .filter(memberContexts -> !memberContexts.isEmpty())
      .map(memberContexts -> memberContexts.get(0))
      .map(BSLParser.MemberContext::constValue)
      .map(BSLParserRuleContext::getText)
      .flatMap(s -> params.stream().filter(param -> ("\"" + param.name.getText() + "\"").equalsIgnoreCase(s)).findFirst());
  }
}
