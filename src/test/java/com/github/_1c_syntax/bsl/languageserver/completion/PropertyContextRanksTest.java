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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.types.model.*;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static com.github._1c_syntax.bsl.languageserver.completion.CompletionPriority.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PropertyContextRanksTest {
  private static final TypeRef SERVER_TYPE = new TypeRef(TypeKind.PRIMITIVE, "ServerOnly");
  private static final TypeRef CLIENT_TYPE = new TypeRef(TypeKind.PRIMITIVE, "ClientOnly");
  private static final TypeRef ABSENT_TYPE = new TypeRef(TypeKind.PRIMITIVE, "NoContexts");
  private static final TypeRef EVERYWHERE_TYPE = new TypeRef(TypeKind.PRIMITIVE, "Everywhere");

  @Test
  void everyContextMatchesTheOriginalPerContextUnionRulesInBothQueryOrders() {
    var document = mock(DocumentContext.class);
    var resolver = mock(CompilationContextResolver.class);
    Map<TypeRef, Optional<Set<ExecutionContext>>> profiles = Map.of(
      SERVER_TYPE, Optional.of(Set.of(ExecutionContext.SERVER)),
      CLIENT_TYPE, Optional.of(Set.of(ExecutionContext.THIN_CLIENT, ExecutionContext.WEB_CLIENT)),
      ABSENT_TYPE, Optional.<Set<ExecutionContext>>of(Set.of()),
      EVERYWHERE_TYPE, Optional.of(ExecutionContext.ALL),
      TypeRef.UNKNOWN, Optional.<Set<ExecutionContext>>empty(),
      TypeRef.ANY, Optional.<Set<ExecutionContext>>empty());
    when(resolver.typeContexts(same(document), any())).thenAnswer(call -> profiles.get(call.getArgument(1)));
    when(resolver.platformContexts(any())).thenAnswer(call -> platformContexts(call.getArgument(0)));
    var returns = List.of(TypeSet.EMPTY, TypeSet.of(SERVER_TYPE), TypeSet.of(CLIENT_TYPE), TypeSet.of(ABSENT_TYPE),
      TypeSet.of(EVERYWHERE_TYPE), TypeSet.of(TypeRef.UNKNOWN), TypeSet.of(TypeRef.ANY),
      TypeSet.of(SERVER_TYPE, CLIENT_TYPE), TypeSet.of(SERVER_TYPE, TypeRef.UNKNOWN),
      TypeSet.of(ABSENT_TYPE, TypeRef.UNKNOWN), TypeSet.of(CLIENT_TYPE, ABSENT_TYPE),
      TypeSet.of(SERVER_TYPE, CLIENT_TYPE, ABSENT_TYPE), TypeSet.of(ABSENT_TYPE, EVERYWHERE_TYPE));
    var platforms = List.of(Set.<Availability>of(), Set.of(Availability.SERVER), Set.of(Availability.THIN_CLIENT),
      Set.of(Availability.WEB_CLIENT), Set.of(Availability.SERVER, Availability.WEB_CLIENT));
    var forward = new CompletionTypeContext(document, new Position(), resolver, mock(TypeService.class));
    var reverse = new CompletionTypeContext(document, new Position(), resolver, mock(TypeService.class));
    var modes = new ArrayList<>(ExecutionContext.ALL);
    var reversed = new ArrayList<>(modes); Collections.reverse(reversed);
    for (var returnTypes : returns) for (var platform : platforms) {
      var member = property(returnTypes, platform);
      for (var context : modes) {
        assertThat(forward.memberRank(member, context)).as("forward %s %s %s", returnTypes, platform, context)
          .isEqualTo(originalRank(member, context, profiles));
      }
      for (var context : reversed) {
        assertThat(reverse.memberRank(member, context)).as("reverse %s %s %s", returnTypes, platform, context)
          .isEqualTo(originalRank(member, context, profiles));
      }
    }
  }

  @Test
  void aNewQueryReadsChangedAvailabilityAndKeepsInputSetsUntouched() {
    var document = mock(DocumentContext.class);
    var resolver = mock(CompilationContextResolver.class);
    var initial = EnumSet.of(ExecutionContext.SERVER);
    var current = new AtomicReference<Set<ExecutionContext>>(initial);
    when(resolver.typeContexts(document, SERVER_TYPE)).thenAnswer(call -> Optional.of(current.get()));
    var member = property(TypeSet.of(SERVER_TYPE), Set.of());
    var first = new CompletionTypeContext(document, new Position(), resolver, mock(TypeService.class));
    assertThat(first.memberRank(member, ExecutionContext.SERVER)).isEqualTo(AVAILABLE);
    current.set(Set.of(ExecutionContext.THIN_CLIENT));
    assertThat(first.memberRank(member, ExecutionContext.THIN_CLIENT)).isEqualTo(UNAVAILABLE);
    var second = new CompletionTypeContext(document, new Position(), resolver, mock(TypeService.class));
    assertThat(second.memberRank(member, ExecutionContext.THIN_CLIENT)).isEqualTo(AVAILABLE);
    assertThat(second.memberRank(member, ExecutionContext.SERVER)).isEqualTo(UNAVAILABLE);
    assertThat(initial).containsExactly(ExecutionContext.SERVER);
  }

  @Test
  void methodsStillUseOnlyTheirOwnPlatformAvailability() {
    var document = mock(DocumentContext.class);
    var resolver = mock(CompilationContextResolver.class);
    var query = new CompletionTypeContext(document, new Position(), resolver, mock(TypeService.class));
    var method = new MemberDescriptor(BilingualString.of("Method"), MemberKind.METHOD, BilingualString.EMPTY,
      TypeSet.of(ABSENT_TYPE), List.of(), null, false, PlatformMetadata.EMPTY);
    for (var context : ExecutionContext.ALL) assertThat(query.memberRank(method, context)).isEqualTo(AVAILABLE);
    verifyNoInteractions(resolver);
  }

  /** Исходный алгоритм до группировки; эталон намеренно перебирает альтернативы в каждом режиме. */
  private static int originalRank(MemberDescriptor member, ExecutionContext context,
                                  Map<TypeRef, Optional<Set<ExecutionContext>>> profiles) {
    var platform = member.metadata().availabilities();
    var rank = platform.isEmpty() || platformContexts(platform).contains(context) ? AVAILABLE : UNAVAILABLE;
    var available = false;
    var unavailable = false;
    for (var type : member.returnTypes().refs()) {
      var known = profiles.get(type);
      var value = known.isEmpty() || known.orElseThrow().contains(context) ? AVAILABLE : UNAVAILABLE;
      available |= value != UNAVAILABLE;
      unavailable |= value != AVAILABLE;
    }
    return Math.max(rank, unavailable ? (available ? PARTIAL : UNAVAILABLE) : AVAILABLE);
  }

  private static Set<ExecutionContext> platformContexts(Set<Availability> available) {
    var contexts = EnumSet.noneOf(ExecutionContext.class);
    if (available.contains(Availability.SERVER)) contexts.add(ExecutionContext.SERVER);
    if (available.contains(Availability.THIN_CLIENT)) contexts.add(ExecutionContext.THIN_CLIENT);
    if (available.contains(Availability.WEB_CLIENT)) contexts.add(ExecutionContext.WEB_CLIENT);
    return contexts;
  }

  private static MemberDescriptor property(TypeSet returns, Set<Availability> available) {
    var metadata = new PlatformMetadata("", "", List.of(), available, null,
      BilingualString.EMPTY, BilingualString.EMPTY, List.of(), List.of());
    return new MemberDescriptor(BilingualString.of("Property"), MemberKind.PROPERTY, BilingualString.EMPTY,
      returns, List.of(), null, false, metadata);
  }
}
