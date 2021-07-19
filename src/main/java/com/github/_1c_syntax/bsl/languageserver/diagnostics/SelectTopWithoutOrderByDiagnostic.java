/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.SDBLParser;
import org.antlr.v4.runtime.tree.ParseTree;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.SQL,
    DiagnosticTag.SUSPICIOUS
  },
  scope = DiagnosticScope.BSL
)
public class SelectTopWithoutOrderByDiagnostic extends AbstractSDBLVisitorDiagnostic {

  private static final String TOP_ONE_STRING = "1";

  // for skip queries with 'TOP 1' limitation without 'ORDER BY'
  private static final boolean SKIP_SELECT_TOP_ONE = true;
  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + SKIP_SELECT_TOP_ONE
  )
  private boolean skipSelectTopOne = SKIP_SELECT_TOP_ONE;

  @Override
  public ParseTree visitSubquery(SDBLParser.SubqueryContext ctx) {
    if (!ctx.union().isEmpty()) {
      // always presence of 'top' is a mistake
      checkQuery(ctx.query(), false);
      ctx.union().forEach(unionCtx -> checkQuery(unionCtx.query(), false));
    } else {
      // missing order by
      if (!Trees.nodeContains(ctx.getParent(), SDBLParser.RULE_orders)) {
        checkQuery(ctx.query(), skipSelectTopOne);
      }
    }
    return super.visitSubquery(ctx);
  }

  private void checkQuery(SDBLParser.QueryContext ctx, boolean canTopOne) {

    SDBLParser.LimitationsContext limitations = ctx.limitations();

    //limitations or top is missing
    if (limitations == null || limitations.top() == null) {
      return;
    }

    var topCtx = ctx.limitations().top();
    if (topCtx.DECIMAL().isEmpty()) {
      // why? it's error!
      return;
    }

    var topLimit = topCtx.DECIMAL().get(0).getText();
    if (!(TOP_ONE_STRING.equals(topLimit) && (canTopOne || ctx.where().WHERE() != null))) {
      diagnosticStorage.addDiagnostic(topCtx);
    }
  }
}
