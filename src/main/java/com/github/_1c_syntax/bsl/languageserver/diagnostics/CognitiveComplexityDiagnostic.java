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

import com.github._1c_syntax.bsl.languageserver.context.computer.CognitiveComplexityComputer;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.CRITICAL,
  minutesToFix = 15,
  tags = {
    DiagnosticTag.BRAINOVERLOAD
  }
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
    complexityThreshold = (int) configuration.getOrDefault("complexityThreshold", complexityThreshold);
    checkModuleBody = (boolean) configuration.getOrDefault("checkModuleBody", checkModuleBody);
  }

  @Override
  public ParseTree visitSub(BSLParser.SubContext ctx) {
    Optional<MethodSymbol> optionalMethodSymbol = documentContext.getMethodSymbol(ctx);
    optionalMethodSymbol.ifPresent((MethodSymbol methodSymbol) -> {
      Integer methodComplexity = documentContext.getCognitiveComplexityData().getMethodsComplexity().get(methodSymbol);

      if (methodComplexity > complexityThreshold) {

        List<DiagnosticRelatedInformation> relatedInformation = new ArrayList<>();

        relatedInformation.add(RangeHelper.createRelatedInformation(
          documentContext.getUri(),
          methodSymbol.getSubNameRange(),
          getDiagnosticMessage(methodSymbol.getName(), methodComplexity, complexityThreshold)
        ));

        List<CognitiveComplexityComputer.SecondaryLocation> secondaryLocations =
          documentContext.getCognitiveComplexityData().getMethodsComplexitySecondaryLocations().get(methodSymbol);

        secondaryLocations.stream()
          .map((CognitiveComplexityComputer.SecondaryLocation secondaryLocation) ->
            RangeHelper.createRelatedInformation(
              documentContext.getUri(),
              secondaryLocation.getRange(),
              secondaryLocation.getMessage()
            )
          )
          .collect(Collectors.toCollection(() -> relatedInformation));

        diagnosticStorage.addDiagnostic(
          methodSymbol.getSubNameRange(),
          getDiagnosticMessage(methodSymbol.getName(), methodComplexity, complexityThreshold),
          relatedInformation
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

    Integer fileCodeBlockComplexity = documentContext.getCognitiveComplexityData().getFileCodeBlockComplexity();

    if (fileCodeBlockComplexity > complexityThreshold) {

      List<DiagnosticRelatedInformation> relatedInformation = new ArrayList<>();

      relatedInformation.add(RangeHelper.createRelatedInformation(
        documentContext.getUri(),
        RangeHelper.newRange(ctx.getStart()),
        getDiagnosticMessage("body", fileCodeBlockComplexity, complexityThreshold)
      ));

      List<CognitiveComplexityComputer.SecondaryLocation> secondaryLocations =
        documentContext.getCognitiveComplexityData().getFileBlockComplexitySecondaryLocations();

      secondaryLocations.stream()
        .map((CognitiveComplexityComputer.SecondaryLocation secondaryLocation) ->
          RangeHelper.createRelatedInformation(
            documentContext.getUri(),
            secondaryLocation.getRange(),
            secondaryLocation.getMessage()
          )
        )
        .collect(Collectors.toCollection(() -> relatedInformation));

      diagnosticStorage.addDiagnostic(
        ctx.getStart(),
        getDiagnosticMessage("body", fileCodeBlockComplexity, complexityThreshold),
        relatedInformation
      );
    }
  }

}
