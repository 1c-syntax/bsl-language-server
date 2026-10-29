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
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.CompilerDirectiveKind;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver.MethodContext;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.Availability;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.TypeService.TypedMember;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Position;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


/** Мемоизация профилей, уточнённых целей и доступности только на время проверки одного файла. */
final class MemberAccessContexts {
  private final DocumentContext documentContext;
  private final CompilationContextResolver contextResolver;
  private final TypeService typeService;
  private final Map<ProfileKey, Optional<MethodContext>> profiles = new HashMap<>();
  private final Map<ContextualAccess, List<TypedMember>> contextualTargets = new HashMap<>();
  private final Map<TypeRef, Optional<Set<ExecutionContext>>> typeContexts = new HashMap<>();
  @SuppressWarnings("java:S6411") // Ключ — неизменяемый ограниченный набор флагов из СП, без отношения порядка.
  private final Map<Set<Availability>, Set<ExecutionContext>> platformContexts = new HashMap<>();

  MemberAccessContexts(DocumentContext document, CompilationContextResolver resolver, TypeService typeService) {
    documentContext = document;
    contextResolver = resolver;
    this.typeService = typeService;
  }

  /** Ключ профиля имеет тот же порядок, что URI и необязательная директива. */
  private record ProfileKey(URI uri, Optional<CompilerDirectiveKind> directive) implements Comparable<ProfileKey> {
    @Override
    public int compareTo(ProfileKey other) {
      var byUri = uri.compareTo(other.uri);
      return byUri != 0 ? byUri : Integer.compare(directiveOrdinal(), other.directiveOrdinal());
    }

    private int directiveOrdinal() {
      return directive.isPresent() ? directive.orElseThrow().ordinal() : -1;
    }
  }

  /** Полный ключ уточнения: координаты LSP и выбранный режим. */
  private record ContextualAccess(Position position, ExecutionContext context) implements Comparable<ContextualAccess> {
    @Override
    public int compareTo(ContextualAccess other) {
      var byLine = Integer.compare(position.getLine(), other.position.getLine());
      if (byLine != 0) {
        return byLine;
      }
      var byCharacter = Integer.compare(position.getCharacter(), other.position.getCharacter());
      return byCharacter != 0 ? byCharacter : context.compareTo(other.context);
    }
  }

  /** Неизвестные данные СП не доказывают запрет; известное ограничение владельца или члена доказывает. */
  boolean platformUnavailable(TypedMember member, ExecutionContext context) {
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

  /** Один контекстный вывод на обращение используется для исходных и платформенных целей. */
  List<TypedMember> targetsInContext(MemberAccessSite access, ExecutionContext context) {
    return contextualTargets.computeIfAbsent(new ContextualAccess(startOf(access.node()), context),
      ignored -> typeService.accessTargetsAt(documentContext, access.node(), context));
  }

  /** Повторно использовать профиль документа и директивы только в пределах проверки текущего файла. */
  @SuppressWarnings("java:S3553") // Пустой Optional сохраняет контракт отсутствующей директивы.
  Optional<MethodContext> methodContext(DocumentContext document, Optional<CompilerDirectiveKind> directive) {
    return profiles.computeIfAbsent(new ProfileKey(document.getUri(), directive),
      ignored -> contextResolver.methodContext(document, directive));
  }

  private static Position startOf(TerminalNode node) {
    return Ranges.create(node).getStart();
  }
}
