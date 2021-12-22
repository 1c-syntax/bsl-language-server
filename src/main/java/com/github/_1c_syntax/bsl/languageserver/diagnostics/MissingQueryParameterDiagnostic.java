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
import com.github._1c_syntax.bsl.parser.BSLParser.AssignmentContext;
import com.github._1c_syntax.bsl.parser.BSLParser.CodeBlockContext;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.bsl.parser.SDBLParser;
import com.github._1c_syntax.bsl.parser.SDBLParser.ParameterContext;
import com.github._1c_syntax.bsl.parser.SDBLParser.QueryPackageContext;
import com.github._1c_syntax.bsl.parser.Tokenizer;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
  private final Map<CodeBlockContext, List<QueryTextSetupData>> codeBlockAssignments = new HashMap<>();
  private final Map<CodeBlockContext, List<BSLParser.CallStatementContext>> codeBlockCallStatements = new HashMap<>();

  enum QueryVarKind {
    NEW_QUERY,
    QUERY_TEXT,
    EMPTY,
    VAR
  }

  @AllArgsConstructor
  @Getter
  private static class QueryTextSetupData {
    private QueryVarKind kind;
    private String varName;
    private BSLParser.ExpressionContext rightExpr;
    private Optional<BSLParser.CallParamListContext> newQueryExpr;

    boolean isQueryObject(){
      return kind == QueryVarKind.NEW_QUERY || kind == QueryVarKind.QUERY_TEXT;
    }
    boolean isVar(){
      return kind == QueryVarKind.VAR;
    }
  }

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

  private static Map<String, ParameterContext> getParams(QueryPackageContext queryPackage) {
    return Trees.findAllRuleNodes(queryPackage, SDBLParser.RULE_parameter).stream()
      .filter(ParameterContext.class::isInstance)
      .map(ParameterContext.class::cast)
//      .map(ctx -> Pair.of(ctx, "\"" + ctx.name.getText() + "\""))
      // если есть несколько одинаковых параметров в запросе
      .collect(Collectors.toMap(ctx -> "\"" + ctx.name.getText() + "\"", ctx -> ctx,
        (parameterContext, parameterContext2) -> parameterContext));
//      .collect(Collectors.toSet());
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

  private void visitQuery(QueryPackageContext queryPackage, Map<String, ParameterContext> params) {
    final var codeBlockByQuery = getCodeBlockByQuery(queryPackage);
    codeBlockByQuery.ifPresent(codeBlock -> getQueryTextAssignmentsInsideBlock(codeBlock, queryPackage)
      .forEach(queryTextAssignment -> checkAssignment(codeBlock, params, queryTextAssignment)));
  }

  private Optional<CodeBlockContext> getCodeBlockByQuery(QueryPackageContext key) {
    return codeBlocks.stream()
      .filter(block -> Ranges.containsRange(Ranges.create(block), Ranges.create(key)))
      .findFirst();
  }

  private List<QueryTextSetupData> getQueryTextAssignmentsInsideBlock(CodeBlockContext codeBlock,
                                                                      QueryPackageContext queryPackage) {

    final var queryAssignments = codeBlockAssignments.computeIfAbsent(codeBlock,
      MissingQueryParameterDiagnostic::getAllQueryAssignmentInsideBlock);

    final var queryObjectTextAssignments = queryAssignments.stream()
      .filter(QueryTextSetupData::isQueryObject)
      .collect(Collectors.toList());

    final var queryTextAssignments = queryAssignments.stream()
      .filter(QueryTextSetupData::isVar)
      .flatMap(queryTextSetupData -> getQueryTextAssignment(queryObjectTextAssignments,
        queryTextSetupData.getRightExpr(), queryTextSetupData.getVarName())
        .stream())
      .collect(Collectors.toList());

    final var queryRange = Ranges.create(queryPackage);
    final var defaultQueryTextAssignments = queryObjectTextAssignments.stream()
      .filter(queryTextSetupData -> Ranges.containsRange(Ranges.create(queryTextSetupData.getRightExpr()), queryRange))
      .collect(Collectors.toList());

    queryTextAssignments.addAll(defaultQueryTextAssignments);
    return queryTextAssignments;
  }

  private static List<QueryTextSetupData> getAllQueryAssignmentInsideBlock(CodeBlockContext codeBlock) {
    return Trees.findAllRuleNodes(codeBlock, BSLParser.RULE_assignment).stream()
      .filter(AssignmentContext.class::isInstance)
      .map(AssignmentContext.class::cast)
      .map(MissingQueryParameterDiagnostic::computeQueryVarData)
      .filter(queryTextSetupData -> queryTextSetupData.getKind() != QueryVarKind.EMPTY)
      .collect(Collectors.toList());
  }

  private static QueryTextSetupData computeQueryVarData(AssignmentContext assignment) {
    final var newQueryExpr = isNewQueryExpr(assignment);
    if (newQueryExpr.isPresent()) {
      return new QueryTextSetupData(QueryVarKind.NEW_QUERY, assignment.lValue().getText(), assignment.expression(),
        newQueryExpr);
    }
    final var pair = computeQueryVarNameFromLValue(assignment.lValue());
    return new QueryTextSetupData(pair.getRight(), pair.getLeft(), assignment.expression(), Optional.empty());
  }

  private static Optional<BSLParser.CallParamListContext> isNewQueryExpr(AssignmentContext assignment) {
    return Trees.findAllRuleNodes(assignment, BSLParser.RULE_newExpression).stream()
      .filter(BSLParser.NewExpressionContext.class::isInstance)
      .map(BSLParser.NewExpressionContext.class::cast)
      .filter(ctx -> ctx.typeName() != null)
      .filter(ctx -> QUERY_PATTERN.matcher(ctx.typeName().getText()).matches())
      .map(BSLParser.NewExpressionContext::doCall)
      .filter(Objects::nonNull)
      .map(BSLParser.DoCallContext::callParamList)
      .filter(Objects::nonNull)
      .findFirst();
  }

  private static Pair<String, QueryVarKind> computeQueryVarNameFromLValue(BSLParser.LValueContext lValue) {
    final var identifier = lValue.IDENTIFIER();
    final var acceptor = lValue.acceptor();
    if (acceptor == null || identifier == null) {
      return Pair.of(lValue.getText(), QueryVarKind.VAR);
    }
    return Optional.of(acceptor)
      .map(BSLParser.AcceptorContext::accessProperty)
      .map(BSLParser.AccessPropertyContext::IDENTIFIER)
      .map(ParseTree::getText)
      .filter(s -> QUERY_TEXT_PATTERN.matcher(s).matches())
      .map(s -> identifier.getText())
      .map(s -> Pair.of(s, QueryVarKind.QUERY_TEXT))
      .orElse(Pair.of("", QueryVarKind.EMPTY));
  }

  private Optional<QueryTextSetupData> getQueryTextAssignment(List<QueryTextSetupData> queryObjectTextAssignments,
                                                              BSLParser.ExpressionContext varAssign, String varName) {
    return queryObjectTextAssignments.stream()
      .filter(queryTextSetupData -> queryTextSetupData.getRightExpr().getStop().getLine() > varAssign.getStop().getLine())
      .filter(queryTextSetupData -> queryTextSetupData.getNewQueryExpr()
        .filter(callParamListContext -> callParamListContext.getText().equalsIgnoreCase(varName))
        .isPresent())
      .findFirst()
      ;
  }

  private void checkAssignment(CodeBlockContext codeBlock,
                               Map<String, ParameterContext> params,
                               QueryTextSetupData queryTextAssignment) {

    final var callStatements = codeBlockCallStatements.computeIfAbsent(codeBlock,
      MissingQueryParameterDiagnostic::getIsSetParameterCallStatements);

    final var allParams = params.values();

    if (!callStatements.isEmpty()) {

      final var queryVarName = queryTextAssignment.getVarName();
      final var usedParams = callStatements.stream()
        .map(callStatementContext -> findAppropriateParamFromSetParameter(callStatementContext, queryVarName, params))
        .flatMap(Optional::stream)
        .collect(Collectors.toList());
      allParams.removeAll(usedParams);
    }

    allParams.forEach(node -> diagnosticStorage.addDiagnostic(node,
      info.getMessage(node.PARAMETER_IDENTIFIER().getText())));
  }

  private static List<BSLParser.CallStatementContext> getIsSetParameterCallStatements(CodeBlockContext codeBlock) {
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

  private static Optional<ParameterContext> findAppropriateParamFromSetParameter(BSLParser.CallStatementContext callStatementContext,
                                                                                 String queryVarName,
                                                                                 Map<String, ParameterContext> params) {
    final var callCtx = Optional.of(callStatementContext);
    return callCtx
      .map(BSLParser.CallStatementContext::IDENTIFIER)
      .map(ParseTree::getText)
      .filter(queryVarName::equalsIgnoreCase)
      .flatMap(dummy -> findAppropriateParamFromSetParameterMethod(callCtx, params));
  }

  private static Optional<ParameterContext> findAppropriateParamFromSetParameterMethod(Optional<BSLParser.CallStatementContext> callCtx,
                                                                                       Map<String, ParameterContext> params) {
    return callCtx.map(BSLParser.CallStatementContext::accessCall)
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
      .flatMap(firstValueForSetParameterMethod -> findParameterByName(params, firstValueForSetParameterMethod));
  }

  private static Optional<ParameterContext> findParameterByName(Map<String, ParameterContext> params,
                                                                String firstValueForSetParameterMethod) {
    return params.entrySet().stream()
      .filter(entry -> entry.getKey().equalsIgnoreCase(firstValueForSetParameterMethod))
      .map(Map.Entry::getValue).findFirst();
  }
}
