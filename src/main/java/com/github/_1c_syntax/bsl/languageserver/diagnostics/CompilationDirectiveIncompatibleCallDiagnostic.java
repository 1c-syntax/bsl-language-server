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

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.CompilerDirectiveKind;
import com.github._1c_syntax.bsl.languageserver.diagnostics.PreprocessorContexts.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.mdo.Form;
import com.github._1c_syntax.bsl.mdo.support.FormType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * Вызов метода модуля формы или команды из метода, в контексте которого вызываемый метод не компилируется.
 * <br/>
 * Клиентский метод недоступен в серверном контексте и внеконтекстным методам, контекстный серверный -
 * внеконтекстным. Модуль с таким вызовом не компилируется. Контексты, в которых компилируется вызов, берутся из
 * директивы вызывающего метода и условий окружающих инструкций препроцессора {@code #Если}
 * (см. {@link PreprocessorContexts}). Методы с директивой, недоступной виду модуля, пропускаются - их отмечает
 * {@link CompilationDirectiveNotAllowedDiagnostic}.
 */
@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.CRITICAL,
  scope = DiagnosticScope.BSL,
  modules = {
    ModuleType.FormModule,
    ModuleType.CommandModule
  },
  minutesToFix = 5,
  tags = {
    DiagnosticTag.ERROR
  }
)
public class CompilationDirectiveIncompatibleCallDiagnostic extends AbstractListenerDiagnostic {

  private static final Set<CompilerDirectiveKind> FORM_MODULE_DIRECTIVES = EnumSet.of(
    CompilerDirectiveKind.AT_CLIENT,
    CompilerDirectiveKind.AT_SERVER,
    CompilerDirectiveKind.AT_SERVER_NO_CONTEXT,
    CompilerDirectiveKind.AT_CLIENT_AT_SERVER_NO_CONTEXT
  );

  private static final Set<CompilerDirectiveKind> COMMAND_MODULE_DIRECTIVES = EnumSet.of(
    CompilerDirectiveKind.AT_CLIENT,
    CompilerDirectiveKind.AT_SERVER,
    CompilerDirectiveKind.AT_CLIENT_AT_SERVER
  );

  private static final Set<CompilerDirectiveKind> NO_CONTEXT_DIRECTIVES = EnumSet.of(
    CompilerDirectiveKind.AT_SERVER_NO_CONTEXT,
    CompilerDirectiveKind.AT_CLIENT_AT_SERVER_NO_CONTEXT
  );

  private Set<CompilerDirectiveKind> allowedDirectives = EnumSet.noneOf(CompilerDirectiveKind.class);

  /**
   * Директива метода, вызовы из которого проверяются; null - вызовы текущего метода не проверяются.
   */
  private @Nullable CompilerDirectiveKind callerDirective;

  private final PreprocessorContexts preprocessor = new PreprocessorContexts();

  @Override
  public void enterFile(BSLParser.FileContext ctx) {
    allowedDirectives = EnumSet.noneOf(CompilerDirectiveKind.class);
    callerDirective = null;
    preprocessor.clear();

    var moduleType = documentContext.getModuleType();
    if (moduleType == ModuleType.FormModule && !isOrdinaryForm()) {
      allowedDirectives = FORM_MODULE_DIRECTIVES;
    } else if (moduleType == ModuleType.CommandModule) {
      allowedDirectives = COMMAND_MODULE_DIRECTIVES;
    }
  }

  @Override
  public void enterSub(BSLParser.SubContext ctx) {
    callerDirective = documentContext.getSymbolTree().getMethodSymbol(ctx)
      .map(this::compilerDirective)
      .orElse(null);
  }

  @Override
  public void exitSub(BSLParser.SubContext ctx) {
    callerDirective = null;
  }

  @Override
  public void enterPreproc_if(BSLParser.Preproc_ifContext ctx) {
    preprocessor.enterIf(ctx);
  }

  @Override
  public void enterPreproc_elsif(BSLParser.Preproc_elsifContext ctx) {
    preprocessor.enterElsif(ctx);
  }

  @Override
  public void enterPreproc_else(BSLParser.Preproc_elseContext ctx) {
    preprocessor.enterElse();
  }

  @Override
  public void enterPreproc_endif(BSLParser.Preproc_endifContext ctx) {
    preprocessor.enterEndif();
  }

  @Override
  public void enterGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
    var caller = callerDirective;
    if (caller == null) {
      return;
    }
    var activeContexts = preprocessor.active();
    if (activeContexts == null) {
      return;
    }
    var callContexts = EnumSet.copyOf(callerContexts(caller));
    callContexts.retainAll(activeContexts);
    if (callContexts.isEmpty()) {
      return;
    }

    var methodName = ctx.methodName().getText();
    documentContext.getSymbolTree().getMethodSymbol(methodName)
      .map(this::compilerDirective)
      .filter(callee -> callContexts.stream().anyMatch(context -> !isAvailable(caller, callee, context)))
      .ifPresent(callee -> diagnosticStorage.addDiagnostic(ctx.methodName(), info.getMessage(methodName)));
  }

  private boolean isOrdinaryForm() {
    return documentContext.getMdObject()
      .filter(Form.class::isInstance)
      .map(Form.class::cast)
      .map(form -> form.getFormType() != FormType.MANAGED)
      .orElse(false);
  }

  /**
   * Директива метода с учетом директивы по умолчанию; null - директива недоступна виду модуля.
   */
  private @Nullable CompilerDirectiveKind compilerDirective(MethodSymbol method) {
    var directive = method.getCompilerDirectiveKind().orElse(CompilerDirectiveKind.AT_SERVER);
    return allowedDirectives.contains(directive) ? directive : null;
  }

  private static Set<ExecutionContext> callerContexts(CompilerDirectiveKind caller) {
    return switch (caller) {
      case AT_CLIENT -> PreprocessorContexts.CLIENT_CONTEXTS;
      case AT_SERVER, AT_SERVER_NO_CONTEXT -> PreprocessorContexts.SERVER_CONTEXTS;
      case AT_CLIENT_AT_SERVER, AT_CLIENT_AT_SERVER_NO_CONTEXT -> PreprocessorContexts.ALL_CONTEXTS;
    };
  }

  /**
   * Доступен ли вызываемый метод вызывающему в контексте компиляции. Внеконтекстный метод не видит ни
   * клиентских, ни контекстных серверных методов - и на клиенте, и на сервере.
   */
  private static boolean isAvailable(
    CompilerDirectiveKind caller,
    CompilerDirectiveKind callee,
    ExecutionContext context
  ) {
    return switch (callee) {
      case AT_CLIENT -> context != ExecutionContext.SERVER && !NO_CONTEXT_DIRECTIVES.contains(caller);
      case AT_SERVER -> !NO_CONTEXT_DIRECTIVES.contains(caller);
      default -> true;
    };
  }
}
