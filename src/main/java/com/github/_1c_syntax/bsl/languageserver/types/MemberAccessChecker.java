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
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.CompilerDirectiveKind;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CallAvailability;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver.Declaration;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver.MethodContext;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.Availability;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.TypeService.TypedMember;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.mdo.CommonModule;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseListener;
import com.github._1c_syntax.bsl.types.ModuleType;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Проверка существования и доступности членов без формирования диагностических сообщений.
 * <p>
 * Контекст уточняет тип получателя; найденное объявление не исчезает из поиска из-за
 * недоступности. Отсутствующий член и запрещённый вызов одной цели в одном режиме
 * поэтому не дают двух ошибок. Разные получатели или режимы могут иметь разные ошибки.
 * <p>
 * Состояние обхода и кеши принадлежат одному вызову {@link #check}; компонент не удерживает AST.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class MemberAccessChecker {
  private final CompilationContextResolver contextResolver;
  private final TypeService typeService;
  private final GlobalScopeProvider globalScopeProvider;

  /**
   * Запрошенная проверка: выключенные диагностики не требуют вычислять их результаты.
   */
  public enum Check {EXISTENCE, AVAILABILITY}

  /**
   * Доказанная проблема обращения; допустимые и неразрешённые обращения проблем не создают.
   */
  public sealed interface Problem permits UnknownMember, UnavailableMember {
  }

  /**
   * Член отсутствует у известных типов получателя либо неизвестно голое имя.
   *
   * @param range         диапазон имени.
   * @param name          имя обращения.
   * @param receiverTypes типы без этого члена; пустой набор для голого имени.
   * @param context       режим, в котором установлена ошибка; пусто для проверки без известного режима.
   */
  public record UnknownMember(Range range, String name, TypeSet receiverTypes,
                              Optional<ExecutionContext> context) implements Problem {
  }

  /**
   * Известный метод или свойство, обращение к которому запрещено в данном режиме.
   *
   * @param range             диапазон имени обращения.
   * @param name              имя члена.
   * @param context           режим вызывающего кода.
   * @param kind              метод или свойство.
   * @param moduleName        имя общего модуля, если все альтернативные объявления принадлежат ему.
   * @param moduleUnavailable недоступен сам общий модуль, а не только его метод.
   */
  public record UnavailableMember(Range range, String name, ExecutionContext context, MemberKind kind,
                                  Optional<String> moduleName, boolean moduleUnavailable) implements Problem {
  }

  /**
   * Проверить обращения файла в выбранных режимах.
   * <p>
   * На одно обращение возвращается не более одной проблемы существования члена
   * и по одной проблеме доступности вызова на каждый контекст. При неизвестном контексте
   * сохраняется обычная проверка имён; доступность вызова не предполагается.
   * OneScript использует собственную достижимость препроцессора и проверку имён без режимов 1С.
   *
   * @param document        документ с доступным AST.
   * @param enabledContexts выбранные пользователем режимы; не расширяют возможности модуля.
   * @param checks          требуемые виды проверки.
   * @return неизменяемый список проблем в порядке исходного текста.
   */
  public List<Problem> check(DocumentContext document, Set<ExecutionContext> enabledContexts, Set<Check> checks) {
    var analysis = new Analysis(document, enabledContexts, checks);
    ParseTreeWalker.DEFAULT.walk(analysis, document.getAst());
    return analysis.checkAccesses();
  }

  /**
   * Снимок обращения; пустой Optional контекстов означает неизвестность, а не недостижимость.
   */
  private record AccessSite(TerminalNode node, Optional<MethodContext> caller,
                            Optional<Set<ExecutionContext>> contexts, boolean unqualified, boolean method,
                            ParseTree chain) {
  }

  /**
   * Одно из альтернативных исходных объявлений метода.
   */
  private record Target(DocumentContext owner, Declaration declaration) {
  }

  /**
   * Профиль документа и директивы без условия конкретного объявления.
   */
  private record ProfileKey(URI uri, Optional<CompilerDirectiveKind> directive) {
  }

  /**
   * Уточнение целей обращения в выбранном режиме.
   */
  private record ContextualAccess(Position position, ExecutionContext context) {
  }

  private final class Analysis extends BSLParserBaseListener {
    private final DocumentContext documentContext;
    private final Set<ExecutionContext> enabledContexts;
    private final Set<Check> checks;
    private final PreprocessorReachability preprocessor;
    private final List<AccessSite> accesses = new ArrayList<>();
    private final List<Problem> problems = new ArrayList<>();
    private final Map<String, List<Target>> globalTargets = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<ProfileKey, Optional<MethodContext>> profiles = new HashMap<>();
    private final Map<ContextualAccess, List<TypedMember>> contextualTargets = new HashMap<>();
    private final Map<TypeRef, Optional<Set<ExecutionContext>>> typeContexts = new HashMap<>();
    private final Map<Set<Availability>, Set<ExecutionContext>> platformContexts = new HashMap<>();
    private final Map<ParseTree, Set<ExecutionContext>> unavailableChains = new IdentityHashMap<>();
    private final List<DocumentContext> globalModules = new ArrayList<>();
    private Optional<MethodContext> caller;
    private boolean globalsCollected;
    private boolean unreachable;

    private Analysis(DocumentContext document, Set<ExecutionContext> enabled, Set<Check> checks) {
      documentContext = document;
      enabledContexts = enabled;
      this.checks = checks;
      preprocessor = new PreprocessorReachability(document.getFileType());
      caller = methodContext(document, Optional.empty());
    }

    @Override
    public void enterSub(BSLParser.SubContext ctx) {
      caller = documentContext.getSymbolTree().getMethodSymbol(ctx)
        .flatMap(method -> methodContext(documentContext, method.getCompilerDirectiveKind()));
    }

    @Override
    public void exitSub(BSLParser.SubContext ctx) {
      caller = methodContext(documentContext, Optional.empty());
    }

    @Override
    public void enterPreproc_if(BSLParser.Preproc_ifContext ctx) {
      preprocessor.enterIf(ctx);
      unreachable = preprocessor.isUnreachable(contextResolver.supportedContexts(documentContext));
    }

    @Override
    public void enterPreproc_elsif(BSLParser.Preproc_elsifContext ctx) {
      preprocessor.enterElsif(ctx);
      unreachable = preprocessor.isUnreachable(contextResolver.supportedContexts(documentContext));
    }

    @Override
    public void enterPreproc_else(BSLParser.Preproc_elseContext ctx) {
      preprocessor.enterElse();
      unreachable = preprocessor.isUnreachable(contextResolver.supportedContexts(documentContext));
    }

    @Override
    public void enterPreproc_endif(BSLParser.Preproc_endifContext ctx) {
      preprocessor.enterEndif();
      unreachable = preprocessor.isUnreachable(contextResolver.supportedContexts(documentContext));
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
      if (checks.contains(Check.AVAILABILITY) && name != null
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
      var contexts = caller.flatMap(profile -> {
        var active = ExecutionContext.copyOf(profile.contexts());
        active.retainAll(enabledContexts);
        if (branch != null) {
          active.retainAll(branch);
        }
        return branch != null || active.isEmpty() ? Optional.of(Set.copyOf(active)) : Optional.empty();
      });
      if (contexts.filter(Set::isEmpty).isPresent()
        || contexts.isEmpty() && !checks.contains(Check.EXISTENCE)) {
        return;
      }
      accesses.add(new AccessSite(name, caller, contexts, unqualified, method, accessChain(name)));
    }

    private List<Problem> checkAccesses() {
      for (var access : accesses) {
        if (checks.contains(Check.EXISTENCE)) {
          checkExistence(access);
        }
        if (!checks.contains(Check.AVAILABILITY) || access.contexts().isEmpty()) {
          continue;
        }
        var remaining = ExecutionContext.copyOf(access.contexts().orElseThrow());
        remaining.removeAll(unavailableChains.getOrDefault(access.chain(), Set.of()));
        if (remaining.isEmpty()) {
          continue;
        }
        if (!access.method()) {
          checkPlatformAccess(access, typeService.accessTargetsAt(documentContext, access.node(), null), remaining);
          continue;
        }
        if (!access.unqualified()) {
          checkQualifiedCall(access, remaining);
          continue;
        }
        var name = access.node().getText();
        var local = contextResolver.declarations(documentContext).getOrDefault(name, List.of());
        if (local.isEmpty() && isPlatformGlobal(name)) {
          checkPlatformAccess(access, typeService.accessTargetsAt(documentContext, access.node(), null), remaining);
          continue;
        }
        var targets = local.isEmpty() ? globals(name) : local.stream().map(d -> new Target(documentContext, d)).toList();
        if (!targets.isEmpty()) {
          checkCall(access, targets, remaining);
        } else {
          checkPlatformAccess(access, typeService.accessTargetsAt(documentContext, access.node(), null), remaining);
        }
      }
      return List.copyOf(problems);
    }

    /**
     * Проверить известные типы; наличие члена сохраняется независимо от доступности его объявления.
     */
    private void checkExistence(AccessSite access) {
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
    private void checkQualifiedCall(AccessSite call, Set<ExecutionContext> remainingContexts) {
      var members = typeService.accessTargetsAt(documentContext, call.node(), null);
      Set<URI> checked = new HashSet<>();
      for (var member : members) {
        if (!(member.descriptor().getSourceSymbol().orElse(null) instanceof MethodSymbol method)) {
          continue;
        }
        var owner = method.getOwner();
        if (!checked.add(owner.getUri())) {
          continue;
        }
        var targets = contextResolver.declarations(owner).getOrDefault(method.getName(), List.of()).stream()
          .filter(declaration -> owner.getUri().equals(documentContext.getUri()) || declaration.exported())
          .map(declaration -> new Target(owner, declaration)).toList();
        // Ветки одного объявления — альтернативы; разные типы получателя проверяются независимо.
        if (!targets.isEmpty()) {
          checkCall(call, targets, remainingContexts);
          if (remainingContexts.isEmpty()) {
            return;
          }
        }
      }
      checkPlatformAccess(call, members, remainingContexts);
    }

    /**
     * Проверить методы и свойства СП, включая тип владельца и значения глобальных свойств.
     * Нейтральные цели отбирают потенциальный запрет; контекстный вывод подтверждает его
     * только перед сообщением. Альтернативы одного владельца рассматриваются совместно.
     */
    private void checkPlatformAccess(AccessSite access, List<TypedMember> members, Set<ExecutionContext> remaining) {
      Map<TypeRef, List<TypedMember>> byOwner = new LinkedHashMap<>();
      var expected = access.method() ? MemberKind.METHOD : MemberKind.PROPERTY;
      for (var member : members) {
        if (member.descriptor().kind() != expected || TypeRef.ANY.equals(member.owner())
            || member.descriptor().getSourceSymbol().orElse(null) instanceof MethodSymbol) {
          continue;
        }
        byOwner.computeIfAbsent(member.owner(), ignored -> new ArrayList<>()).add(member);
      }
      for (var entry : byOwner.entrySet()) {
        for (var iterator = remaining.iterator(); iterator.hasNext();) {
          var context = iterator.next();
          if (entry.getValue().stream().anyMatch(member -> !platformUnavailable(member, context))) {
            continue;
          }
          var actual = targetsInContext(access, context).stream()
            .filter(member -> Objects.equals(member.owner(), entry.getKey()) && member.descriptor().kind() == expected)
            .toList();
          if (actual.isEmpty() || actual.stream().anyMatch(member -> !platformUnavailable(member, context))) {
            continue;
          }
          var common = access.unqualified() && !access.method()
            ? documentContext.getServerContext().findCommonModule(access.node().getText())
              .filter(module -> !contextResolver.canSeeCommonModule(module, context))
            : Optional.<CommonModule>empty();
          reportUnavailable(access, context, common.map(CommonModule::getName), common.isPresent());
          iterator.remove();
        }
      }
    }

    /** Неизвестные данные СП не доказывают запрет; известное ограничение владельца или члена доказывает. */
    private boolean platformUnavailable(TypedMember member, ExecutionContext context) {
      var available = member.descriptor().metadata().availabilities();
      if (!available.isEmpty()
          && !platformContexts.computeIfAbsent(available, contextResolver::platformContexts).contains(context)) {
        return true;
      }
      var owner = member.owner();
      if (owner != null) {
        return typeUnavailable(owner, context);
      }
      return member.descriptor().kind() == MemberKind.PROPERTY
        && member.descriptor().returnTypes().refs().stream().anyMatch(type -> typeUnavailable(type, context));
    }

    private boolean typeUnavailable(TypeRef type, ExecutionContext context) {
      return typeContexts.computeIfAbsent(type, value -> contextResolver.typeContexts(documentContext, value))
        .map(contexts -> !contexts.contains(context)).orElse(false);
    }

    /** Первое недоступное звено цепочки заменяет сообщения о зависящих от него обращениях в том же режиме. */
    private void reportUnavailable(AccessSite access, ExecutionContext context, Optional<String> moduleName,
                                    boolean moduleUnavailable) {
      problems.add(new UnavailableMember(Ranges.create(access.node()), access.node().getText(), context,
        access.method() ? MemberKind.METHOD : MemberKind.PROPERTY, moduleName, moduleUnavailable));
      unavailableChains.computeIfAbsent(access.chain(), ignored -> ExecutionContext.copyOf(Set.of())).add(context);
    }


    /** Найти альтернативные экспортные объявления глобальных модулей и модулей приложения/соединения. */
    private List<Target> globals(String name) {
      if (!globalsCollected) {
        globalsCollected = true;
        var serverContext = documentContext.getServerContext();
        for (var module : serverContext.getConfiguration().getCommonModules()) {
          if (!module.isGlobal()) {
            continue;
          }
          serverContext.getDocument(module.getMdoRef(), ModuleType.CommonModule).ifPresent(globalModules::add);
        }
        var configuration = serverContext.getConfiguration().getMergedConfiguration();
        for (var type : List.of(ModuleType.OrdinaryApplicationModule, ModuleType.ManagedApplicationModule,
          ModuleType.ExternalConnectionModule)) {
          serverContext.getDocument(configuration.getMdoRef(), type).ifPresent(globalModules::add);
        }
      }
      return globalTargets.computeIfAbsent(name, ignored -> {
        List<Target> targets = new ArrayList<>();
        for (var owner : globalModules) {
          for (var definition : contextResolver.declarations(owner).getOrDefault(name, List.of())) {
            if (definition.exported()) {
              targets.add(new Target(owner, definition));
            }
          }
        }
        return List.copyOf(targets);
      });
    }


    /**
     * Проверить альтернативные объявления одной цели. Доступной или неизвестной
     * альтернативы достаточно для отсутствия доказанного запрета. Исключённое
     * локальное объявление допускает разрешение голого имени через глобальный контекст.
     * Контексты с найденной проблемой удаляются из {@code remainingContexts}, чтобы
     * другие типы получателя не создавали повторного замечания в том же контексте.
     */
    private void checkCall(AccessSite call, List<Target> targets, Set<ExecutionContext> remainingContexts) {
      var name = call.node().getText();
      var local = call.unqualified() && targets.stream()
        .allMatch(target -> target.owner().getUri().equals(documentContext.getUri()));
      for (var iterator = remainingContexts.iterator(); iterator.hasNext();) {
        var context = iterator.next();
        var candidates = targets;
        if (local && targets.stream().allMatch(target -> localDeclarationIsAbsent(target, context))) {
          if (isPlatformGlobal(name)) {
            var member = globalScopeProvider.globalFunction(name, documentContext.getFileType()).orElseThrow();
            if (platformUnavailable(new TypedMember(null, member, Ranges.create(call.node())), context)) {
              reportUnavailable(call, context, Optional.empty(), false);
              iterator.remove();
            }
            continue;
          }
          var global = globals(name);
          if (!global.isEmpty()) {
            candidates = global;
          }
        }
        var results = candidates.stream().map(target -> contextResolver.availability(
          documentContext, call.caller().orElseThrow(), target.owner(), target.declaration(),
          methodContext(target.owner(), target.declaration().directive()), context)).toList();
        if (results.contains(CallAvailability.AVAILABLE) || results.contains(CallAvailability.UNKNOWN)) {
          continue;
        }
        var owner = candidates.getFirst().owner();
        if (!call.unqualified() && !targetExistsInContext(call, context, owner.getUri())) {
          continue;
        }
        var singleOwner = candidates.stream().allMatch(t -> t.owner().getUri().equals(owner.getUri()));
        var common = singleOwner ? commonModule(owner) : Optional.<CommonModule>empty();
        reportUnavailable(call, context, common.map(CommonModule::getName),
          common.isPresent() && results.stream().allMatch(result -> result == CallAvailability.MODULE_UNAVAILABLE));
        iterator.remove();
      }
    }


    /** Проверить, остался ли исходный владелец среди целей контекстного вывода. */
    private boolean targetExistsInContext(AccessSite call, ExecutionContext context, URI owner) {
      return targetsInContext(call, context).stream().map(member -> member.descriptor().getSourceSymbol())
        .flatMap(Optional::stream).filter(MethodSymbol.class::isInstance).map(MethodSymbol.class::cast)
        .anyMatch(method -> method.getOwner().getUri().equals(owner));
    }

    /** Один контекстный вывод на обращение используется для исходных и платформенных целей. */
    private List<TypedMember> targetsInContext(AccessSite access, ExecutionContext context) {
      return contextualTargets.computeIfAbsent(new ContextualAccess(startOf(access.node()), context),
        ignored -> typeService.accessTargetsAt(documentContext, access.node(), context));
    }

    private boolean isPlatformGlobal(String name) {
      return globalScopeProvider.globalFunction(name, documentContext.getFileType())
        .filter(member -> member.getSourceSymbol().isEmpty()).isPresent();
    }

    /**
     * Определить, исключено ли локальное объявление из компиляции после перехода в его контекст.
     * Недостаток данных даёт {@code false}: он не позволяет разрешать имя как глобальное
     * на основании якобы отсутствующего локального метода.
     */
    private boolean localDeclarationIsAbsent(Target target, ExecutionContext context) {
      var definition = target.declaration();
      var method = methodContext(target.owner(), definition.directive());
      if (method.isEmpty() || definition.branch() == null) {
        return false;
      }
      var targetContext = contextResolver.targetContext(documentContext, target.owner(), method.get(), context);
      return !method.get().contexts().contains(targetContext) || !definition.branch().contains(targetContext);
    }


    private Optional<CommonModule> commonModule(DocumentContext document) {
      return document.getMdObject().filter(CommonModule.class::isInstance).map(CommonModule.class::cast);
    }

    /** Повторно использовать профиль документа и директивы только в пределах проверки текущего файла. */
    private Optional<MethodContext> methodContext(DocumentContext document, Optional<CompilerDirectiveKind> directive) {
      return profiles.computeIfAbsent(new ProfileKey(document.getUri(), directive),
        ignored -> contextResolver.methodContext(document, directive));
    }

  }

  private static Position startOf(TerminalNode node) {
    return Ranges.create(node).getStart();
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
