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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  private static final Pattern SET_PARAMETER_PATTERN = CaseInsensitivePattern.compile(
    "УстановитьПараметр|SetParameter");
  public static final int SET_PARAMETER_PARAMS_COUNT = 2;

  private Collection<CodeBlockContext> codeBlocks = Collections.emptyList();
  private final Map<CodeBlockContext, List<BSLParser.AssignmentContext>> codeBlockAssignments = new HashMap<>();
  private final Map<CodeBlockContext, List<BSLParser.CallStatementContext>> codeBlockCallStatements = new HashMap<>();

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
      codeBlockAssignments.clear();
      codeBlockCallStatements.clear();

    }

    return super.visitFile(file);
  }

  private static List<Pair<ParameterContext, String>> getParams(QueryPackageContext queryPackage) {
    return Trees.findAllRuleNodes(queryPackage, SDBLParser.RULE_parameter).stream()
      .filter(ParameterContext.class::isInstance)
      .map(ParameterContext.class::cast)
      .map(ctx -> Pair.of(ctx, "\"" + ctx.name.getText() + "\""))
      .collect(Collectors.toList());
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

  private void visitQuery(QueryPackageContext queryPackage, List<Pair<ParameterContext, String>> params) {
    final var codeBlockByQuery = getCodeBlockByQuery(queryPackage);
    codeBlockByQuery.ifPresent(block -> checkQueriesInsideBlock(block, queryPackage, params));
  }

  private Optional<CodeBlockContext> getCodeBlockByQuery(QueryPackageContext key) {
    return codeBlocks.stream()
      .filter(block -> Ranges.containsRange(Ranges.create(block), Ranges.create(key)))
      .findFirst();
  }

  private void checkQueriesInsideBlock(CodeBlockContext codeBlock, QueryPackageContext queryPackage,
                                       List<Pair<ParameterContext, String>> params) {

    final var assignments = codeBlockAssignments.computeIfAbsent(codeBlock,
      MissingQueryParameterDiagnostic::getAllAssignmentInsideBlock);

    final var range = Ranges.create(queryPackage);
    assignments.stream()
      .filter(assignment -> Ranges.containsRange(Ranges.create(assignment.expression()), range))
      .forEach(assignment -> checkAssignment(codeBlock, queryPackage, params, assignment));
  }

  private static List<BSLParser.AssignmentContext> getAllAssignmentInsideBlock(CodeBlockContext codeBlock) {
    return Trees.findAllRuleNodes(codeBlock, BSLParser.RULE_assignment).stream()
      .filter(BSLParser.AssignmentContext.class::isInstance)
      .map(BSLParser.AssignmentContext.class::cast)
      .collect(Collectors.toList());
  }

  private void checkAssignment(CodeBlockContext codeBlock, QueryPackageContext queryPackage,
                               List<Pair<ParameterContext, String>> params, BSLParser.AssignmentContext assignment) {

    final var callStatements = codeBlockCallStatements.computeIfAbsent(codeBlock,
      MissingQueryParameterDiagnostic::getCallStatements);

    final var allParams = params.stream()
      .map(Pair::getLeft)
      .collect(Collectors.toList());

    if (!callStatements.isEmpty()) {

      final var queryRange = Ranges.create(queryPackage);
      final var queryVarName = computeQueryVarName(assignment);
      final var usedParams = callStatements.stream()
        .filter(callStatement -> Ranges.containsRange(Ranges.create(assignment.expression()), queryRange))
        .map(callStatementContext -> computeSetParameterByQueryVarName(callStatementContext, queryVarName, params))
        .flatMap(Optional::stream)
        .collect(Collectors.toList());
      allParams.removeAll(usedParams);
    }
    allParams.forEach(node -> diagnosticStorage.addDiagnostic(node,
      info.getMessage(node.PARAMETER_IDENTIFIER().getText())));
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
    return computeQueryVarNameFromLValue(assignment.lValue());
  }

  private static boolean isNewQueryExpr(BSLParser.AssignmentContext assignment) {
    return Trees.findAllRuleNodes(assignment, BSLParser.RULE_newExpression).stream()
      .filter(BSLParser.NewExpressionContext.class::isInstance)
      .map(BSLParser.NewExpressionContext.class::cast)
      .anyMatch(ctx -> QUERY_PATTERN.matcher(ctx.typeName().getText()).matches());
  }

  private static String computeQueryVarNameFromLValue(BSLParser.LValueContext lValue) {
    final var identifier = lValue.IDENTIFIER();
    final var acceptor = lValue.acceptor();
    if (acceptor == null || identifier == null) {
      return lValue.getText();
    }
    return Optional.of(acceptor)
      .map(BSLParser.AcceptorContext::accessProperty)
      .map(BSLParser.AccessPropertyContext::IDENTIFIER)
      .map(ParseTree::getText)
      .filter(s -> QUERY_TEXT_PATTERN.matcher(s).matches())
      .map(s -> identifier.getText())
      .orElse("");
  }

  private static Optional<ParameterContext> computeSetParameterByQueryVarName(BSLParser.CallStatementContext callStatementContext,
                                                                              String queryVarName, List<Pair<ParameterContext, String>> params) {
    final var ctx = Optional.of(callStatementContext);
    final var isCorrectQueryVarNameInCall = ctx
      .map(BSLParser.CallStatementContext::IDENTIFIER)
      .map(ParseTree::getText)
      .filter(queryVarName::equalsIgnoreCase)
      .isPresent();
    if (!isCorrectQueryVarNameInCall) {
      return Optional.empty();
    }
    return computeSetParameter(ctx, params);
  }

  @NotNull
  private static Optional<ParameterContext> computeSetParameter(Optional<BSLParser.CallStatementContext> ctx,
                                                                List<Pair<ParameterContext, String>> params) {
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
      .flatMap(firstValueForSetParameterMethod -> computeSetParameterByName(params, firstValueForSetParameterMethod));
  }

  private static Optional<ParameterContext> computeSetParameterByName(List<Pair<ParameterContext, String>> params,
                                                                      String firstValueForSetParameterMethod) {
    return params.stream().filter(param -> param.getRight().equalsIgnoreCase(firstValueForSetParameterMethod))
      .map(Pair::getLeft).findFirst();
  }
}
