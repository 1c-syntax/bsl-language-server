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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 1,
  tags = {DiagnosticTag.DESIGN}
)

public class UnusedParametersDiagnostic extends AbstractVisitorDiagnostic {

  @Override
  public ParseTree visitSubCodeBlock(BSLParser.SubCodeBlockContext ctx) {

    if (ctx.codeBlock().getChildCount() == 0
      || itsHandler(ctx)) {
      return ctx;
    }

    List<String> paramsNames = Trees.findAllRuleNodes(ctx.getParent(), BSLParser.RULE_param)
      .stream()
      .map(node -> ((BSLParser.ParamContext) node).IDENTIFIER().getText().toLowerCase(Locale.getDefault()))
      .collect(Collectors.toList());

    Trees.findAllRuleNodes(ctx, BSLParser.RULE_lValue)
      .stream()
      .map(node -> ((BSLParser.LValueContext) node).IDENTIFIER())
      .filter(Objects::nonNull)
      .forEach(node ->
        paramsNames.remove((node.getText().toLowerCase(Locale.getDefault())))
      );

    Trees.findAllRuleNodes(ctx, BSLParser.RULE_complexIdentifier)
      .stream()
      .map(node -> ((BSLParser.ComplexIdentifierContext) node).IDENTIFIER())
      .filter(Objects::nonNull)
      .forEach(node ->
        paramsNames.remove(node.getText().toLowerCase(Locale.getDefault()))
      );


    Trees.findAllRuleNodes(ctx.getParent(), BSLParser.RULE_param)
      .stream()
      .filter(param -> paramsNames.contains(((BSLParser.ParamContext) param).IDENTIFIER().getText().toLowerCase()))
      .forEach(param ->
        diagnosticStorage.addDiagnostic(
          ((BSLParser.ParamContext) param).IDENTIFIER(),
          getDiagnosticMessage(((BSLParser.ParamContext) param).IDENTIFIER().getText())
        )
      );

    return ctx;
  }

  private boolean itsHandler(BSLParser.SubCodeBlockContext ctx) {
    //todo handlers from context
    return false;
  }

}
