/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018
 * Alexey Sosnoviy <labotamy@yandex.ru>, Nikita Gryzlov <nixel2007@gmail.com>
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
package org.github._1c_syntax.intellij.bsl.lsp.server.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.github._1c_syntax.parser.BSLParser;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DiagnosticProvider {

  public static final String SOURCE = "bsl-language-server";

  private static List<BSLDiagnostic> diagnosticClasses = Arrays.asList(
    new FunctionShouldHaveReturnDiagnostic(),
    new LineLengthDiagnostic()
  );

  public static void computeAndPublishDiagnostics(LanguageClient client, String uri, BSLParser.FileContext fileTree) {

    List<Diagnostic> diagnostics = diagnosticClasses.parallelStream()
      .flatMap(diagnostic -> diagnostic.getDiagnostics(fileTree).stream())
      .collect(Collectors.toList());

    client.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics));
  }
}
