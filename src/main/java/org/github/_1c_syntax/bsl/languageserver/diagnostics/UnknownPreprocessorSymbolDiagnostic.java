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
import org.github._1c_syntax.bsl.parser.BSLParser;

public class UnknownPreprocessorSymbolDiagnostic extends AbstractVisitorDiagnostic {

  @Override
  public ParseTree visitPreproc_unknownSymbol(BSLParser.Preproc_unknownSymbolContext ctx) {
    addDiagnostic(ctx, getDiagnosticMessage(ctx));
    return super.visitPreproc_unknownSymbol(ctx);
  }

  private String getDiagnosticMessage(BSLParser.Preproc_unknownSymbolContext unknownSymbol) {
    String diagnosticMessage = super.getDiagnosticMessage();
    return String.format(diagnosticMessage, unknownSymbol.getText());
  }
}
