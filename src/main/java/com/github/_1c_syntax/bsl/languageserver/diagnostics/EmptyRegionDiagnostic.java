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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.providers.CodeActionProvider;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.STANDARD
  }
)
public class EmptyRegionDiagnostic extends AbstractDiagnostic implements QuickFixProvider {

  public EmptyRegionDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  protected void check(DocumentContext documentContext) {

    documentContext.getMethods();

    documentContext.getRegionsFlat()
      .stream()
      .filter(regionSymbol -> regionSymbol.getMethods().isEmpty())
      .forEach(regionSymbol -> diagnosticStorage.addDiagnostic(regionSymbol.getNode(), info.getMessage(regionSymbol.getName())));

  }

  @Override
  public List<CodeAction> getQuickFixes(List<Diagnostic> diagnostics, CodeActionParams params, DocumentContext documentContext) {

    List<TextEdit> textEdits = new ArrayList<>();
    AtomicInteger maxDiagnosticEndLine = new AtomicInteger();

    for (Diagnostic diagnostic : diagnostics) {

      int diagnosticStartLine = diagnostic.getRange().getStart().getLine();
      Position diagnosticRangeStart = diagnostic.getRange().getStart();
      diagnosticRangeStart = new Position(
        diagnosticRangeStart.getLine(),
        diagnosticRangeStart.getCharacter() - 1
      );

      Optional<RegionSymbol> optionalRegionSymbol = documentContext.getRegionsFlat()
        .stream()
        .filter(regionSymbol -> regionSymbol.getStartLine()-1 == diagnosticStartLine)
        .findFirst();
      if (optionalRegionSymbol.isEmpty()) {
        continue;
      }
      RegionSymbol region = optionalRegionSymbol.get();

      int diagnosticEndLine = region.getEndLine() - 1;
      if (diagnosticEndLine < maxDiagnosticEndLine.get()) {
        continue;
      }

      if (maxDiagnosticEndLine.get() == 0 || diagnosticEndLine > maxDiagnosticEndLine.get()) {
        maxDiagnosticEndLine.set(diagnosticEndLine);
      }

      Position diagnosticRangeEnd = new Position(
        diagnosticEndLine + 1,
        0
      );

      Range range = new Range(diagnosticRangeStart, diagnosticRangeEnd);

      TextEdit textEdit = new TextEdit(range, "");
      textEdits.add(textEdit);

    }

    return CodeActionProvider.createCodeActions(
      textEdits,
      info.getResourceString("quickFixMessage"),
      documentContext.getUri(),
      diagnostics
    );
  }
}

