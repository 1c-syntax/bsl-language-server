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
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.MemberAccessChecker.Problem;
import com.github._1c_syntax.bsl.languageserver.types.MemberAccessChecker.UnavailableMember;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.mdo.CommonModule;
import org.antlr.v4.runtime.tree.ParseTree;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Проблемы файла и уже недоступные звенья цепочек; сохраняет порядок и убирает зависимые дубли. */
final class MemberAccessProblems {
  private final DocumentContext documentContext;
  private final CompilationContextResolver contextResolver;
  private final List<Problem> problems = new ArrayList<>();
  private final Map<ParseTree, Set<ExecutionContext>> unavailableChains = new IdentityHashMap<>();

  MemberAccessProblems(DocumentContext document, CompilationContextResolver resolver) {
    documentContext = document;
    contextResolver = resolver;
  }

  void add(Problem problem) {
    problems.add(problem);
  }

  List<Problem> results() {
    return List.copyOf(problems);
  }

  Set<ExecutionContext> remainingContexts(MemberAccessSite access) {
    var remaining = ExecutionContext.copyOf(access.contexts().orElseThrow());
    remaining.removeAll(unavailableChains.getOrDefault(access.chain(), Set.of()));
    return remaining;
  }

  void reportPlatformUnavailable(MemberAccessSite access, ExecutionContext context) {
    var common = access.unqualified() && !access.method()
      ? documentContext.getServerContext().findCommonModule(access.node().getText())
        .filter(module -> !contextResolver.canSeeCommonModule(module, context))
      : Optional.<CommonModule>empty();
    reportUnavailable(access, context, common.map(CommonModule::getName), common.isPresent());
  }

  void reportGlobalUnavailable(MemberAccessSite access, ExecutionContext context) {
    reportUnavailable(access, context, Optional.empty(), false);
  }

  void reportSourceUnavailable(MemberAccessSite access, ExecutionContext context, DocumentContext owner,
                               boolean singleOwner, boolean moduleUnavailable) {
    var common = singleOwner ? commonModule(owner) : Optional.<CommonModule>empty();
    reportUnavailable(access, context, common.map(CommonModule::getName), common.isPresent() && moduleUnavailable);
  }

  /** Первое недоступное звено цепочки заменяет сообщения о зависящих от него обращениях в том же режиме. */
  private void reportUnavailable(MemberAccessSite access, ExecutionContext context, Optional<String> moduleName,
                                  boolean moduleUnavailable) {
    problems.add(new UnavailableMember(Ranges.create(access.node()), access.node().getText(), context,
      access.method() ? MemberKind.METHOD : MemberKind.PROPERTY, moduleName, moduleUnavailable));
    unavailableChains.computeIfAbsent(access.chain(), ignored -> ExecutionContext.copyOf(Set.of())).add(context);
  }

  private static Optional<CommonModule> commonModule(DocumentContext document) {
    return document.getMdObject().filter(CommonModule.class::isInstance).map(CommonModule.class::cast);
  }
}
