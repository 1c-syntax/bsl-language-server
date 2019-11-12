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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.computer.DiagnosticIgnoranceComputer;
import com.github._1c_syntax.bsl.languageserver.diagnostics.DiagnosticSupplier;
import com.github._1c_syntax.mdclasses.metadata.additional.CompatibilityMode;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.services.LanguageClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class DiagnosticProvider {

  public static final String SOURCE = "bsl-language-server";

  private final Map<String, Set<Diagnostic>> computedDiagnostics;
  private final DiagnosticSupplier diagnosticSupplier;

  public DiagnosticProvider() {
    this(new DiagnosticSupplier(LanguageServerConfiguration.create()));
  }

  public DiagnosticProvider(DiagnosticSupplier diagnosticSupplier) {
    this.diagnosticSupplier = diagnosticSupplier;
    computedDiagnostics = new HashMap<>();
  }

  public void computeAndPublishDiagnostics(LanguageClient client, DocumentContext documentContext) {
    List<Diagnostic> diagnostics = computeDiagnostics(documentContext);

    client.publishDiagnostics(new PublishDiagnosticsParams(documentContext.getUri(), diagnostics));
  }

  public void publishEmptyDiagnosticList(LanguageClient client, DocumentContext documentContext) {
    List<Diagnostic> diagnostics = new ArrayList<>();
    computedDiagnostics.put(documentContext.getUri(), new LinkedHashSet<>());
    client.publishDiagnostics(
      new PublishDiagnosticsParams(documentContext.getUri(), diagnostics)
    );
  }

  public List<Diagnostic> computeDiagnostics(DocumentContext documentContext) {

    FileType fileType = documentContext.getFileType();
    CompatibilityMode contextCompatibilityMode = documentContext
      .getServerContext()
      .getConfiguration()
      .getCompatibilityMode();
    DiagnosticIgnoranceComputer.Data diagnosticIgnorance = documentContext.getDiagnosticIgnorance();

    List<Diagnostic> diagnostics =
      diagnosticSupplier.getDiagnosticInstances(fileType, contextCompatibilityMode).parallelStream()
      .flatMap(diagnostic -> diagnostic.getDiagnostics(documentContext).stream())
      .filter((Diagnostic diagnostic) ->
        !diagnosticIgnorance.diagnosticShouldBeIgnored(diagnostic))
      .collect(Collectors.toList());

    computedDiagnostics.put(documentContext.getUri(), new LinkedHashSet<>(diagnostics));

    return diagnostics;
  }

  public Set<Diagnostic> getComputedDiagnostics(DocumentContext documentContext) {
    return computedDiagnostics.getOrDefault(documentContext.getUri(), new LinkedHashSet<>());
  }

  public void clearComputedDiagnostics(DocumentContext documentContext) {
    computedDiagnostics.put(documentContext.getUri(), new LinkedHashSet<>());
  }

}
