/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParser.GlobalMethodCallContext;
import lombok.val;
import org.antlr.v4.runtime.tree.ParseTree;
import java.util.ArrayList;
import java.util.Optional;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  scope = DiagnosticScope.BSL,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.BADPRACTICE
  }

)
public class FormDataToValueDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern MESSAGE_PATTERN = Pattern.compile(
    "ДанныеФормыВЗначение|FormDataToValue",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  private static final Pattern DIRECTIVE_PATTERN = Pattern.compile(
    "&НаСервереБезКонтекста|&НаКлиентеНаСервереБезКонтекста|&AtServerNoContext|&AtClientAtServerNoContext",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  public FormDataToValueDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  public ParseTree visitSub(BSLParser.SubContext ctx) {

    Optional<MethodSymbol> methodSymbol = documentContext.getMethodSymbol(ctx);
    val directivesList = new ArrayList<ParseTree>(Trees.findAllRuleNodes(ctx, BSLParser.RULE_compilerDirective));

    if (methodSymbol.isPresent()
      && (directivesList.isEmpty() || !DIRECTIVE_PATTERN.matcher(directivesList.get(0).getText()).matches())) {

      val subNode = methodSymbol.get().getNode();
      Trees.findAllRuleNodes(subNode, BSLParser.RULE_globalMethodCall).stream()
        .map(GlobalMethodCallContext.class::cast)
        .filter(node -> MESSAGE_PATTERN.matcher(node.methodName().getText()).matches()).forEach(node -> diagnosticStorage.addDiagnostic(node.methodName()));
    }

    return super.visitSub(ctx);
  }

  @Override
  public ParseTree visitMethodCall(BSLParser.MethodCallContext ctx) {

    if (MESSAGE_PATTERN.matcher(ctx.methodName().getText()).matches()) {
      diagnosticStorage.addDiagnostic(ctx.methodName());
    }

    return super.visitMethodCall(ctx);
  }

}
