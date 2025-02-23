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

import com.github._1c_syntax.bsl.languageserver.utils.Keywords;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Optional;
import java.util.regex.Pattern;

abstract class AbstractExecuteExternalCodeDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern EVAL_METHOD_NAME = CaseInsensitivePattern.compile(
    String.format("^(%s|%s)$", Keywords.EVAL_EN, Keywords.EVAL_RU));

  @Override
  public ParseTree visitExecuteStatement(BSLParser.ExecuteStatementContext ctx) {
    diagnosticStorage.addDiagnostic(ctx);
    return super.visitExecuteStatement(ctx);
  }

  @Override
  public ParseTree visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
    Optional.of(ctx)
      .filter(it -> EVAL_METHOD_NAME.matcher(it.methodName().getText()).matches())
      .ifPresent(diagnosticStorage::addDiagnostic);

    return super.visitGlobalMethodCall(ctx);
  }

}
