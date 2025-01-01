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

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.RelatedInformation;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.Range;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.DESIGN,
    DiagnosticTag.BADPRACTICE
  }

)
public class FunctionReturnsSamePrimitiveDiagnostic extends AbstractVisitorDiagnostic {

  private static final String KEY_MESSAGE = "diagnosticMessageReturnStatement";
  private static final Pattern pattern = CaseInsensitivePattern.compile(
    "^(подключаемый|attachable)_"
  );

  private static final boolean SKIP_ATTACHABLE = true;
  private static final boolean CASE_SENSITIVE_FOR_STRING = false;

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + SKIP_ATTACHABLE
  )
  private boolean skipAttachable = SKIP_ATTACHABLE;

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + CASE_SENSITIVE_FOR_STRING
  )
  private boolean caseSensitiveForString = CASE_SENSITIVE_FOR_STRING;

  @Override
  public ParseTree visitFunction(BSLParser.FunctionContext ctx) {

    if (skipAttachable) {
      // Исключаем подключаемые методы
      var abortCheck = Optional.ofNullable(ctx.funcDeclaration())
        .map(BSLParser.FuncDeclarationContext::subName)
        .filter(subNameContext -> pattern.matcher(subNameContext.getText()).find())
        .isPresent();
      if (abortCheck) {
        return ctx;
      }
    }

    var tree = Trees.findAllRuleNodes(ctx, BSLParser.RULE_returnStatement);
    if (tree.size() > 1) {
      var expressions = tree.stream()
        .map(BSLParser.ReturnStatementContext.class::cast)
        .map(BSLParser.ReturnStatementContext::expression)
        .flatMap(Stream::ofNullable)
        .collect(Collectors.toList());

      expressions.stream()
        .map(expression -> Trees.findAllRuleNodes(expression, BSLParser.RULE_complexIdentifier))
        .filter(Collection::isEmpty)
        .findAny()
        .ifPresent(
          parseTrees -> checkPrimitiveValue(ctx, tree, expressions)
        );
    }
    return ctx;
  }

  private void checkPrimitiveValue(
    BSLParser.FunctionContext ctx, Collection<ParseTree> tree, List<BSLParser.ExpressionContext> expressions) {

    var count = expressions.stream()
      .map(this::getExpressionText)
      .distinct()
      .count();

    if (count == 1) {
      var relatedInformation = tree.stream()
        .map(BSLParser.ReturnStatementContext.class::cast)
        .map(statement -> RelatedInformation.create(
          documentContext.getUri(),
          Ranges.create(statement.getStart()),
          info.getResourceString(KEY_MESSAGE)))
        .collect(Collectors.toList());
      diagnosticStorage.addDiagnostic(getSubNameRange(ctx), relatedInformation);
    }

  }

  private String getExpressionText(BSLParser.ExpressionContext expression) {
    if (caseSensitiveForString && Trees.nodeContains(expression, BSLParser.RULE_string)) {
      return expression.getText();
    }
    return expression.getText().toUpperCase(Locale.ENGLISH);
  }

  private Range getSubNameRange(BSLParserRuleContext ctx) {
    return Optional.ofNullable(Trees.getAncestorByRuleIndex(ctx, BSLParser.RULE_sub))
      .map(BSLParser.SubContext.class::cast)
      .flatMap(context -> documentContext.getSymbolTree().getMethodSymbol(context))
      .map(MethodSymbol::getSubNameRange)
      .orElse(Ranges.create(ctx.getStart()));
  }

}
