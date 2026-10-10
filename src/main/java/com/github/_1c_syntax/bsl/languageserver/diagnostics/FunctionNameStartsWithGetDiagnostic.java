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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 3,
  activatedByDefault = false,
  tags = {
    DiagnosticTag.STANDARD
  }

)
public class FunctionNameStartsWithGetDiagnostic extends AbstractVisitorDiagnostic {
  private static final Pattern get = CaseInsensitivePattern.compile(
    "^Получить.*$"
  );

  @DiagnosticParameter(type = String.class)
  private List<String> prefixes = List.of();

  @Override
  public void configure(Map<String, Object> configuration) {
    prefixes = Arrays.stream(((String) configuration.getOrDefault("prefixes", "")).split(","))
      .map(String::trim)
      .filter(prefix -> !prefix.isEmpty())
      .toList();
  }

  @Override
  public ParseTree visitFuncDeclaration(BSLParser.FuncDeclarationContext ctx) {

    BSLParser.SubNameContext subName = ctx.subName();

    if (subName == null) {
      return ctx;
    }

    var name = subName.getText();
    if (get.matcher(name).matches() || prefixes.stream().anyMatch(prefix ->
      name.regionMatches(true, 0, prefix, 0, prefix.length())
        && get.matcher(name.substring(prefix.length())).matches())) {
      diagnosticStorage.addDiagnostic(subName);
    }

    return ctx;

  }
}
