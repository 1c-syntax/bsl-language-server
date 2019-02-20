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

import org.antlr.v4.runtime.*;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.github._1c_syntax.bsl.parser.BSLParser;
import org.github._1c_syntax.bsl.languageserver.utils.RangeHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class YoLetterUsageDiagnostic implements BSLDiagnostic {

  @Override
  public DiagnosticSeverity getSeverity() {
    return DiagnosticSeverity.Hint;
  }

  @Override
  public List<Diagnostic> getDiagnostics(BSLParser.FileContext fileTree) {

    List<Token> wrongIdentifiers = fileTree.getTokens()
                                  .parallelStream()
                                  .filter((Token t) ->
                                    t.getType() == BSLParser.IDENTIFIER &&
                                    t.getText().toUpperCase().contains("Ё"))
                                  .collect((Collectors.toList()));

    List<Diagnostic> diagnostics = new ArrayList<>();

    for(Token token : wrongIdentifiers) {
         diagnostics.add(BSLDiagnostic.createDiagnostic(
           this,
           RangeHelper.newRange(token),
           getDiagnosticMessage()));
    }

    return diagnostics;

  }
}
