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
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CallAvailability;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SharedReceiverRankingTest {
  private record Fixture(DocumentContext caller, Position position, CompilationContextResolver resolver,
                         TypeService types, MemberDescriptor first, MemberDescriptor second) {
    CompletionContextEvaluation evaluation() {
      return new CompletionContextEvaluation(caller, position, resolver, types);
    }
  }

  @Test
  void sharedEvaluationMatchesTheOriginalPathAndDoesNotShareMutableHints() {
    var fixture = fixture(true, false);
    var evaluation = fixture.evaluation();
    var original = new CompletionMethodHint();
    var shared = new CompletionMethodHint();
    assertThat(evaluation.rankDotSourceMember("First", fixture.first(), original)).isZero();
    assertThat(evaluation.rankDotSourceMember("Second", fixture.second(), shared)).isZero();
    var renderer = new CompletionMethodHintRenderer(Language.RU, true);
    var expected = renderer.apply(original, new CompletionItem("method"));
    assertThat(renderer.apply(shared, new CompletionItem("method"))).isEqualTo(expected);
    shared.unknownReceiver();
    var third = new CompletionMethodHint();
    evaluation.rankDotSourceMember("Second", fixture.second(), third);
    assertThat(renderer.apply(third, new CompletionItem("method"))).isEqualTo(expected);
    verify(fixture.resolver(), times(2)).availability(any(), any(), any(), any(), any(), any());
    verify(fixture.types(), times(2)).receiverTypesAt(any(), any(), any());
  }

  @Test
  void anUnknownReceiverInAnotherModeKeepsConservativeHints() {
    var fixture = fixture(true, true);
    var evaluation = fixture.evaluation();
    for (var member : List.of(fixture.first(), fixture.second())) {
      var hint = new CompletionMethodHint();
      assertThat(evaluation.rankDotSourceMember(member.name(), member, hint)).isZero();
      assertThat(new CompletionMethodHintRenderer(Language.RU, true)
        .apply(hint, new CompletionItem("method"))).isEmpty();
    }
    verify(fixture.resolver(), times(1)).availability(any(), any(), any(), any(), any(), any());
  }

  @Test
  void privateMethodsOfAnotherDocumentStayUnavailable() {
    var fixture = fixture(false, false);
    var evaluation = fixture.evaluation();
    for (var member : List.of(fixture.first(), fixture.second())) {
      assertThat(evaluation.rankDotSourceMember(member.name(), member, new CompletionMethodHint()))
        .isEqualTo(CompletionPriority.UNAVAILABLE);
    }
    verify(fixture.resolver(), never()).availability(any(), any(), any(), any(), any(), any());
  }

  @Test
  void anotherRequestReadsCurrentCallAvailability() {
    var fixture = fixture(true, false);
    var first = new CompletionMethodHint();
    fixture.evaluation().rankDotSourceMember("First", fixture.first(), first);
    when(fixture.resolver().availability(any(), any(), any(), any(), any(), any()))
      .thenReturn(CallAvailability.UNKNOWN);
    var second = new CompletionMethodHint();
    fixture.evaluation().rankDotSourceMember("First", fixture.first(), second);
    var renderer = new CompletionMethodHintRenderer(Language.EN, true);
    var oldItem = new CompletionItem("method");
    var newItem = new CompletionItem("method");
    assertThat(renderer.apply(second, newItem)).isNotEqualTo(renderer.apply(first, oldItem));
    assertThat(oldItem.getLabelDetails()).isNotNull();
    assertThat(newItem.getLabelDetails()).isNull();
  }

  private static Fixture fixture(boolean exported, boolean unknownInClient) {
    var caller = mock(DocumentContext.class);
    var callee = mock(DocumentContext.class);
    when(caller.getUri()).thenReturn(URI.create("file:///caller.bsl"));
    when(callee.getUri()).thenReturn(URI.create("file:///callee.bsl"));
    when(caller.getFileType()).thenReturn(FileType.BSL);
    var position = new Position();
    var modes = Set.of(ExecutionContext.SERVER, ExecutionContext.THIN_CLIENT);
    var profile = new CompilationContextResolver.MethodContext(Optional.empty(), modes);
    var declaration = new CompilationContextResolver.Declaration(Optional.empty(), modes, exported);
    var resolver = mock(CompilationContextResolver.class);
    when(resolver.contextAt(caller, position)).thenReturn(Optional.of(profile));
    when(resolver.automaticContexts(caller)).thenReturn(modes);
    when(resolver.methodContext(callee, Optional.empty())).thenReturn(Optional.of(profile));
    when(resolver.declarations(callee)).thenReturn(Map.of("First", List.of(declaration),
      "Second", List.of(new CompilationContextResolver.Declaration(Optional.empty(), Set.copyOf(modes), exported))));
    when(resolver.typeContexts(caller, TypeRef.UNDEFINED)).thenReturn(Optional.of(modes));
    when(resolver.availability(any(), any(), any(), any(), any(), any())).thenReturn(CallAvailability.AVAILABLE);
    when(resolver.targetContext(any(), any(), any(), any())).thenReturn(ExecutionContext.SERVER);
    var first = member(callee, "First");
    var second = member(callee, "Second");
    var types = mock(TypeService.class);
    when(types.receiverTypesQuery(caller, position)).thenReturn(mode -> types.receiverTypesAt(caller, position, mode));
    when(types.receiverTypesAt(caller, position, ExecutionContext.SERVER)).thenReturn(TypeSet.of(TypeRef.UNDEFINED));
    when(types.receiverTypesAt(caller, position, ExecutionContext.THIN_CLIENT))
      .thenReturn(TypeSet.of(unknownInClient ? TypeRef.UNKNOWN : TypeRef.UNDEFINED));
    when(types.getMembers(TypeRef.UNDEFINED, FileType.BSL)).thenReturn(List.of(first, second));
    return new Fixture(caller, position, resolver, types, first, second);
  }

  private static MemberDescriptor member(DocumentContext owner, String name) {
    var method = mock(MethodSymbol.class);
    when(method.getOwner()).thenReturn(owner);
    when(method.getName()).thenReturn(name);
    return new MemberDescriptor(BilingualString.of(name), MemberKind.METHOD, BilingualString.EMPTY,
      TypeSet.EMPTY, List.of(), method, false, PlatformMetadata.EMPTY);
  }
}
