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
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;

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

  private static final Pattern patternNewExpression = CaseInsensitivePattern.compile(
    "^(COMОбъект|COMObject|Почта|Mail)"
  );

  private static final Pattern patternTypePlatform = CaseInsensitivePattern.compile(
    "Linux_x86|Windows|MacOS"
  );

  /**
   * Проверяем все объявления на тип COMОбъект или Почта. Если условие выше (обрабатывается вся
   * цепочка) с проверкой ТипПлатформы = Linux не найдено в методе, то диагностика срабатывает.
   * Пример:
   * Компонента = Новый COMОбъект("System.Text.UTF8Encoding");
   */
  @Override
  public ParseTree visitNewExpression(BSLParser.NewExpressionContext ctx) {
    var typeNameContext = ctx.typeName();
    if (typeNameContext == null) {
      return super.visitNewExpression(ctx);
    }
    var matcherTypeName = patternNewExpression.matcher(typeNameContext.getText());
    // ищем условие выше, пока не дойдем до null

    if (matcherTypeName.find() && !isFindIfBranchWithLinuxCondition(ctx)) {
      diagnosticStorage.addDiagnostic(ctx, info.getMessage(typeNameContext.getText()));
    }
    return super.visitNewExpression(ctx);
  }

  private static boolean isFindIfBranchWithLinuxCondition(BSLParserRuleContext element) {
    BSLParserRuleContext ancestor = Trees.getAncestorByRuleIndex(element, BSLParser.RULE_ifBranch);
    if (ancestor == null) {
      return false;
    }
    String content = ancestor.getText();
    var matcher = patternTypePlatform.matcher(content);
    if (matcher.find()) {
      return true;
    }
    return isFindIfBranchWithLinuxCondition(ancestor);
  }

}
