/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Fedkin <nixel2007@gmail.com> and contributors
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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.SDBLParser;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.HashSet;
import java.util.Set;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 15,
  tags = {
    DiagnosticTag.SQL,
    DiagnosticTag.PERFORMANCE,
    DiagnosticTag.STANDARD
  },
  scope = DiagnosticScope.BSL
)
public class LogicalOrInTheWhereSectionOfQueryDiagnostic extends AbstractSDBLListenerDiagnostic {

  private final Set<ParseTree> ors = new HashSet<>();

  @Override
  public void enterQueryPackage(SDBLParser.QueryPackageContext ctx) {
    ors.clear();
    super.enterQueryPackage(ctx);
  }

  @Override
  public void exitQueryPackage(SDBLParser.QueryPackageContext ctx) {
    ors.forEach(diagnosticStorage::addDiagnostic);
    super.exitQueryPackage(ctx);
  }

  @Override
  public void exitQuery(SDBLParser.QueryContext ctx) {
    if (ctx.where != null) {
      ors.addAll(new HashSet<>(Trees.findAllTokenNodes(ctx.where, SDBLParser.OR)));
    }
    super.exitQuery(ctx);
  }
}
