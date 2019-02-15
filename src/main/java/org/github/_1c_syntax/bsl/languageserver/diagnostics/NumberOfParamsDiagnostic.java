/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com>
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
import org.antlr.v4.runtime.tree.Trees;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.github._1c_syntax.bsl.parser.BSLParser;

import java.util.List;


public class NumberOfParamsDiagnostic extends AbstractVisitorDiagnostic {

  private static final int MAX_PARAMS_COUNT = 7;
  private static final int MAX_OPTIONAL_PARAMS_COUNT = 3;
  private int optionalCount;
  private int paramsCount;
  private boolean onePerline;

  @Override
  public DiagnosticSeverity getSeverity() {
    return DiagnosticSeverity.Information;
  }

  @Override
  public ParseTree visitParamList(BSLParser.ParamListContext ctx) {

    optionalCount = 0;
    paramsCount = 0;

    List<ParseTree> params = Trees.findAllNodes(ctx, BSLParser.RULE_param, false);

    for (ParseTree param : params) {
      boolean itsOptional = ((BSLParser.ParamContext) param).defaultValue() != null;
      if (itsOptional) {
        optionalCount++;
      } else {
        paramsCount++;
      }

      if (!itsOptional && optionalCount > 0 && !onePerline) {
        onePerline = true;
        addDiagnostic(ctx, getDiagnosticMessage("MoveOptionalParams"));
      }

    }

    if (paramsCount > MAX_PARAMS_COUNT) {
      addDiagnostic(ctx, getDiagnosticMessage("MaxParamsMessage"));
    }

    if (optionalCount > MAX_OPTIONAL_PARAMS_COUNT) {
      addDiagnostic(ctx, getDiagnosticMessage("MaxOptionalParamsMessage"));
    }

    return super.visitParamList(ctx);
  }

}
