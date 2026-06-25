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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.symbol.PlatformMemberSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mockito-юнит для {@link DefinitionProvider}: переход к определению члена
 * платформенного/конфигурационного типа, разрешённого через
 * {@code TypeService.memberAt} в синтетический {@link PlatformMemberSymbol}.
 * <p>
 * Покрывает разворот ссылки на source-defined символ-источник дескриптора
 * без поднятия полного {@code ServerContext}.
 * Обсуждение: https://github.com/1c-syntax/bsl-language-server/pull/4197
 */
class DefinitionProviderUnitTest {

  private static final URI SOURCE_URI = URI.create("file:///lib/asserts/assertion.os");

  @Test
  void definitionFollowsSourceSymbolOfPlatformMember() {
    // given — член (метод OS-класса) с прикреплённым source-defined символом-источником
    var sourceRange = Ranges.create(10, 0, 40);
    var sourceSelectionRange = Ranges.create(10, 9, 17);
    var sourceSymbol = sourceDefinedMethod(sourceRange, sourceSelectionRange);

    var memberRange = Ranges.create(3, 24, 32);
    var reference = platformMemberReference(sourceSymbol, memberRange);

    var provider = providerReturning(reference, true);

    var params = new DefinitionParams();
    params.setPosition(new Position(3, 26));

    // when
    var definitions = provider.getDefinition(mock(DocumentContext.class), params);

    // then — переход ведёт к источнику, origin = диапазон обращения к члену
    assertThat(definitions.isRight()).isTrue();
    assertThat(definitions.getRight()).hasSize(1);

    var link = definitions.getRight().get(0);
    assertThat(link.getTargetUri()).isEqualTo(SOURCE_URI.toString());
    assertThat(link.getTargetRange()).isEqualTo(sourceRange);
    assertThat(link.getTargetSelectionRange()).isEqualTo(sourceSelectionRange);
    assertThat(link.getOriginSelectionRange()).isEqualTo(memberRange);
  }

  @Test
  void definitionEmptyForPlatformMemberWithoutSource() {
    // given — платформенный член без символа-источника (нет объявления в исходниках)
    var memberRange = Ranges.create(3, 24, 32);
    var member = new PlatformMemberSymbol("Добавить", null,
      MemberDescriptor.method("Добавить"), -1, List.of());
    var reference = new Reference(
      mock(MethodSymbol.class), member, SOURCE_URI, memberRange, OccurrenceType.REFERENCE);

    var provider = providerReturning(reference, true);

    var params = new DefinitionParams();
    params.setPosition(new Position(3, 26));

    // when
    var definitions = provider.getDefinition(mock(DocumentContext.class), params);

    // then — перехода нет (остаётся только hover)
    assertThat(definitions.isRight()).isTrue();
    assertThat(definitions.getRight()).isEmpty();
  }

  private static MethodSymbol sourceDefinedMethod(Range range, Range selectionRange) {
    var owner = mock(DocumentContext.class);
    when(owner.getUri()).thenReturn(SOURCE_URI);

    var sourceSymbol = mock(MethodSymbol.class);
    when(sourceSymbol.getOwner()).thenReturn(owner);
    when(sourceSymbol.getRange()).thenReturn(range);
    when(sourceSymbol.getSelectionRange()).thenReturn(selectionRange);
    return sourceSymbol;
  }

  private static Reference platformMemberReference(Symbol sourceSymbol, Range memberRange) {
    var descriptor = MemberDescriptor.method("ИмеетТип").withSourceSymbol(sourceSymbol);
    var member = new PlatformMemberSymbol("ИмеетТип", null, descriptor, 1, List.of());
    return new Reference(
      mock(MethodSymbol.class), member, SOURCE_URI, memberRange, OccurrenceType.REFERENCE);
  }

  private static DefinitionProvider providerReturning(Reference reference, boolean linkSupport) {
    var referenceResolver = mock(ReferenceResolver.class);
    when(referenceResolver.findReference(any(), any())).thenReturn(Optional.of(reference));

    var capabilitiesHolder = mock(ClientCapabilitiesHolder.class);
    var provider = new DefinitionProvider(referenceResolver, capabilitiesHolder);
    if (linkSupport) {
      var capabilities = new org.eclipse.lsp4j.ClientCapabilities();
      var textDocument = new org.eclipse.lsp4j.TextDocumentClientCapabilities();
      textDocument.setDefinition(new org.eclipse.lsp4j.DefinitionCapabilities(false, true));
      capabilities.setTextDocument(textDocument);
      when(capabilitiesHolder.getCapabilities()).thenReturn(Optional.of(capabilities));
      provider.handleInitializeEvent();
    }
    return provider;
  }
}
