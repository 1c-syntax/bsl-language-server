/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
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
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 20,
  activatedByDefault = false,
  tags = {
    DiagnosticTag.BRAINOVERLOAD
  }
)
public class TooManyReturnsDiagnostic extends AbstractVisitorDiagnostic {
  private static final int MAX_RETURNS_COUNT = 3;
  private static final int MAX_RELATION_TEXT_LENGTH = 20;

  @DiagnosticParameter(
    type = Integer.class,
    defaultValue = "" + MAX_RETURNS_COUNT
  )
  private int maxReturnsCount = MAX_RETURNS_COUNT;

  private static String leftSubStr(String inputString) {
    if (inputString.length() < MAX_RELATION_TEXT_LENGTH) {
      return inputString;
    }
    return inputString.substring(0, MAX_RELATION_TEXT_LENGTH);
  }

  private String getRelatedMessage(BSLParser.ReturnStatementContext context) {
    if (context.getChildCount() > 1) {
      return leftSubStr(documentContext.getText(Ranges.create(context)));
    } else {
      return "+1";
    }
  }

  @Override
  public ParseTree visitSub(BSLParser.SubContext ctx) {
    Optional<MethodSymbol> optionalMethodSymbol = documentContext.getSymbolTree().getMethodSymbol(ctx);
    optionalMethodSymbol.ifPresent((MethodSymbol methodSymbol) -> {
      Collection<ParseTree> statements = Trees.findAllRuleNodes(ctx, BSLParser.RULE_returnStatement);

      if (statements.size() > maxReturnsCount) {
        List<DiagnosticRelatedInformation> relatedInformation = new ArrayList<>();
        statements.stream()
          .map(context -> RelatedInformation.create(
            documentContext.getUri(),
            Ranges.create((BSLParser.ReturnStatementContext) context),
            getRelatedMessage((BSLParser.ReturnStatementContext) context)
          )).collect(Collectors.toCollection(() -> relatedInformation));

        diagnosticStorage.addDiagnostic(
          methodSymbol.getSubNameRange(),
          info.getMessage(methodSymbol.getName(), statements.size(), maxReturnsCount),
          relatedInformation
        );
      }
    });
    return ctx;
  }
}
