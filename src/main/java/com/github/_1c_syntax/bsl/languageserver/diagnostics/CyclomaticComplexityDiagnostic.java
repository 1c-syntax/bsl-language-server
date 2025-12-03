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

import com.github._1c_syntax.bsl.languageserver.context.computer.ComplexitySecondaryLocation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.RelatedInformation;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.CRITICAL,
  minutesToFix = 25,
  tags = {
    DiagnosticTag.BRAINOVERLOAD
  },
  extraMinForComplexity = 1
)
public class CyclomaticComplexityDiagnostic extends AbstractVisitorDiagnostic {
  private static final int COMPLEXITY_THRESHOLD = 20;
  private static final boolean CHECK_MODULE_BODY = true;

  @DiagnosticParameter(
    type = Integer.class,
    defaultValue = "" + COMPLEXITY_THRESHOLD
  )
  private int complexityThreshold = COMPLEXITY_THRESHOLD;

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + CHECK_MODULE_BODY
  )
  private boolean checkModuleBody = CHECK_MODULE_BODY;

  private boolean fileCodeBlockChecked;

  private List<DiagnosticRelatedInformation> makeRelations(MethodSymbol methodSymbol) {
    List<DiagnosticRelatedInformation> relatedInformation = new ArrayList<>();

    List<ComplexitySecondaryLocation> secondaryLocations =
      documentContext.getCyclomaticComplexityData().methodsComplexitySecondaryLocations().get(methodSymbol);

    secondaryLocations.stream()
      .map((ComplexitySecondaryLocation secondaryLocation) ->
        RelatedInformation.create(
          documentContext.getUri(),
          secondaryLocation.range(),
          secondaryLocation.message()
        )
      )
      .collect(Collectors.toCollection(() -> relatedInformation));

    return relatedInformation;
  }

  @Override
  public ParseTree visitSub(BSLParser.SubContext ctx) {
    Optional<MethodSymbol> optionalMethodSymbol = documentContext.getSymbolTree().getMethodSymbol(ctx);
    optionalMethodSymbol.ifPresent((MethodSymbol methodSymbol) -> {
      Integer methodComplexity = documentContext.getCyclomaticComplexityData().methodsComplexity().get(methodSymbol);

      if (methodComplexity > complexityThreshold) {
        diagnosticStorage.addDiagnostic(
          methodSymbol.getSubNameRange(),
          info.getMessage(methodSymbol.getName(), methodComplexity, complexityThreshold),
          makeRelations(methodSymbol)
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

  private void checkFileCodeBlock(ParserRuleContext ctx) {
    if (!checkModuleBody) {
      return;
    }

    Integer fileCodeBlockComplexity = documentContext.getCyclomaticComplexityData().fileCodeBlockComplexity();

    if (fileCodeBlockComplexity > complexityThreshold) {

      List<DiagnosticRelatedInformation> relatedInformation = new ArrayList<>();

      relatedInformation.add(RelatedInformation.create(
        documentContext.getUri(),
        Ranges.create(ctx.getStart()),
        info.getMessage("body", fileCodeBlockComplexity, complexityThreshold)
      ));

      List<ComplexitySecondaryLocation> secondaryLocations =
        documentContext.getCyclomaticComplexityData().fileBlockComplexitySecondaryLocations();

      secondaryLocations.stream()
        .map((ComplexitySecondaryLocation secondaryLocation) ->
          RelatedInformation.create(
            documentContext.getUri(),
            secondaryLocation.range(),
            secondaryLocation.message()
          )
        )
        .collect(Collectors.toCollection(() -> relatedInformation));

      diagnosticStorage.addDiagnostic(
        ctx.getStart(),
        info.getMessage("body", fileCodeBlockComplexity, complexityThreshold),
        relatedInformation
      );
    }
  }
}
