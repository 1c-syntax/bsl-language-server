/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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
import com.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.RelatedInformation;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParser.NewExpressionContext;
import jakarta.annotation.PostConstruct;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Leon Chagelishvili &lt;lChagelishvily@gmail.com&gt;
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

  private String relatedMessage;

  @PostConstruct
  public void init() {
    relatedMessage = this.info.getResourceString("nestedConstructorRelatedMessage");
  }

  @Override
  public ParseTree visitNewExpression(NewExpressionContext ctx) {

    BSLParser.TypeNameContext typeName = ctx.typeName();

    if (typeName == null
      || !(DiagnosticHelper.isStructureType(typeName) || DiagnosticHelper.isFixedStructureType(typeName))
    ) {
      return super.visitNewExpression(ctx);
    }

    BSLParser.DoCallContext structureDoCallContext = ctx.doCall();

    if (structureDoCallContext == null) {
      return ctx;
    }

    if (structureDoCallContext.callParamList().callParam().size() <= 1) {
      return super.visitNewExpression(ctx);
    }

    List<DiagnosticRelatedInformation> relatedInformation = new ArrayList<>();
    relatedInformation.add(RelatedInformation.create(
      documentContext.getUri(),
      Ranges.create(ctx),
      relatedMessage
    ));
    structureDoCallContext.callParamList().callParam().stream()
      .filter(tree -> tree.start.getType() == BSLParser.NEW_KEYWORD)
      .map(tree -> Trees.findAllRuleNodes(tree, BSLParser.RULE_newExpression))
      .filter(tree -> !tree.isEmpty())
      .map(tree -> (ParseTree) tree.toArray()[0])
      .map(tree -> (NewExpressionContext) tree)
      .filter(NestedConstructorsInStructureDeclarationDiagnostic::hasParams)
      .map(newContext -> RelatedInformation.create(
        documentContext.getUri(),
        Ranges.create(newContext),
        relatedMessage
      ))
      .collect(Collectors.toCollection(() -> relatedInformation));

    if (relatedInformation.size() > 1) {
      diagnosticStorage.addDiagnostic(ctx, relatedInformation);
    }

    return super.visitNewExpression(ctx);
  }

  private static boolean hasParams(NewExpressionContext newContext) {
    BSLParser.DoCallContext doCallContext = newContext.doCall();
    return doCallContext != null
      && doCallContext.callParamList().callParam().stream().anyMatch(param -> param.getChildCount() > 0);
  }

}
