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
package com.github._1c_syntax.bsl.languageserver.types.references;

import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceFinder;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.symbol.PlatformMemberSymbol;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Position;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Optional;

/**
 * Finder для членов платформенных/конфигурационных типов и глобальных
 * функций/свойств, разрешаемых через {@link TypeService#memberAt}.
 *
 * <p>Покрывает кейсы, у которых нет соответствующего
 * {@link com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol}
 * в дереве символов: цепочки accessor'ов (например, {@code A.B.C.}),
 * platform-методы ({@code СтрЗаменить}), члены коллекций и т.п.
 *
 * <p>Возвращает {@link Reference} с {@link PlatformMemberSymbol} —
 * специализированным symbol'ом, который несёт всю информацию для рендеринга
 * hover/completion/signature-help без обращения в другие сервисы из consumer'ов.
 */
@Component
@Order(200)
@RequiredArgsConstructor
public class PlatformMemberReferenceFinder implements ReferenceFinder {

  private final ServerContextProvider serverContextProvider;
  private final TypeService typeService;

  @Override
  public Optional<Reference> findReference(URI uri, Position position) {
    // Горячий путь inferencer/hover — без захвата per-document RWLock.
    return serverContextProvider.getDocumentUnsafeNoLock(uri)
      .flatMap(document -> typeService.memberAt(document, position)
        .map(member -> new Reference(
          document.getSymbolTree().getModule(),
          new PlatformMemberSymbol(
            member.descriptor().name(),
            member.owner(),
            member.descriptor(),
            member.callArgCount(),
            member.argTypes()
          ),
          uri,
          member.range(),
          OccurrenceType.REFERENCE
        ))
      );
  }
}
