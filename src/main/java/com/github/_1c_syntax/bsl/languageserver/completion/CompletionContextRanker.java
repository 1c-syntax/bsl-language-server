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
package com.github._1c_syntax.bsl.languageserver.completion;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.Resources;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.CompilerDirectiveKind;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CallAvailability;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver.Declaration;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver.MethodContext;
import com.github._1c_syntax.bsl.languageserver.types.model.Availability;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemLabelDetails;
import org.eclipse.lsp4j.Position;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Приоритеты доступности и подписи контекстов для одного запроса completion.
 * <p>
 * Общая оценка используется для отбора и сортировки: частично доступные следуют за обычными,
 * полностью недоступные — последними, если пользователь включил их показ.
 * Недостаток сведений не понижает приоритет. Исходные корзины, признак устаревания
 * и порядок имён сохраняются внутри этих групп.
 * Приписка к документации описывает компиляцию метода, а стрелка — серверный переход именно из
 * запрошенной позиции. Смешанные или неполные сведения о вызове дают условную стрелку.
 * Все кеши ограничены запросом; объект нельзя сохранять в провайдере между запросами.
 */
public final class CompletionContextRanker {
  private static final int AVAILABLE = 0;
  private static final int PARTIAL = 1;
  private static final int UNAVAILABLE = 2;

  private final DocumentContext document;
  private final Position position;
  private final CompilationContextResolver resolver;
  private final TypeService types;
  private final Optional<MethodContext> scope;
  private final Map<CompletionItem, Integer> priorities = new IdentityHashMap<>();
  private final Map<CompletionItem, MethodHint> hints = new IdentityHashMap<>();
  private final Map<MethodSymbol, List<MethodTarget>> methodTargets = new IdentityHashMap<>();
  private final Map<ProfileKey, Optional<MethodContext>> profiles = new HashMap<>();
  private final Map<ExecutionContext, TypeSet> receivers = new HashMap<>();
  private final Map<TypeRef, Map<String, List<MemberDescriptor>>> members = new HashMap<>();
  private final Map<Set<Availability>, Set<ExecutionContext>> platformContexts = new HashMap<>();
  private final Map<TypeRef, Optional<Set<ExecutionContext>>> typeContexts = new HashMap<>();

  /** Профиль цели без условия конкретного объявления. */
  private record ProfileKey(URI uri, Optional<CompilerDirectiveKind> directive) { }

  /** Объявление и вычисленные один раз профиль и контексты его компиляции. */
  private record MethodTarget(DocumentContext owner, Declaration declaration, Optional<MethodContext> profile,
                              Optional<Set<ExecutionContext>> contexts) { }

  /** Наблюдения для разных режимов и возможных целей одного пункта completion. */
  private enum CallPath {LOCAL, SERVER, UNKNOWN, UNAVAILABLE}

  /** Состояние одного пункта, собираемое вместе с проверкой доступности. */
  private static final class MethodHint {
    private final Set<ExecutionContext> contexts = EnumSet.noneOf(ExecutionContext.class);
    private final Set<CallPath> paths = EnumSet.noneOf(CallPath.class);
    private boolean knownContexts = true;

    /** Объединить контексты объявлений, сохранив отсутствие сведений у любой альтернативы. */
    private void include(Optional<Set<ExecutionContext>> declared) {
      if (declared.isEmpty()) {
        knownContexts = false;
      } else {
        contexts.addAll(declared.orElseThrow());
      }
    }
  }

  /**
   * Подготовить контекст один раз для всех предлагаемых имён.
   *
   * @param document документ запроса.
   * @param position позиция курсора, в том числе после незавершённой точки.
   * @param resolver общая модель компиляции и серверных переходов.
   * @param types служба вывода типов и поиска членов.
   */
  public CompletionContextRanker(DocumentContext document, Position position, CompilationContextResolver resolver,
                                 TypeService types) {
    this.document = document;
    this.position = position;
    this.resolver = resolver;
    this.types = types;
    scope = resolver.contextAt(document, position).map(profile -> {
      var contexts = ExecutionContext.copyOf(profile.contexts());
      contexts.retainAll(resolver.automaticContexts(document));
      return new MethodContext(profile.directive(), Set.copyOf(contexts));
    }).filter(profile -> !profile.contexts().isEmpty());
  }

  /**
   * Оценить член без уточнения получателя: глобальную функцию, свойство или неявный self-член.
   *
   * @param item сформированный пункт completion.
   * @param member предлагаемый член.
   */
  public void rankMember(CompletionItem item, MemberDescriptor member) {
    if (member.kind() == MemberKind.METHOD && scope.isPresent()) {
      var hint = new MethodHint();
      remember(item, acrossContexts(context -> methodRank(member, context, hint)));
      hints.put(item, hint);
    } else {
      remember(item, acrossContexts(context -> memberRank(member, context)));
    }
  }

  /**
   * Оценить конкретное локальное объявление, сохраняя различие одноимённых веток.
   *
   * @param item пункт локального метода.
   * @param method объявление, чья сигнатура вставляется этим пунктом.
   */
  public void rankLocalMethod(CompletionItem item, MethodSymbol method) {
    if (scope.isEmpty()) {
      return;
    }
    var owner = method.getOwner();
    var targets = List.of(target(owner, new Declaration(method.getCompilerDirectiveKind(),
      owner.getSymbolTreeNoLock().getMethodPreprocessorContexts(method), method.isExport())));
    var hint = new MethodHint();
    remember(item, acrossContexts(context -> methodRank(targets, context, hint)));
    hints.put(item, hint);
  }

  /**
   * Оценить тип после «Новый» либо составное имя метаданных.
   *
   * @param item пункт типа или имени конфигурации.
   * @param name имя, разрешаемое службой типов.
   */
  public void rankType(CompletionItem item, String name) {
    if (scope.isPresent()) {
      types.resolve(name, document.getFileType())
        .ifPresent(type -> remember(item, acrossContexts(context -> typeRank(type, context))));
    }
  }

  /**
   * Оценить член после точки по типам получателя в каждом актуальном режиме.
   *
   * @param item пункт dot-completion.
   * @param member член из исходного объединения типов.
   */
  public void rankDotMember(CompletionItem item, MemberDescriptor member) {
    if (scope.isEmpty()) {
      return;
    }
    var hint = member.kind() == MemberKind.METHOD ? Optional.of(new MethodHint()) : Optional.<MethodHint>empty();
    var name = item.getLabel();
    remember(item, acrossContexts(context -> {
      var receiver = receivers.computeIfAbsent(context, mode -> types.receiverTypesAt(document, position, mode));
      if (receiver.isEmpty()) {
        hint.ifPresent(value -> {
          value.knownContexts = false;
          value.paths.add(CallPath.UNKNOWN);
        });
        return AVAILABLE;
      }
      return combine(receiver.refs().stream().mapToInt(owner -> {
        if (owner.equals(TypeRef.ANY) || owner.equals(TypeRef.UNKNOWN)) {
          hint.ifPresent(value -> {
            value.knownContexts = false;
            value.paths.add(CallPath.UNKNOWN);
          });
          return AVAILABLE;
        }
        if (member.kind() == MemberKind.PROPERTY && receiver.getLocalFields(owner).keySet().stream()
            .anyMatch(name::equalsIgnoreCase)) {
          return typeRank(owner, context);
        }
        var alternatives = members.computeIfAbsent(owner, this::indexMembers)
          .getOrDefault(key(member.kind(), name), List.of());
        if (alternatives.isEmpty()) {
          hint.ifPresent(value -> value.paths.add(CallPath.UNAVAILABLE));
          return UNAVAILABLE;
        }
        var rank = alternatives.stream().mapToInt(candidate -> hint.isPresent()
          ? methodRank(candidate, context, hint.orElseThrow()) : memberRank(candidate, context)).min().orElseThrow();
        return Math.max(typeRank(owner, context), rank);
      }).toArray());
    }));
    hint.ifPresent(value -> hints.put(item, value));
  }

  /**
   * Заведомо ли пункт недоступен во всех проверенных контекстах и для всех известных целей.
   * <p>
   * Частичная доступность и недостаток сведений не дают основания скрывать предложение.
   * Используется уже рассчитанная оценка; дополнительного разрешения типов не выполняется.
   *
   * @param item ранее оценённый пункт либо пункт без ограничений, например ключевое слово.
   * @return {@code true}, если пункт можно исключить из списка по контексту компиляции.
   */
  public boolean isUnavailable(CompletionItem item) {
    return priorities.getOrDefault(item, AVAILABLE) == UNAVAILABLE;
  }

  /**
   * Добавить группу доступности и пометку серверного вызова, сохранив имя, сигнатуру и текст вставки.
   *
   * @param items отобранные предложения, включая переменные и ключевые слова без ограничений.
   * @param labelDetailsSupport поддерживает ли клиент подпись справа от сигнатуры.
   * @return пояснения для добавления после документации метода; ключи сравниваются по идентичности.
   */
  public Map<CompletionItem, String> apply(List<CompletionItem> items, boolean labelDetailsSupport) {
    var language = document.getScriptVariantLanguage();
    Map<CompletionItem, String> descriptions = new IdentityHashMap<>();
    for (var item : items) {
      if (!priorities.isEmpty()) {
        item.setSortText(priorities.getOrDefault(item, AVAILABLE) + "_" + item.getSortText());
      }
      var hint = hints.get(item);
      if (hint != null) {
        var description = applyHint(item, hint, language, labelDetailsSupport);
        if (!description.isBlank()) {
          descriptions.put(item, description);
        }
      }
    }
    return descriptions;
  }

  private void remember(CompletionItem item, int priority) {
    if (priority != AVAILABLE) {
      priorities.put(item, priority);
    }
  }

  private int acrossContexts(ToIntFunction<ExecutionContext> rank) {
    return scope.map(profile -> combine(profile.contexts().stream().mapToInt(rank).toArray())).orElse(AVAILABLE);
  }

  /** Пустой набор означает отсутствие сведений; смешанная доступность — отдельную промежуточную группу. */
  private static int combine(int[] ranks) {
    var available = false;
    var unavailable = false;
    for (var rank : ranks) {
      available |= rank != UNAVAILABLE;
      unavailable |= rank != AVAILABLE;
    }
    return unavailable ? (available ? PARTIAL : UNAVAILABLE) : AVAILABLE;
  }

  private int memberRank(MemberDescriptor member, ExecutionContext context) {
    var rank = platformRank(member.metadata(), context);
    if (member.kind() == MemberKind.PROPERTY) {
      rank = Math.max(rank, combine(member.returnTypes().refs().stream()
        .mapToInt(type -> typeRank(type, context)).toArray()));
    }
    return rank;
  }

  /** Платформенная доступность сама по себе не доказывает наличие или отсутствие серверного перехода. */
  private int methodRank(MemberDescriptor member, ExecutionContext context, MethodHint hint) {
    if (member.getSourceSymbol().orElse(null) instanceof MethodSymbol method) {
      var targets = methodTargets.computeIfAbsent(method, symbol -> resolver.declarations(symbol.getOwner())
        .getOrDefault(symbol.getName(), List.of()).stream()
        .map(declaration -> target(symbol.getOwner(), declaration)).toList());
      return methodRank(targets, context, hint);
    }
    var available = member.metadata().availabilities();
    hint.include(available.isEmpty() ? Optional.empty()
      : Optional.of(platformContexts.computeIfAbsent(available, resolver::platformContexts)));
    var rank = platformRank(member.metadata(), context);
    hint.paths.add(rank == UNAVAILABLE ? CallPath.UNAVAILABLE : CallPath.UNKNOWN);
    return rank;
  }

  /** Профиль кешируется на директиву, пересечение с веткой — на объявление, без повторного вывода типов. */
  private MethodTarget target(DocumentContext owner, Declaration declaration) {
    var profile = profiles.computeIfAbsent(new ProfileKey(owner.getUri(), declaration.directive()),
      ignored -> resolver.methodContext(owner, declaration.directive()));
    var contexts = declaration.branch() == null ? Optional.<Set<ExecutionContext>>empty() : profile.map(value -> {
      var declared = ExecutionContext.copyOf(value.contexts());
      declared.retainAll(declaration.branch());
      return Set.copyOf(declared);
    });
    return new MethodTarget(owner, declaration, profile, contexts);
  }

  /** Найденные исходные цели проверяются теми же правилами перехода, что и диагностика. */
  private int methodRank(List<MethodTarget> targets, ExecutionContext context, MethodHint hint) {
    if (targets.isEmpty()) {
      hint.knownContexts = false;
      hint.paths.add(CallPath.UNKNOWN);
      return AVAILABLE;
    }
    var rank = UNAVAILABLE;
    for (var target : targets) {
      if (!target.owner().getUri().equals(document.getUri()) && !target.declaration().exported()) {
        continue;
      }
      hint.include(target.contexts());
      var availability = resolver.availability(document, scope.orElseThrow(), target.owner(), target.declaration(),
        target.profile(), context);
      if (availability == CallAvailability.UNKNOWN) {
        hint.paths.add(CallPath.UNKNOWN);
        rank = AVAILABLE;
      } else if (availability == CallAvailability.AVAILABLE) {
        var destination = resolver.targetContext(document, target.owner(), target.profile().orElseThrow(), context);
        hint.paths.add(CompilationContextResolver.isServerTransition(context, destination) ? CallPath.SERVER : CallPath.LOCAL);
        rank = AVAILABLE;
      }
    }
    if (rank == UNAVAILABLE) {
      hint.paths.add(CallPath.UNAVAILABLE);
    }
    return rank;
  }

  /** В списке остаётся только серверная стрелка; пояснения возвращаются для приписки под документацией. */
  private static String applyHint(CompletionItem item, MethodHint hint, Language language, boolean labelDetailsSupport) {
    var explanation = new ArrayList<String>();
    if (hint.knownContexts) {
      var names = hint.contexts.isEmpty() ? tr(language, "noContexts") : hint.contexts.stream()
        .filter(context -> !context.isFileMode() || !hint.contexts.contains(context.optionContext()))
        .map(context -> tr(language, "context." + context.name())).distinct().collect(Collectors.joining(", "));
      explanation.add(tr(language, "contexts").formatted(names));
    }
    var serverCall = "";
    if (hint.paths.contains(CallPath.SERVER)) {
      var conditional = hint.paths.size() > 1;
      serverCall = tr(language, conditional ? "possibleServerCall" : "serverCall");
      explanation.add(tr(language, conditional ? "possibleServerCall.detail" : "serverCall.detail"));
    } else if (hint.paths.equals(Set.of(CallPath.UNAVAILABLE))) {
      explanation.add(tr(language, "unavailableCall.detail"));
    } else if (hint.paths.equals(Set.of(CallPath.LOCAL))) {
      explanation.add(tr(language, "localCall.detail"));
    }
    if (!serverCall.isEmpty() && labelDetailsSupport) {
      var labels = item.getLabelDetails();
      if (labels == null) {
        labels = new CompletionItemLabelDetails();
        item.setLabelDetails(labels);
      }
      var type = labels.getDescription();
      labels.setDescription(type == null || type.isBlank() ? serverCall : serverCall + " · " + type);
    } else if (!serverCall.isEmpty()) {
      var detail = item.getDetail();
      item.setDetail(detail == null || detail.isBlank() ? serverCall : detail + " · " + serverCall);
    }
    return String.join("\n\n", explanation);
  }

  private static String tr(Language language, String key) {
    return Resources.getResourceString(language, CompletionContextRanker.class, key);
  }

  /** Видимость общего модуля учитывает серверный вызов; прочие типы используют сведения СП. */
  private int typeRank(TypeRef type, ExecutionContext context) {
    return typeContexts.computeIfAbsent(type, candidate -> resolver.typeContexts(document, candidate))
      .map(contexts -> contexts.contains(context) ? AVAILABLE : UNAVAILABLE).orElse(AVAILABLE);
  }

  private int platformRank(PlatformMetadata metadata, ExecutionContext context) {
    return metadata.availabilities().isEmpty()
      || platformContexts.computeIfAbsent(metadata.availabilities(), resolver::platformContexts).contains(context)
      ? AVAILABLE : UNAVAILABLE;
  }

  /** Имена обоих языков индексируются один раз на тип, локальные поля остаются в контекстном TypeSet. */
  private Map<String, List<MemberDescriptor>> indexMembers(TypeRef type) {
    Map<String, List<MemberDescriptor>> result = new HashMap<>();
    for (var member : types.getMembers(type, document.getFileType())) {
      for (var name : Stream.of(member.bilingualName().ru(), member.bilingualName().en())
          .filter(value -> !value.isEmpty()).distinct().toList()) {
        result.computeIfAbsent(key(member.kind(), name), ignored -> new ArrayList<>()).add(member);
      }
    }
    return result;
  }

  private static String key(MemberKind kind, String name) {
    return kind + ":" + name.toLowerCase(Locale.ROOT);
  }
}
