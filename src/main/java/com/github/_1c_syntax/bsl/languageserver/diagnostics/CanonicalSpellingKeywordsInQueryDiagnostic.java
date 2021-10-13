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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.providers.CodeActionProvider;
import com.github._1c_syntax.bsl.languageserver.utils.SDBLKeywords;
import com.github._1c_syntax.bsl.parser.SDBLParser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.TextEdit;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.STANDARD
  },
  scope = DiagnosticScope.BSL

)
public class CanonicalSpellingKeywordsInQueryDiagnostic extends AbstractSDBLVisitorDiagnostic implements QuickFixProvider {

  private static final Map<Integer, List<String>> canonicalKeywords = getKeywords();

  private static Map<Integer, List<String>> getKeywords() {
    Map<Integer, List<String>> result = new HashMap<>();

    result.put(SDBLParser.SELECT, List.of(SDBLKeywords.SELECT_RU, SDBLKeywords.SELECT_EN));
    result.put(SDBLParser.AS, List.of(SDBLKeywords.AS_RU, SDBLKeywords.AS_EN));
    result.put(SDBLParser.ALLOWED, List.of(SDBLKeywords.ALLOWED_RU, SDBLKeywords.ALLOWED_EN));
    result.put(SDBLParser.AND, List.of(SDBLKeywords.AND_RU, SDBLKeywords.AND_EN));
    result.put(SDBLParser.ASC, List.of(SDBLKeywords.ASC_RU, SDBLKeywords.ASC_EN));
    result.put(SDBLParser.AUTOORDER, List.of(SDBLKeywords.AUTOORDER_RU, SDBLKeywords.AUTOORDER_EN));
    result.put(SDBLParser.BETWEEN, List.of(SDBLKeywords.BETWEEN_RU, SDBLKeywords.BETWEEN_EN));
    result.put(SDBLParser.CASE, List.of(SDBLKeywords.CASE_RU, SDBLKeywords.CASE_EN));
    result.put(SDBLParser.CAST, List.of(SDBLKeywords.CAST_RU, SDBLKeywords.CAST_EN));
    result.put(SDBLParser.DESC, List.of(SDBLKeywords.DESC_RU, SDBLKeywords.DESC_EN));
    result.put(SDBLParser.DISTINCT, List.of(SDBLKeywords.DISTINCT_RU, SDBLKeywords.DISTINCT_EN));
    result.put(SDBLParser.DROP, List.of(SDBLKeywords.DROP_RU, SDBLKeywords.DROP_EN));
    result.put(SDBLParser.ELSE, List.of(SDBLKeywords.ELSE_RU, SDBLKeywords.ELSE_EN));
    result.put(SDBLParser.END, List.of(SDBLKeywords.END_RU, SDBLKeywords.END_EN));
    result.put(SDBLParser.ESCAPE, List.of(SDBLKeywords.ESCAPE_RU, SDBLKeywords.ESCAPE_EN));
    result.put(SDBLParser.FALSE, List.of(SDBLKeywords.FALSE_RU, SDBLKeywords.FALSE_EN));
    result.put(SDBLParser.FROM, List.of(SDBLKeywords.FROM_RU, SDBLKeywords.FROM_EN));
    result.put(SDBLParser.HAVING, List.of(SDBLKeywords.HAVING_RU, SDBLKeywords.HAVING_EN));
    result.put(SDBLParser.HIERARCHY, List.of(SDBLKeywords.HIERARCHY_RU, SDBLKeywords.HIERARCHY_EN));
    result.put(SDBLParser.INTO, List.of(SDBLKeywords.INTO_RU, SDBLKeywords.INTO_EN));
    result.put(SDBLParser.IS, List.of(SDBLKeywords.IS_RU, SDBLKeywords.IS_EN));
    result.put(SDBLParser.ISNULL, List.of(SDBLKeywords.ISNULL_RU, SDBLKeywords.ISNULL_EN));
    result.put(SDBLParser.LIKE, List.of(SDBLKeywords.LIKE_RU, SDBLKeywords.LIKE_EN));
    result.put(SDBLParser.NOT, List.of(SDBLKeywords.NOT_RU, SDBLKeywords.NOT_EN));
    result.put(SDBLParser.NULL, List.of(SDBLKeywords.NULL_RU, SDBLKeywords.NULL_EN));
    result.put(SDBLParser.OF, List.of(SDBLKeywords.OF_EN)); // нет на кириллице
    result.put(SDBLParser.OR, List.of(SDBLKeywords.OR_RU, SDBLKeywords.OR_EN));
    result.put(SDBLParser.OVERALL, List.of(SDBLKeywords.OVERALL_RU, SDBLKeywords.OVERALL_EN));
    result.put(SDBLParser.THEN, List.of(SDBLKeywords.THEN_RU, SDBLKeywords.THEN_EN));
    result.put(SDBLParser.TOP, List.of(SDBLKeywords.TOP_RU, SDBLKeywords.TOP_EN));
    result.put(SDBLParser.TOTALS, List.of(SDBLKeywords.TOTALS_RU, SDBLKeywords.TOTALS_EN));
    result.put(SDBLParser.TRUE, List.of(SDBLKeywords.TRUE_RU, SDBLKeywords.TRUE_EN));
    result.put(SDBLParser.UNDEFINED, List.of(SDBLKeywords.UNDEFINED_RU, SDBLKeywords.UNDEFINED_EN));
    result.put(SDBLParser.WHEN, List.of(SDBLKeywords.WHEN_RU, SDBLKeywords.WHEN_EN));
    result.put(SDBLParser.WHERE, List.of(SDBLKeywords.WHERE_RU, SDBLKeywords.WHERE_EN));
    result.put(SDBLParser.AVG, List.of(SDBLKeywords.AVG_RU, SDBLKeywords.AVG_EN));
    result.put(SDBLParser.BEGINOFPERIOD, List.of(SDBLKeywords.BEGINOFPERIOD_RU, SDBLKeywords.BEGINOFPERIOD_EN));
    result.put(SDBLParser.BOOLEAN, List.of(SDBLKeywords.BOOLEAN_RU, SDBLKeywords.BOOLEAN_EN));
    result.put(SDBLParser.COUNT, List.of(SDBLKeywords.COUNT_RU, SDBLKeywords.COUNT_EN));
    result.put(SDBLParser.DATE, List.of(SDBLKeywords.DATE_RU, SDBLKeywords.DATE_EN));
    result.put(SDBLParser.DATEADD, List.of(SDBLKeywords.DATEADD_RU, SDBLKeywords.DATEADD_EN));
    result.put(SDBLParser.DATEDIFF, List.of(SDBLKeywords.DATEDIFF_RU, SDBLKeywords.DATEDIFF_EN));
    result.put(SDBLParser.DATETIME, List.of(SDBLKeywords.DATETIME_RU, SDBLKeywords.DATETIME_EN));
    result.put(SDBLParser.DAY, List.of(SDBLKeywords.DAY_RU, SDBLKeywords.DAY_EN));
    result.put(SDBLParser.DAYOFYEAR, List.of(SDBLKeywords.DAYOFYEAR_RU, SDBLKeywords.DAYOFYEAR_EN));
    result.put(SDBLParser.EMPTYTABLE, List.of(SDBLKeywords.EMPTYTABLE_RU, SDBLKeywords.EMPTYTABLE_EN));
    result.put(SDBLParser.ENDOFPERIOD, List.of(SDBLKeywords.ENDOFPERIOD_RU, SDBLKeywords.ENDOFPERIOD_EN));
    result.put(SDBLParser.HALFYEAR, List.of(SDBLKeywords.HALFYEAR_RU, SDBLKeywords.HALFYEAR_EN));
    result.put(SDBLParser.HOUR, List.of(SDBLKeywords.HOUR_RU, SDBLKeywords.HOUR_EN));
    result.put(SDBLParser.MAX, List.of(SDBLKeywords.MAX_RU, SDBLKeywords.MAX_EN));
    result.put(SDBLParser.MIN, List.of(SDBLKeywords.MIN_RU, SDBLKeywords.MIN_EN));
    result.put(SDBLParser.MINUTE, List.of(SDBLKeywords.MINUTE_RU, SDBLKeywords.MINUTE_EN));
    result.put(SDBLParser.MONTH, List.of(SDBLKeywords.MONTH_RU, SDBLKeywords.MONTH_EN));
    result.put(SDBLParser.NUMBER, List.of(SDBLKeywords.NUMBER_RU, SDBLKeywords.NUMBER_EN));
    result.put(SDBLParser.QUARTER, List.of(SDBLKeywords.QUARTER_RU, SDBLKeywords.QUARTER_EN));
    result.put(SDBLParser.ONLY, List.of(SDBLKeywords.ONLY_RU, SDBLKeywords.ONLY_EN));
    result.put(SDBLParser.PERIODS, List.of(SDBLKeywords.PERIODS_RU, SDBLKeywords.PERIODS_EN));
    result.put(SDBLParser.REFS, List.of(SDBLKeywords.REFS_RU, SDBLKeywords.REFS_EN));
    result.put(SDBLParser.PRESENTATION, List.of(SDBLKeywords.PRESENTATION_RU, SDBLKeywords.PRESENTATION_EN));
    result.put(SDBLParser.RECORDAUTONUMBER, List.of(SDBLKeywords.RECORDAUTONUMBER_RU, SDBLKeywords.RECORDAUTONUMBER_EN));
    result.put(SDBLParser.REFPRESENTATION, List.of(SDBLKeywords.REFPRESENTATION_RU, SDBLKeywords.REFPRESENTATION_EN));
    result.put(SDBLParser.SECOND, List.of(SDBLKeywords.SECOND_RU, SDBLKeywords.SECOND_EN));
    result.put(SDBLParser.STRING, List.of(SDBLKeywords.STRING_RU, SDBLKeywords.STRING_EN));
    result.put(SDBLParser.SUBSTRING, List.of(SDBLKeywords.SUBSTRING_RU, SDBLKeywords.SUBSTRING_EN));
    result.put(SDBLParser.SUM, List.of(SDBLKeywords.SUM_RU, SDBLKeywords.SUM_EN));
    result.put(SDBLParser.TENDAYS, List.of(SDBLKeywords.TENDAYS_RU, SDBLKeywords.TENDAYS_EN));
    result.put(SDBLParser.TYPE, List.of(SDBLKeywords.TYPE_RU, SDBLKeywords.TYPE_EN));
    result.put(SDBLParser.VALUE, List.of(SDBLKeywords.VALUE_RU, SDBLKeywords.VALUE_EN));
    result.put(SDBLParser.VALUETYPE, List.of(SDBLKeywords.VALUETYPE_RU, SDBLKeywords.VALUETYPE_EN));
    result.put(SDBLParser.WEEK, List.of(SDBLKeywords.WEEK_RU, SDBLKeywords.WEEK_EN));
    result.put(SDBLParser.WEEKDAY, List.of(SDBLKeywords.WEEKDAY_RU, SDBLKeywords.WEEKDAY_EN));
    result.put(SDBLParser.YEAR, List.of(SDBLKeywords.YEAR_RU, SDBLKeywords.YEAR_EN));
    result.put(SDBLParser.INDEX, List.of(SDBLKeywords.INDEX_RU, SDBLKeywords.INDEX_EN));
    result.put(SDBLParser.GROUP, List.of(SDBLKeywords.GROUP_RU, SDBLKeywords.GROUP_EN));
    result.put(SDBLParser.ORDER, List.of(SDBLKeywords.ORDER_RU, SDBLKeywords.ORDER_EN));
    result.put(SDBLParser.GROUPEDBY, List.of(SDBLKeywords.GROUPEDBY_RU, SDBLKeywords.GROUPEDBY_EN));
    result.put(SDBLParser.GROUPING, List.of(SDBLKeywords.GROUPING_RU, SDBLKeywords.GROUPING_EN));
    result.put(SDBLParser.BY_EN, List.of(SDBLKeywords.ON_EN)); // Странное
    result.put(SDBLParser.PO_RU, List.of(SDBLKeywords.ON_RU)); // Cтранное
    result.put(SDBLParser.ON_EN, List.of(SDBLKeywords.ON_EN));
    result.put(SDBLParser.SET, List.of(SDBLKeywords.SET_RU, SDBLKeywords.SET_EN));
    result.put(SDBLParser.RIGHT, List.of(SDBLKeywords.RIGHT_RU, SDBLKeywords.RIGHT_EN));
    result.put(SDBLParser.LEFT, List.of(SDBLKeywords.LEFT_RU, SDBLKeywords.LEFT_EN));
    result.put(SDBLParser.INNER, List.of(SDBLKeywords.INNER_RU, SDBLKeywords.INNER_EN));
    result.put(SDBLParser.FULL, List.of(SDBLKeywords.FULL_RU, SDBLKeywords.FULL_EN));
    result.put(SDBLParser.JOIN, List.of(SDBLKeywords.JOIN_RU, SDBLKeywords.JOIN_EN));
    result.put(SDBLParser.OUTER, List.of(SDBLKeywords.OUTER_RU, SDBLKeywords.OUTER_EN));
    result.put(SDBLParser.FOR, List.of(SDBLKeywords.FOR_RU, SDBLKeywords.FOR_EN));
    result.put(SDBLParser.UPDATE, List.of(SDBLKeywords.UPDATE_RU, SDBLKeywords.UPDATE_EN));
    result.put(SDBLParser.ALL, List.of(SDBLKeywords.ALL_RU, SDBLKeywords.ALL_EN));
    result.put(SDBLParser.UNION, List.of(SDBLKeywords.UNION_RU, SDBLKeywords.UNION_EN));
    result.put(SDBLParser.HIERARCHY_FOR_IN, List.of(SDBLKeywords.IN_HIERARCHY_RU, SDBLKeywords.HIERARCHY_EN));
    result.put(SDBLParser.IN, List.of(SDBLKeywords.IN_RU, SDBLKeywords.IN_EN));

    return result;

  }


  @Override
  public ParseTree visitQueryPackage(SDBLParser.QueryPackageContext ctx) {

    ctx.getTokens().parallelStream().filter((Token t) ->
        canonicalKeywords.get(t.getType()) != null && !canonicalKeywords.get(t.getType()).contains(t.getText()))
      .forEach(token ->
        diagnosticStorage.addDiagnostic(
          token,
          info.getMessage(token.getText())
        ));

    return ctx;

  }

  @Override
  public List<CodeAction> getQuickFixes(List<Diagnostic> diagnostics, CodeActionParams params, DocumentContext documentContext) {

    List<TextEdit> textEdits = diagnostics.stream()
      .map(Diagnostic::getRange)
      .map(range -> new TextEdit(range, documentContext.getText(range).toUpperCase(Locale.ENGLISH)))
      .collect(Collectors.toList());

    return CodeActionProvider.createCodeActions(
      textEdits,
      info.getResourceString("quickFixMessage"),
      documentContext.getUri(),
      diagnostics
    );

  }

}
