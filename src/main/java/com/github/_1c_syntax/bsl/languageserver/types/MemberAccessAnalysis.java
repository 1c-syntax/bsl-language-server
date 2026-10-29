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
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CallAvailability;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.TypeService.TypedMember;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.jspecify.annotations.Nullable;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import com.github._1c_syntax.bsl.languageserver.types.MemberAccessChecker.UnknownMember;

/** Проверка собранных обращений; все результаты и кеши принадлежат одному файлу. */
final class MemberAccessAnalysis {
  private final DocumentContext documentContext;
  private final Set<MemberAccessCheckKind> checks;
  private final MemberAccessContexts contextData;
  private final CompilationContextResolver contextResolver;
  private final TypeService typeService;
  private final GlobalMemberTargets globals;
  private final MemberAccessProblems problems;

  MemberAccessAnalysis(DocumentContext document, Set<MemberAccessCheckKind> checks, MemberAccessContexts contextData,
                       CompilationContextResolver contextResolver, TypeService typeService,
                       GlobalMemberTargets globals, MemberAccessProblems problems) {
    documentContext = document;
    this.checks = Set.copyOf(checks);
    this.contextData = contextData;
    this.contextResolver = contextResolver;
    this.typeService = typeService;
    this.globals = globals;
    this.problems = problems;
  }

  void checkAccess(MemberAccessSite access) {
    if (checks.contains(MemberAccessCheckKind.EXISTENCE)) {
      checkExistence(access);
    }
    if (checks.contains(MemberAccessCheckKind.AVAILABILITY) && access.contexts().isPresent()) {
      checkAvailability(access);
    }
  }

  private void checkAvailability(MemberAccessSite access) {
    var remaining = problems.remainingContexts(access);
    if (remaining.isEmpty()) {
      return;
    }
    if (!access.method()) {
      checkPlatformAccess(access, typeService.accessTargetsAt(documentContext, access.node(), null), remaining);
      return;
    }
    if (!access.unqualified()) {
      checkQualifiedCall(access, remaining);
      return;
    }
    checkUnqualifiedCall(access, remaining);
  }

  private void checkUnqualifiedCall(MemberAccessSite access, Set<ExecutionContext> remaining) {
    var name = access.node().getText();
    var local = contextResolver.declarations(documentContext).getOrDefault(name, List.of());
    if (local.isEmpty() && globals.isPlatformFunction(name)) {
      checkPlatformAccess(access, typeService.accessTargetsAt(documentContext, access.node(), null), remaining);
      return;
    }
    var targets = local.isEmpty() ? globals.sourceTargets(name)
      : local.stream().map(d -> new MemberCallTarget(documentContext, d)).toList();
    if (targets.isEmpty()) {
      checkPlatformAccess(access, typeService.accessTargetsAt(documentContext, access.node(), null), remaining);
    } else {
      checkCall(access, targets, remaining);
    }
  }

  /**
   * Проверить известные типы; наличие члена сохраняется независимо от доступности его объявления.
   */
  private void checkExistence(MemberAccessSite access) {
    var node = access.node();
    if (access.unqualified()) {
      if (!access.method()) {
        return;
      }
      if (typeService.isUnknownGlobalAt(documentContext, node)) {
        var context = access.contexts().flatMap(contexts -> ExecutionContext.copyOf(contexts).stream().findFirst());
        problems.add(new UnknownMember(Ranges.create(node), node.getText(), TypeSet.EMPTY, context));
      }
      return;
    }
    if (access.contexts().isEmpty()) {
      typeService.unknownMemberReceiverAt(documentContext, node).ifPresent(types ->
        problems.add(new UnknownMember(Ranges.create(node), node.getText(), types, Optional.empty())));
      return;
    }
    for (var context : ExecutionContext.copyOf(access.contexts().orElseThrow())) {
      var missing = typeService.unknownMemberReceiverAt(documentContext, node, context);
      if (missing.isPresent()) {
        problems.add(new UnknownMember(Ranges.create(node), node.getText(), missing.orElseThrow(), Optional.of(context)));
        return;
      }
    }
  }

  /**
   * Проверить исходные и платформенные цели. Нейтральный поиск отбирает кандидатов;
   * уточнение типа по режиму требуется только перед сообщением о недоступности.
   */
  private void checkQualifiedCall(MemberAccessSite call, Set<ExecutionContext> remainingContexts) {
    var members = typeService.accessTargetsAt(documentContext, call.node(), null);
    Set<URI> checked = new HashSet<>();
    for (var member : members) {
      if (member.descriptor().getSourceSymbol().orElse(null) instanceof MethodSymbol method
        && checked.add(method.getOwner().getUri())) {
        checkSourceOwner(call, method, remainingContexts);
        if (remainingContexts.isEmpty()) {
          return;
        }
      }
    }
    checkPlatformAccess(call, members, remainingContexts);
  }

  private void checkSourceOwner(MemberAccessSite call, MethodSymbol method, Set<ExecutionContext> remaining) {
    var owner = method.getOwner();
    var targets = contextResolver.declarations(owner).getOrDefault(method.getName(), List.of()).stream()
      .filter(declaration -> owner.getUri().equals(documentContext.getUri()) || declaration.exported())
      .map(declaration -> new MemberCallTarget(owner, declaration)).toList();
    if (!targets.isEmpty()) {
      checkCall(call, targets, remaining);
    }
  }

  /**
   * Проверить методы и свойства СП, включая тип владельца и значения глобальных свойств.
   * Нейтральные цели отбирают потенциальный запрет; контекстный вывод подтверждает его
   * только перед сообщением. Альтернативы одного владельца рассматриваются совместно.
   */
  private void checkPlatformAccess(MemberAccessSite access, List<TypedMember> members, Set<ExecutionContext> remaining) {
    var expected = access.method() ? MemberKind.METHOD : MemberKind.PROPERTY;
    var byOwner = platformTargets(members, expected);
    for (var entry : byOwner.entrySet()) {
      checkPlatformOwner(access, entry.getKey(), entry.getValue(), expected, remaining);
    }
  }

  private static Map<@Nullable TypeRef, List<TypedMember>> platformTargets(List<TypedMember> members, MemberKind expected) {
    Map<@Nullable TypeRef, List<TypedMember>> byOwner = new LinkedHashMap<>();
    for (var member : members) {
      if (member.descriptor().kind() == expected && !TypeRef.ANY.equals(member.owner())
        && !(member.descriptor().getSourceSymbol().orElse(null) instanceof MethodSymbol)) {
        byOwner.computeIfAbsent(member.owner(), ignored -> new ArrayList<>()).add(member);
      }
    }
    return byOwner;
  }

  private void checkPlatformOwner(MemberAccessSite access, @Nullable TypeRef owner, List<TypedMember> candidates,
                                  MemberKind expected, Set<ExecutionContext> remaining) {
    for (var iterator = remaining.iterator(); iterator.hasNext();) {
      var context = iterator.next();
      if (platformUnavailableInContext(access, owner, candidates, expected, context)) {
        problems.reportPlatformUnavailable(access, context);
        iterator.remove();
      }
    }
  }

  private boolean platformUnavailableInContext(MemberAccessSite access, @Nullable TypeRef owner,
                                               List<TypedMember> candidates, MemberKind expected,
                                               ExecutionContext context) {
    if (candidates.stream().anyMatch(member -> !contextData.platformUnavailable(member, context))) {
      return false;
    }
    var actual = contextData.targetsInContext(access, context).stream()
      .filter(member -> Objects.equals(member.owner(), owner) && member.descriptor().kind() == expected).toList();
    return !actual.isEmpty() && actual.stream().allMatch(member -> contextData.platformUnavailable(member, context));
  }

  /**
   * Проверить альтернативные объявления одной цели. Доступной или неизвестной
   * альтернативы достаточно для отсутствия доказанного запрета. Исключённое
   * локальное объявление допускает разрешение голого имени через глобальный контекст.
   * Контексты с найденной проблемой удаляются из {@code remainingContexts}, чтобы
   * другие типы получателя не создавали повторного замечания в том же контексте.
   */
  private void checkCall(MemberAccessSite call, List<MemberCallTarget> targets, Set<ExecutionContext> remainingContexts) {
    var local = call.unqualified() && targets.stream()
      .allMatch(target -> target.owner().getUri().equals(documentContext.getUri()));
    for (var iterator = remainingContexts.iterator(); iterator.hasNext();) {
      if (checkCallInContext(call, targets, iterator.next(), local)) {
        iterator.remove();
      }
    }
  }

  private boolean checkCallInContext(MemberAccessSite call, List<MemberCallTarget> targets, ExecutionContext context, boolean local) {
    var candidates = targets;
    if (local && targets.stream().allMatch(target -> localDeclarationIsAbsent(target, context))) {
      var name = call.node().getText();
      if (globals.isPlatformFunction(name)) {
        return checkGlobalCall(call, context, name);
      }
      var global = globals.sourceTargets(name);
      if (!global.isEmpty()) {
        candidates = global;
      }
    }
    return checkCandidates(call, candidates, context);
  }

  private boolean checkGlobalCall(MemberAccessSite call, ExecutionContext context, String name) {
    var member = globals.platformFunction(name, call.node());
    if (!contextData.platformUnavailable(member, context)) {
      return false;
    }
    problems.reportGlobalUnavailable(call, context);
    return true;
  }

  private boolean checkCandidates(MemberAccessSite call, List<MemberCallTarget> candidates, ExecutionContext context) {
    var results = candidates.stream().map(target -> contextResolver.availability(
      documentContext, call.caller().orElseThrow(), target.owner(), target.declaration(),
      contextData.methodContext(target.owner(), target.declaration().directive()), context)).toList();
    if (results.contains(CallAvailability.AVAILABLE) || results.contains(CallAvailability.UNKNOWN)) {
      return false;
    }
    var owner = candidates.getFirst().owner();
    if (!call.unqualified() && !targetExistsInContext(call, context, owner.getUri())) {
      return false;
    }
    var singleOwner = candidates.stream().allMatch(t -> t.owner().getUri().equals(owner.getUri()));
    problems.reportSourceUnavailable(call, context, owner, singleOwner,
      results.stream().allMatch(result -> result == CallAvailability.MODULE_UNAVAILABLE));
    return true;
  }


  /** Проверить, остался ли исходный владелец среди целей контекстного вывода. */
  private boolean targetExistsInContext(MemberAccessSite call, ExecutionContext context, URI owner) {
    return contextData.targetsInContext(call, context).stream().map(member -> member.descriptor().getSourceSymbol())
      .flatMap(Optional::stream).filter(MethodSymbol.class::isInstance).map(MethodSymbol.class::cast)
      .anyMatch(method -> method.getOwner().getUri().equals(owner));
  }

  /**
   * Определить, исключено ли локальное объявление из компиляции после перехода в его контекст.
   * Недостаток данных даёт {@code false}: он не позволяет разрешать имя как глобальное
   * на основании якобы отсутствующего локального метода.
   */
  private boolean localDeclarationIsAbsent(MemberCallTarget target, ExecutionContext context) {
    var definition = target.declaration();
    var method = contextData.methodContext(target.owner(), definition.directive());
    var branch = definition.branch();
    if (method.isEmpty() || branch == null) {
      return false;
    }
    var targetContext = contextResolver.targetContext(documentContext, target.owner(), method.get(), context);
    return !method.get().contexts().contains(targetContext) || !branch.contains(targetContext);
  }

}
