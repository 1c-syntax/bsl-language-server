/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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
package com.github._1c_syntax.bsl.languageserver.utils;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic;
import com.github._1c_syntax.bsl.languageserver.providers.CodeActionProvider;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.TextEdit;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@UtilityClass
public final class QuickFixHelper {

  public List<CodeAction> getQuickFixes(
    BSLDiagnostic diagnosticInstance,
    List<Diagnostic> diagnostics,
    DocumentContext documentContext,
    Function<Diagnostic, TextEdit> mapFunction
  ) {

    var textEdits = diagnostics.stream()
      .map(mapFunction)
      .filter(Objects::nonNull)
      .collect(Collectors.toList());

    return createCodeActions(diagnosticInstance, textEdits, documentContext, diagnostics);
  }

  public static List<CodeAction> createCodeActions(
    BSLDiagnostic diagnosticInstance, List<TextEdit> textEdits,
    DocumentContext documentContext, List<Diagnostic> diagnostics) {

    return CodeActionProvider.createCodeActions(
      textEdits,
      diagnosticInstance.getInfo().getResourceString("quickFixMessage"),
      documentContext.getUri(),
      diagnostics
    );
  }

}
