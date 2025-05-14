/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
import com.github._1c_syntax.bsl.parser.SDBLParser;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.SQL,
    DiagnosticTag.PERFORMANCE,
    DiagnosticTag.UNPREDICTABLE
  }

)
public class LogicalOrInJoinQuerySectionDiagnostic extends AbstractSDBLVisitorDiagnostic {

  @Override
  public ParseTree visitQuery(SDBLParser.QueryContext ctx) {
    //При посещении запроса сразу отбираем контексты соединений, которые в цикле обрабатываем отдельным методом.
    Trees.findAllRuleNodes(ctx, SDBLParser.RULE_joinPart).
      forEach(jpt -> processJoinPart((SDBLParser.JoinPartContext) jpt));

    return ctx;
  }

  private void processJoinPart(SDBLParser.JoinPartContext ctx) {

    //Инициализируем поток для коллекции условий соединения, каждое условие преобразуем в логическое выражение.
      ctx.condition.condidions.stream().map(SDBLParser.PredicateContext::logicalExpression)
        //фильтрация null отсекает условия, не содержащие составных логических конструкций.
        .filter(Objects::nonNull)
        //Каждое условие дополнительно отбирается по наличию более чем одного различных полей.
        .filter(this::isMultipleFieldsExpression)
        //По оставшимся условиям проводится цикл с поиском операторов "ИЛИ"
        // и вложенным циклом фиксации ошибки диагностики для каждого оператора
        .forEach(
          exp -> Trees.findAllTokenNodes(exp, SDBLParser.OR)
            .forEach(diagnosticStorage::addDiagnostic));

  }

  private boolean isMultipleFieldsExpression(SDBLParser.LogicalExpressionContext exp){

    //От контекста логического выражения спускаемся до контекста колонки,
    // далее поток текстового представления колонок собираем в Set.
    //По наличию в Set более чем одного элемента проверяем использование различных полей в условии
    Set<String> expFields = Trees.findAllRuleNodes(exp, SDBLParser.RULE_column).stream()
      .map(ParseTree::getText)
      .collect(Collectors.toSet());
    return expFields.size() > 1;
  }

}
