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

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.AnnotationKind;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.BLOCKER,
  minutesToFix = 1,
  scope = DiagnosticScope.BSL,
  tags = {
    DiagnosticTag.ERROR,
    DiagnosticTag.SUSPICIOUS
  }

)

public class WrongUseFunctionProceedWithCallDiagnostic extends AbstractFindMethodDiagnostic {
  private static final Pattern MESSAGE_PATTERN = CaseInsensitivePattern.compile(
    "(ПродолжитьВызов|ProceedWithCall)"
  );

  private static final Set<AnnotationKind> EXTENSION_ANNOTATIONS = EnumSet.of(
    AnnotationKind.AROUND
  );

  public WrongUseFunctionProceedWithCallDiagnostic() {
    super(MESSAGE_PATTERN);
  }

  @Override
  protected boolean checkGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {

    var isFindMethod = getMethodPattern().matcher(ctx.methodName().getText()).matches();
    if (!isFindMethod) {
      return false;
    }

    var parentNode = (BSLParser.SubContext) Trees.getAncestorByRuleIndex(ctx, BSLParser.RULE_sub);
    if (parentNode == null) {
      return false;
    }

    List<Annotation> methodAnnotations = documentContext.getSymbolTree().getMethodSymbol(parentNode).map(MethodSymbol::getAnnotations).get();
    for (Annotation methodAnnotation : methodAnnotations) {
      if (EXTENSION_ANNOTATIONS.contains(methodAnnotation.getKind())) {
        return false;
      }
    }

    return true;
  }

  @Override
  protected boolean checkMethodCall(BSLParser.MethodCallContext ctx) {
    return false;
  }

}
