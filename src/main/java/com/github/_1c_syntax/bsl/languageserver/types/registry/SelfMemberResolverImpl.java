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
package com.github._1c_syntax.bsl.languageserver.types.registry;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.references.SelfMemberResolver;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.symbol.PlatformMemberSymbol;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Реализация {@link SelfMemberResolver} для слоя {@code references}: по
 * self-типу модуля документа ({@link GlobalScopeProvider#moduleTypeRefByUri}) и имени
 * собирает {@link PlatformMemberSymbol}.
 * <p>
 * Вынесена в отдельный бин на {@code GlobalScopeProvider}+{@code TypeRegistry} (без
 * {@code TypeService}), чтобы {@code ReferenceIndex} не образовал цикл
 * {@code ReferenceIndex→TypeService→ReferenceResolver→…→ReferenceIndex}.
 * <p>
 * Self-тип берётся ТОЛЬКО из кэша {@code moduleTypeRefByUri} — в отличие от
 * {@code TypeService.findSelfMember}, у которого при промахе кэша есть fallback на
 * метаданные. Здесь fallback не нужен: резолвер зовётся лишь на реконструкции ссылки
 * ({@code ReferenceIndex.buildReference}) — уже после наполнения кэша. Если self-типа
 * всё же нет (типы ещё не зарегистрированы), вернётся empty, а корректный результат
 * восстановит переиндексация на {@code ConfigurationTypesRegisteredEvent}.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class SelfMemberResolverImpl implements SelfMemberResolver {

  private final GlobalScopeProvider globalScopeProvider;
  private final TypeRegistry typeRegistry;

  @Override
  public Optional<Symbol> resolveSelfMember(DocumentContext documentContext, SymbolKind symbolKind, String name) {
    var kind = symbolKind == SymbolKind.Method ? MemberKind.METHOD : MemberKind.PROPERTY;
    return globalScopeProvider.moduleTypeRefByUri(documentContext.getUri())
      .flatMap(ref -> typeRegistry.findMember(ref, kind, name, documentContext.getFileType())
        .map(descriptor -> new PlatformMemberSymbol(descriptor.name(), ref, descriptor, -1, List.of())));
  }
}
