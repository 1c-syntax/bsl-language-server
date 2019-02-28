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
package org.github._1c_syntax.bsl.languageserver.providers;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.github._1c_syntax.bsl.languageserver.configuration.diagnostics.DiagnosticConfiguration;
import org.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.languageserver.diagnostics.*;
import org.github._1c_syntax.bsl.parser.BSLParser;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class DiagnosticProvider {

  public static final String SOURCE = "bsl-language-server";
  private final LanguageServerConfiguration configuration;

  public DiagnosticProvider(LanguageServerConfiguration configuration) {
    this.configuration = configuration;
  }

  public void computeAndPublishDiagnostics(LanguageClient client, DocumentContext documentContext) {
    List<Diagnostic> diagnostics = computeDiagnostics(documentContext.getAst());

    client.publishDiagnostics(new PublishDiagnosticsParams(documentContext.getUri(), diagnostics));
  }

  public List<Diagnostic> computeDiagnostics(BSLParser.FileContext fileTree) {
    return getDiagnosticClasses().parallelStream()
        .filter(this::isEnabled)
        .flatMap(diagnostic -> diagnostic.getDiagnostics(fileTree).stream())
        .collect(Collectors.toList());
  }

  private List<BSLDiagnostic> getDiagnosticClasses() {
    List<BSLDiagnostic> diagnostics = Arrays.asList(
      new CanonicalSpellingKeywordsDiagnostic(),
      new EmptyCodeBlockDiagnostic(),
      new EmptyStatementDiagnostic(),
      new FunctionShouldHaveReturnDiagnostic(),
      new IfElseDuplicatedConditionDiagnostic(),
      new IfElseIfEndsWithElseDiagnostic(),
      new LineLengthDiagnostic(),
      new MethodSizeDiagnostic(),
      new NestedTernaryOperatorDiagnostic(),
      new NumberOfOptionalParamsDiagnostic(),
      new NumberOfParamsDiagnostic(),
      new OneStatementPerLineDiagnostic(),
      new OrderOfParamsDiagnostic(),
      new SelfAssignDiagnostic(),
      new SemicolonPresenceDiagnostic(),
      new UnknownPreprocessorSymbolDiagnostic(),
      new UsingCancelParameterDiagnostic(),
      new YoLetterUsageDiagnostic()
    );

    diagnostics.forEach(diagnostic -> {
        Either<Boolean, DiagnosticConfiguration> diagnosticConfiguration = configuration.getDiagnostics().get(diagnostic.getCode());
        if (diagnosticConfiguration != null && diagnosticConfiguration.isRight()) {
          diagnostic.configure(diagnosticConfiguration.getRight());
        }
      }
    );

    return diagnostics;
  }

  private boolean isEnabled(BSLDiagnostic bslDiagnostic) {
    Either<Boolean, DiagnosticConfiguration> diagnosticConfiguration = configuration.getDiagnostics().get(bslDiagnostic.getCode());
    return diagnosticConfiguration == null ||
      (diagnosticConfiguration.isLeft() && diagnosticConfiguration.getLeft());
  }
}
