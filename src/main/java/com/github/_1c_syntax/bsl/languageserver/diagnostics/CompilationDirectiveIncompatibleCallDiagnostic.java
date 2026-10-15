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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.mdo.Form;
import com.github._1c_syntax.bsl.mdo.support.FormType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Вызов метода модуля формы или команды из метода, в контексте которого вызываемый метод не компилируется.
 * <br/>
 * Клиентский метод недоступен в серверном контексте и внеконтекстным методам, контекстный серверный -
 * внеконтекстным. Модуль с таким вызовом не компилируется. Контексты, в которых компилируется вызов, берутся из
 * директивы вызывающего метода и условий окружающих инструкций препроцессора {@code #Если}: сервер, тонкий,
 * веб- и мобильный клиент. Вызов под условием, которое нельзя вычислить (символ операционной системы или
 * неизвестный), не проверяется. Методы с директивой, недоступной виду модуля, пропускаются - их отмечает
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

  /**
   * Контексты компиляции модулей управляемого приложения.
   */
  private enum ExecutionContext {
    SERVER,
    THIN_CLIENT,
    WEB_CLIENT,
    MOBILE_CLIENT
  }

  private static final Set<ExecutionContext> ALL_CONTEXTS = EnumSet.allOf(ExecutionContext.class);
  private static final Set<ExecutionContext> SERVER_CONTEXTS = EnumSet.of(ExecutionContext.SERVER);
  private static final Set<ExecutionContext> CLIENT_CONTEXTS = EnumSet.complementOf(EnumSet.of(ExecutionContext.SERVER));
  private static final Set<ExecutionContext> NO_CONTEXTS = EnumSet.noneOf(ExecutionContext.class);

  /**
   * Контексты, в которых символ препроцессора истинен. Символа нет в таблице - условие не вычисляется.
   */
  private static final Map<Integer, Set<ExecutionContext>> SYMBOL_CONTEXTS = Map.ofEntries(
    Map.entry(BSLParser.PREPROC_CLIENT_SYMBOL, CLIENT_CONTEXTS),
    Map.entry(BSLParser.PREPROC_ATCLIENT_SYMBOL, CLIENT_CONTEXTS),
    Map.entry(BSLParser.PREPROC_SERVER_SYMBOL, SERVER_CONTEXTS),
    Map.entry(BSLParser.PREPROC_ATSERVER_SYMBOL, SERVER_CONTEXTS),
    Map.entry(BSLParser.PREPROC_THINCLIENT_SYMBOL, EnumSet.of(ExecutionContext.THIN_CLIENT)),
    Map.entry(BSLParser.PREPROC_WEBCLIENT_SYMBOL, EnumSet.of(ExecutionContext.WEB_CLIENT)),
    Map.entry(BSLParser.PREPROC_MOBILECLIENT_SYMBOL, EnumSet.of(ExecutionContext.MOBILE_CLIENT)),
    Map.entry(BSLParser.PREPROC_THICKCLIENTMANAGEDAPPLICATION_SYMBOL, NO_CONTEXTS),
    Map.entry(BSLParser.PREPROC_THICKCLIENTORDINARYAPPLICATION_SYMBOL, NO_CONTEXTS),
    Map.entry(BSLParser.PREPROC_EXTERNALCONNECTION_SYMBOL, NO_CONTEXTS),
    Map.entry(BSLParser.PREPROC_MOBILEAPPCLIENT_SYMBOL, NO_CONTEXTS),
    Map.entry(BSLParser.PREPROC_MOBILEAPPSERVER_SYMBOL, NO_CONTEXTS),
    Map.entry(BSLParser.PREPROC_MOBILE_STANDALONE_SERVER, NO_CONTEXTS)
  );

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

  /**
   * Открытые инструкции {@code #Если}, внутренняя - сверху.
   */
  private final Deque<PreprocessorBranch> preprocessorBranches = new ArrayDeque<>();

  @Override
  public void enterFile(BSLParser.FileContext ctx) {
    allowedDirectives = EnumSet.noneOf(CompilerDirectiveKind.class);
    callerDirective = null;
    preprocessorBranches.clear();

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
    var parent = preprocessorBranches.peek();
    var branch = parent == null ? new PreprocessorBranch(ALL_CONTEXTS) : new PreprocessorBranch(parent.active);
    branch.enter(evaluate(ctx.preproc_expression()));
    preprocessorBranches.push(branch);
  }

  @Override
  public void enterPreproc_elsif(BSLParser.Preproc_elsifContext ctx) {
    var branch = preprocessorBranches.peek();
    if (branch != null) {
      branch.enter(evaluate(ctx.preproc_expression()));
    }
  }

  @Override
  public void enterPreproc_else(BSLParser.Preproc_elseContext ctx) {
    var branch = preprocessorBranches.peek();
    if (branch != null) {
      branch.enterElse();
    }
  }

  @Override
  public void enterPreproc_endif(BSLParser.Preproc_endifContext ctx) {
    preprocessorBranches.poll();
  }

  @Override
  public void enterGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
    var caller = callerDirective;
    if (caller == null) {
      return;
    }
    var branch = preprocessorBranches.peek();
    var activeContexts = branch == null ? ALL_CONTEXTS : branch.active;
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
      case AT_CLIENT -> CLIENT_CONTEXTS;
      case AT_SERVER, AT_SERVER_NO_CONTEXT -> SERVER_CONTEXTS;
      case AT_CLIENT_AT_SERVER, AT_CLIENT_AT_SERVER_NO_CONTEXT -> ALL_CONTEXTS;
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

  /**
   * Контексты, в которых истинно условие препроцессора; null - условие не вычисляется.
   */
  private static @Nullable Set<ExecutionContext> evaluate(BSLParser.@Nullable Preproc_expressionContext expression) {
    if (expression == null) {
      return null;
    }
    var tokens = new ArrayList<Integer>();
    collectTokenTypes(expression, tokens);
    return new PreprocessorExpressionEvaluator(tokens).evaluate();
  }

  private static void collectTokenTypes(ParseTree tree, List<Integer> tokens) {
    if (tree instanceof TerminalNode terminal) {
      tokens.add(terminal.getSymbol().getType());
      return;
    }
    for (var i = 0; i < tree.getChildCount(); i++) {
      collectTokenTypes(tree.getChild(i), tokens);
    }
  }

  /**
   * Ветка открытой инструкции {@code #Если}: контексты, где активна текущая ветка, и контексты, где ни одна
   * из прошлых веток не сработала. null - условие не вычисляется, вызовы под ним не проверяются.
   */
  private static final class PreprocessorBranch {
    private @Nullable Set<ExecutionContext> active;
    private @Nullable Set<ExecutionContext> remaining;

    private PreprocessorBranch(@Nullable Set<ExecutionContext> parentActive) {
      remaining = parentActive == null ? null : EnumSet.copyOf(parentActive);
    }

    private void enter(@Nullable Set<ExecutionContext> condition) {
      if (remaining == null || condition == null) {
        active = null;
        remaining = null;
        return;
      }
      var newActive = EnumSet.copyOf(remaining);
      newActive.retainAll(condition);
      remaining.removeAll(newActive);
      active = newActive;
    }

    private void enterElse() {
      active = remaining;
      remaining = remaining == null ? null : EnumSet.noneOf(ExecutionContext.class);
    }
  }

  /**
   * Вычисление условия препроцессора по типам его лексем: {@code НЕ} сильнее {@code И}, {@code И} сильнее
   * {@code ИЛИ}.
   */
  private static final class PreprocessorExpressionEvaluator {
    private final List<Integer> tokens;
    private int position;

    private PreprocessorExpressionEvaluator(List<Integer> tokens) {
      this.tokens = tokens;
    }

    private @Nullable Set<ExecutionContext> evaluate() {
      var result = or();
      return position == tokens.size() ? result : null;
    }

    private @Nullable Set<ExecutionContext> or() {
      var result = and();
      while (result != null && accept(BSLParser.PREPROC_OR_KEYWORD)) {
        var right = and();
        if (right == null) {
          return null;
        }
        result.addAll(right);
      }
      return result;
    }

    private @Nullable Set<ExecutionContext> and() {
      var result = not();
      while (result != null && accept(BSLParser.PREPROC_AND_KEYWORD)) {
        var right = not();
        if (right == null) {
          return null;
        }
        result.retainAll(right);
      }
      return result;
    }

    private @Nullable Set<ExecutionContext> not() {
      if (accept(BSLParser.PREPROC_NOT_KEYWORD)) {
        var operand = not();
        return operand == null ? null : EnumSet.complementOf(EnumSet.copyOf(operand));
      }
      return operand();
    }

    private @Nullable Set<ExecutionContext> operand() {
      if (accept(BSLParser.PREPROC_LPAREN)) {
        var result = or();
        return accept(BSLParser.PREPROC_RPAREN) ? result : null;
      }
      if (position >= tokens.size()) {
        return null;
      }
      var contexts = SYMBOL_CONTEXTS.get(tokens.get(position++));
      return contexts == null ? null : EnumSet.copyOf(contexts);
    }

    private boolean accept(int tokenType) {
      if (position < tokens.size() && tokens.get(position) == tokenType) {
        position++;
        return true;
      }
      return false;
    }
  }
}
