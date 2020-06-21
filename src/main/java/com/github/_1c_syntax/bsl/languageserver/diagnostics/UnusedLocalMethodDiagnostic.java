/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.Trees;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  scope = DiagnosticScope.ALL,
  modules = {
    ModuleType.CommonModule
  },
  minutesToFix = 1,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.SUSPICIOUS
  }
)
public class UnusedLocalMethodDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern ATTACHABLE_PATTERN = CaseInsensitivePattern.compile(
    "(подключаемый_.*|attachable_.*)"
  );

  private static final Pattern HANDLER_PATTERN = CaseInsensitivePattern.compile(
    "(ПриСозданииОбъекта|OnObjectCreate)"
  );

  private static final Set<AnnotationKind> EXTENSION_ANNOTATIONS = EnumSet.of(
    AnnotationKind.AFTER,
    AnnotationKind.AROUND,
    AnnotationKind.BEFORE,
    AnnotationKind.CHANGEANDVALIDATE
  );

  public UnusedLocalMethodDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  private static boolean isAttachable(MethodSymbol methodSymbol) {
    return ATTACHABLE_PATTERN.matcher(methodSymbol.getName()).matches();
  }

  private static boolean isHandler(MethodSymbol methodSymbol) {
    return HANDLER_PATTERN.matcher(methodSymbol.getName()).matches();
  }

  private static boolean isOverride(MethodSymbol method) {
    return method.getAnnotations()
      .stream()
      .map(Annotation::getKind)
      .anyMatch(EXTENSION_ANNOTATIONS::contains);
  }

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {

    List<String> collect = Trees.findAllRuleNodes(ctx, BSLParser.RULE_globalMethodCall)
      .stream()
      .map(parseTree ->
        ((BSLParser.GlobalMethodCallContext) parseTree).methodName().getText().toLowerCase(Locale.ENGLISH))
      .collect(Collectors.toList());

    documentContext.getSymbolTree().getMethods()
      .stream()
      .filter(method -> !method.isExport())
      .filter(method -> !isOverride(method))
      .filter(method -> !isAttachable(method))
      .filter(method -> !isHandler(method))
      .filter(method -> !collect.contains(method.getName().toLowerCase(Locale.ENGLISH)))
      .forEach(method -> diagnosticStorage.addDiagnostic(method.getSubNameRange(), info.getMessage(method.getName())));

    return ctx;
  }
}
