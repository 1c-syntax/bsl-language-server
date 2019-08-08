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
package org.github._1c_syntax.bsl.languageserver.diagnostics;

import org.antlr.v4.runtime.tree.ParseTree;
import org.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import org.github._1c_syntax.bsl.parser.BSLParser;
import org.github._1c_syntax.bsl.parser.BSLParserRuleContext;

import java.util.Map;
import java.util.Optional;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.CRITICAL,
  minutesToFix = 15
)
public class CognitiveComplexityDiagnostic extends AbstractVisitorDiagnostic {

  private static final int COMPLEXITY_THRESHOLD = 15;
  private static final boolean CHECK_MODULE_BODY = true;

  @DiagnosticParameter(
    type = Integer.class,
    defaultValue = "" + COMPLEXITY_THRESHOLD,
    description = "Допустимая когнитивная сложность метода"
  )
  private int complexityThreshold = COMPLEXITY_THRESHOLD;

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + CHECK_MODULE_BODY,
    description = "Проверять тело модуля"
  )
  private boolean checkModuleBody = CHECK_MODULE_BODY;

  private boolean fileCodeBlockChecked;

  @Override
  public void configure(Map<String, Object> configuration) {
    if (configuration == null) {
      return;
    }
    complexityThreshold = (Integer) configuration.get("complexityThreshold");
    checkModuleBody = (Boolean) configuration.get("checkModuleBody");
  }

  @Override
  public ParseTree visitSub(BSLParser.SubContext ctx) {
    Optional<MethodSymbol> optionalMethodSymbol = documentContext.getMethodSymbol(ctx);
    optionalMethodSymbol.ifPresent((MethodSymbol methodSymbol) -> {
      Integer methodComplexity = documentContext.getMethodsCognitiveComplexity().get(methodSymbol);

      if (methodComplexity > complexityThreshold) {
        diagnosticStorage.addDiagnostic(
          getSubNameContext(methodSymbol),
          getDiagnosticMessage(methodSymbol.getName(), methodComplexity, complexityThreshold)
        );
      }

    });
    return ctx;
  }

  @Override
  public ParseTree visitFileCodeBlockBeforeSub(BSLParser.FileCodeBlockBeforeSubContext ctx) {
    checkFileCodeBlock(ctx);
    fileCodeBlockChecked = ctx.getChildCount() > 0;
    return ctx;
  }

  @Override
  public ParseTree visitFileCodeBlock(BSLParser.FileCodeBlockContext ctx) {
    if (!fileCodeBlockChecked) {
      checkFileCodeBlock(ctx);
    }
    return ctx;
  }

  private void checkFileCodeBlock(BSLParserRuleContext ctx) {
    if (!checkModuleBody) {
      return;
    }

    Integer fileCodeBlockComplexity = documentContext.getFileCodeBlockCognitiveComplexity();

    if (fileCodeBlockComplexity > complexityThreshold) {
      diagnosticStorage.addDiagnostic(
        ctx.getStart(),
        getDiagnosticMessage("body", fileCodeBlockComplexity, complexityThreshold)
      );
    }
  }

  // TODO: ASTHelper? BSPParserRuleContext static methods?
  private static BSLParser.SubNameContext getSubNameContext(MethodSymbol methodSymbol) {
    BSLParser.SubNameContext subNameContext;
    if (methodSymbol.isFunction()) {
      subNameContext = ((BSLParser.FunctionContext) methodSymbol.getNode()).funcDeclaration().subName();
    } else {
      subNameContext = ((BSLParser.ProcedureContext) methodSymbol.getNode()).procDeclaration().subName();
    }
    return subNameContext;
  }
}
