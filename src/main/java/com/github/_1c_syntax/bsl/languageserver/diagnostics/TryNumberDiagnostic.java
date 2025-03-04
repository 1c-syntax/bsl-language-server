/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParser.GlobalMethodCallContext;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.function.Predicate;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 2,
  tags = {
    DiagnosticTag.STANDARD
  }
)
public class TryNumberDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern NUMBER_PATTERN = CaseInsensitivePattern.compile(
    "число|number"
  );
  private static final Predicate<GlobalMethodCallContext> MATCH_METHOD_CALL_CAST_TO_NUMBER
    = e -> NUMBER_PATTERN.matcher(e.methodName().getText()).matches();

  @Override
  public ParseTree visitTryCodeBlock(BSLParser.TryCodeBlockContext ctx) {

    Trees.findAllRuleNodes(ctx, BSLParser.RULE_globalMethodCall)
      .stream()
      .filter(GlobalMethodCallContext.class::isInstance)
      .map(GlobalMethodCallContext.class::cast)
      .filter(MATCH_METHOD_CALL_CAST_TO_NUMBER)
      .forEach(diagnosticStorage::addDiagnostic);

    return ctx;
  }

}
