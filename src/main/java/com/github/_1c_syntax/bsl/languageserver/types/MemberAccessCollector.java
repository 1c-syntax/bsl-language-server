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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.compilation.PreprocessorReachability;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver.MethodContext;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseListener;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Collections;

/** Снимки обращений и их веток препроцессора в порядке исходного текста. */
final class MemberAccessCollector extends BSLParserBaseListener {
  private final DocumentContext documentContext;
  private final Set<ExecutionContext> enabledContexts;
  private final Set<MemberAccessCheckKind> checks;
  private final MemberAccessContexts contextData;
  private final CompilationContextResolver contextResolver;
  private final GlobalScopeProvider globalScopeProvider;
  private final PreprocessorReachability preprocessor;
  private final List<MemberAccessSite> accesses = new ArrayList<>();
  private Optional<MethodContext> caller;
  private boolean unreachable;

  MemberAccessCollector(DocumentContext document, Set<ExecutionContext> enabled, Set<MemberAccessCheckKind> checks,
                        MemberAccessContexts contextData, CompilationContextResolver contextResolver,
                        GlobalScopeProvider globalScopeProvider) {
    documentContext = document;
    enabledContexts = Set.copyOf(enabled);
    this.checks = Set.copyOf(checks);
    this.contextData = contextData;
    this.contextResolver = contextResolver;
    this.globalScopeProvider = globalScopeProvider;
    preprocessor = new PreprocessorReachability(document.getFileType());
    caller = contextData.methodContext(document, Optional.empty());
  }

  /** Обход уже завершён; возвращаемый список изменять нельзя. */
  List<MemberAccessSite> accesses() {
    return Collections.unmodifiableList(accesses);
  }

  @Override
  public void enterSub(BSLParser.SubContext ctx) {
    caller = documentContext.getSymbolTree().getMethodSymbol(ctx)
      .flatMap(method -> contextData.methodContext(documentContext, method.getCompilerDirectiveKind()));
  }

  @Override
  public void exitSub(BSLParser.SubContext ctx) {
    caller = contextData.methodContext(documentContext, Optional.empty());
  }

  @Override
  public void enterPreprocessor(BSLParser.PreprocessorContext ctx) {
    if (preprocessor.enter(ctx)) {
      unreachable = preprocessor.isUnreachable(contextResolver.supportedContexts(documentContext));
    }
  }

  @Override
  public void enterGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
    if (ctx.methodName() != null) {
      rememberAccess(ctx.methodName().IDENTIFIER(), true, true);
    }
  }

  @Override
  public void enterMethodCall(BSLParser.MethodCallContext ctx) {
    if (ctx.methodName() != null) {
      rememberAccess(ctx.methodName().IDENTIFIER(), false, true);
    }
  }

  @Override
  public void enterAccessProperty(BSLParser.AccessPropertyContext ctx) {
    rememberAccess(ctx.IDENTIFIER(), false, false);
  }

  @Override
  public void enterComplexIdentifier(BSLParser.ComplexIdentifierContext ctx) {
    rememberGlobalProperty(ctx.IDENTIFIER());
  }

  @Override
  public void enterCallStatement(BSLParser.CallStatementContext ctx) {
    rememberGlobalProperty(ctx.IDENTIFIER());
  }

  /**
   * Голова цепочки имеет разные правила грамматики в выражении и в отдельном вызове.
   *
   * @param name первый идентификатор; {@code null}, если цепочка начинается вызовом функции.
   */
  private void rememberGlobalProperty(@Nullable TerminalNode name) {
    if (checks.contains(MemberAccessCheckKind.AVAILABILITY) && name != null
        && globalScopeProvider.globalProperty(name.getText(), documentContext.getFileType()).isPresent()) {
      rememberAccess(name, true, false);
    }
  }

  /**
   * Зафиксировать контексты в точке обращения, пока обход находится в нужной ветке.
   */
  private void rememberAccess(@Nullable TerminalNode name, boolean unqualified, boolean method) {
    if (name == null || unreachable) {
      return;
    }
    var branch = preprocessor.activeContexts();
    var contexts = caller.flatMap((MethodContext profile) -> {
      var active = ExecutionContext.copyOf(profile.contexts());
      active.retainAll(enabledContexts);
      if (branch != null) {
        active.retainAll(branch);
      }
      return branch != null || active.isEmpty() ? Optional.of(Set.copyOf(active)) : Optional.empty();
    });
    if (contexts.filter(Set::isEmpty).isPresent()
      || (contexts.isEmpty() && !checks.contains(MemberAccessCheckKind.EXISTENCE))) {
      return;
    }
    accesses.add(new MemberAccessSite(name, caller, contexts, unqualified, method, accessChain(name)));
  }


  /**
   * Ближайшая цепочка; обращения в аргументах проверяются независимо.
   * Отдельное имя вне цепочки представляет самостоятельное обращение.
   */
  private static ParseTree accessChain(TerminalNode node) {
    for (var parent = node.getParent(); parent != null; parent = parent.getParent()) {
      if (parent instanceof BSLParser.ComplexIdentifierContext || parent instanceof BSLParser.CallStatementContext) {
        return parent;
      }
    }
    return node;
  }
}
