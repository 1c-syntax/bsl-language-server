/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.CRITICAL,
  scope = DiagnosticScope.BSL,
  minutesToFix = 30,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.LOCKINOS
  }
)
public class UsingObjectNotAvailableUnixDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern patternNewExpression = Pattern.compile(
    "^(COMОбъект|COMObject|Почта|Mail)",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern patternTypePlatform = Pattern.compile(
    "Linux_x86|Windows|MacOS",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  public UsingObjectNotAvailableUnixDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  /**
   * Проверяем все объявления на тип COMОбъект или Почта. Если условие выше (обрабатывается вся
   * цепочка) с проверкой ТипПлатформы = Linux не найдено в методе, то диагностика срабатывает.
   * Пример:
   * Компонента = Новый COMОбъект("System.Text.UTF8Encoding");
   */
  @Override
  public ParseTree visitNewExpression(BSLParser.NewExpressionContext ctx) {
    BSLParser.TypeNameContext typeNameContext = ctx.typeName();
    if (typeNameContext == null) {
      return super.visitNewExpression(ctx);
    }
    Matcher matcherTypeName = patternNewExpression.matcher(typeNameContext.getText());
    // ищем условие выше, пока не дойдем до null

    if (matcherTypeName.find() && !isFindIfBranchWithLinuxCondition(ctx)) {
      diagnosticStorage.addDiagnostic(ctx, info.getDiagnosticMessage(typeNameContext.getText()));
    }
    return super.visitNewExpression(ctx);
  }

  private static boolean isFindIfBranchWithLinuxCondition(ParserRuleContext element) {
    ParserRuleContext ancestor = Trees.getAncestorByRuleIndex(element, BSLParser.RULE_ifBranch);
    if (ancestor == null) {
      return false;
    }
    String content = ancestor.getText();
    Matcher matcher = patternTypePlatform.matcher(content);
    if (matcher.find()) {
      return true;
    }
    return isFindIfBranchWithLinuxCondition(ancestor);
  }

}
