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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.Trees;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import com.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParser.NewExpressionContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Leon Chagelishvili <lChagelishvily@gmail.com>
 */
@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  scope = DiagnosticScope.ALL,
  minutesToFix = 10,
  tags = {
    DiagnosticTag.BADPRACTICE,
    DiagnosticTag.BRAINOVERLOAD
  }
)
public class NestedConstructorsInStructureDeclarationDiagnostic extends AbstractVisitorDiagnostic {

  private Collection<ParseTree> nestedNewContext = new ArrayList<>();
  private final String relatedMessage = getResourceString("nestedConstructorRelatedMessage");

  @Override
  public ParseTree visitNewExpression(NewExpressionContext ctx) {

    nestedNewContext.clear();

    // Checking that new context is structure declaration with parameters
    BSLParser.TypeNameContext typeName = ctx.typeName();
    if (typeName == null) {
      return ctx;
    }

    if (!(DiagnosticHelper.isStructureType(ctx.typeName()) || DiagnosticHelper.isFixedStructureType(ctx.typeName()))) {
      return ctx;
    }

    BSLParser.DoCallContext structureDoCallContext = ctx.doCall();
    if (structureDoCallContext == null) {
      return ctx;
    }

    // Looking for nested constructors
    structureDoCallContext.callParamList().callParam().stream()
      .filter(parseTree -> parseTree.start.getType() == BSLParser.NEW_KEYWORD)
      .forEach(parseTree -> Trees.findAllRuleNodes(parseTree, BSLParser.RULE_newExpression)
        .stream()
        .limit(1)
        .filter((ParseTree newContext) -> {
            BSLParser.DoCallContext doCallContext = ((NewExpressionContext) newContext).doCall();
            return doCallContext != null &&
              doCallContext.callParamList().callParam().stream().anyMatch(param -> param.getChildCount() > 0);
          }
        ).collect(Collectors.toCollection(() -> nestedNewContext)));

    if (nestedNewContext.isEmpty()) {
      return ctx;
    }

    List<DiagnosticRelatedInformation> relatedInformation = new ArrayList<>();

    relatedInformation.add(RangeHelper.createRelatedInformation(
      documentContext.getUri(),
      RangeHelper.newRange(ctx),
      relatedMessage
    ));

    nestedNewContext.stream()
      .map(expressionContext ->
        RangeHelper.createRelatedInformation(
          documentContext.getUri(),
          RangeHelper.newRange((BSLParser.NewExpressionContext) expressionContext),
          relatedMessage
        )
      )
      .collect(Collectors.toCollection(() -> relatedInformation));

    diagnosticStorage.addDiagnostic(ctx, relatedInformation);

    return super.visitNewExpression(ctx);
  }

}
