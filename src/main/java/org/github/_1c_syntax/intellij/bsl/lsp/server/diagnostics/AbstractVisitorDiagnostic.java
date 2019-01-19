/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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

import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.Diagnostic;
import org.github._1c_syntax.parser.BSLParser;
import org.github._1c_syntax.parser.BSLParserBaseVisitor;
import org.github._1c_syntax.parser.BSLParserRuleContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AbstractVisitorDiagnostic extends BSLParserBaseVisitor<ParseTree> implements BSLDiagnostic {
  protected List<Diagnostic> diagnostics = Collections.synchronizedList(new ArrayList<>());

  @Override
  public List<Diagnostic> getDiagnostics(BSLParser.FileContext fileTree) {
    diagnostics.clear();
    this.visitFile(fileTree);
    return new ArrayList<>(diagnostics);
  }

  protected synchronized void addDiagnostic(BSLParserRuleContext node) {
    diagnostics.add(BSLDiagnostic.createDiagnostic(this, node));
  }

  protected synchronized void addDiagnostic(BSLParserRuleContext node, String diagnosticMessage) {
    diagnostics.add(BSLDiagnostic.createDiagnostic(this, diagnosticMessage, node));
  }

}
