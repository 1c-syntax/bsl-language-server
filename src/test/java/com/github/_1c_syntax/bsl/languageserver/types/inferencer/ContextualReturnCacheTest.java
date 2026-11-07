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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Set;

class ContextualReturnCacheTest {
  @Test
  void depthCutResultsDoNotCrossGuardOrDepthBoundaries() {
    var cache = new ContextualReturnCache();
    var method = mock(MethodSymbol.class);
    var caller = mock(MethodSymbol.class);
    var key = new ContextualReturnKey(method, ExecutionContext.SERVER);
    var first = new ContextualReturnCache.Guard(key, Set.of(method, caller), Set.of(), 4);
    var otherStack = new ContextualReturnCache.Guard(key, Set.of(method), Set.of(), 4);
    var otherDepth = new ContextualReturnCache.Guard(key, Set.of(method, caller), Set.of(), 5);
    var result = new ContextualReturnCache.Result(TypeSet.of(TypeRef.UNDEFINED), Set.of(caller), Set.of(), Set.of(), Set.of(),
      true, false, true);

    cache.remember(first, result, cache.revision());

    assertThat(cache.get(first)).isSameAs(result);
    assertThat(cache.get(otherStack)).isNull();
    assertThat(cache.get(otherDepth)).isNull();
    assertThat(cache.get(key)).isNull();
    cache.invalidate();
    assertThat(cache.get(first)).isNull();
  }

  @Test
  void anUncutDepthBudgetDoesNotPreventReuseOfKnownResults() {
    var cache = new ContextualReturnCache();
    var method = mock(MethodSymbol.class);
    var caller = mock(MethodSymbol.class);
    var key = new ContextualReturnKey(method, ExecutionContext.SERVER);
    var first = new ContextualReturnCache.Guard(key, Set.of(method, caller), Set.of(), 4);
    var deeper = new ContextualReturnCache.Guard(key, Set.of(method, caller), Set.of(), 20);
    var result = new ContextualReturnCache.Result(TypeSet.of(TypeRef.UNDEFINED), Set.of(), Set.of(),
      Set.of(caller), Set.of(), true, false, false);
    cache.remember(first, result, cache.revision());

    assertThat(cache.get(deeper)).isSameAs(result);
  }

  @Test
  void unrelatedAncestorsDoNotPreventReuseButObservedGuardsMustMatch() {
    var cache = new ContextualReturnCache();
    var method = mock(MethodSymbol.class);
    var root = mock(MethodSymbol.class);
    var dependency = mock(MethodSymbol.class);
    var left = mock(MethodSymbol.class);
    var right = mock(MethodSymbol.class);
    var key = new ContextualReturnKey(method, ExecutionContext.SERVER);
    var original = new ContextualReturnCache.Guard(key, Set.of(method, root, left), Set.of(), 4);
    var otherPath = new ContextualReturnCache.Guard(key, Set.of(method, root, right), Set.of(), 4);
    var missingCut = new ContextualReturnCache.Guard(key, Set.of(method, right), Set.of(), 4);
    var newCut = new ContextualReturnCache.Guard(key, Set.of(method, root, dependency), Set.of(), 4);
    var result = new ContextualReturnCache.Result(TypeSet.of(TypeRef.UNDEFINED), Set.of(dependency), Set.of(),
      Set.of(root), Set.of(), true, false, false);

    cache.remember(original, result, cache.revision());

    assertThat(cache.get(otherPath)).isSameAs(result);
    assertThat(cache.get(missingCut)).isNull();
    assertThat(cache.get(newCut)).isNull();
  }

  @Test
  void partialResultsDoNotCrossFlowCacheEligibilityBoundary() {
    var cache = new ContextualReturnCache();
    var method = mock(MethodSymbol.class);
    var ancestor = mock(MethodSymbol.class);
    var key = new ContextualReturnKey(method, ExecutionContext.SERVER);
    var root = new ContextualReturnCache.Guard(key, Set.of(method), Set.of(), 4);
    var nested = new ContextualReturnCache.Guard(key, Set.of(method, ancestor), Set.of(), 4);
    var result = new ContextualReturnCache.Result(TypeSet.of(TypeRef.UNDEFINED), Set.of(), Set.of(),
      Set.of(method), Set.of(), true, false, false);
    cache.remember(root, result, cache.revision());

    assertThat(cache.get(root)).isSameAs(result);
    assertThat(cache.get(nested)).isNull();
  }

  @Test
  void unobservedActiveMethodsDoNotPreventReuse() {
    var cache = new ContextualReturnCache();
    var method = mock(MethodSymbol.class);
    var root = mock(MethodSymbol.class);
    var left = mock(MethodSymbol.class);
    var right = mock(MethodSymbol.class);
    var dependency = mock(MethodSymbol.class);
    var leftBody = mock(BSLParser.CodeBlockContext.class);
    var rightBody = mock(BSLParser.CodeBlockContext.class);
    var key = new ContextualReturnKey(method, ExecutionContext.SERVER);
    var original = new ContextualReturnCache.Guard(key, Set.of(method, root), Set.of(leftBody),
      Set.of(left), Set.of(), 4, false);
    var otherPath = new ContextualReturnCache.Guard(key, Set.of(method, root), Set.of(rightBody),
      Set.of(right), Set.of(), 4, false);
    var newCut = new ContextualReturnCache.Guard(key, Set.of(method, root), Set.of(rightBody),
      Set.of(dependency), Set.of(), 4, false);
    var result = new ContextualReturnCache.Result(TypeSet.of(TypeRef.UNDEFINED), Set.of(dependency), Set.of(),
      Set.of(root), Set.of(), true, false, false);
    cache.remember(original, result, cache.revision());

    assertThat(cache.get(otherPath)).isSameAs(result);
    assertThat(cache.get(newCut)).isNull();
  }

  @Test
  void directlyReadFlowBodiesMustKeepTheirActivity() {
    var cache = new ContextualReturnCache();
    var method = mock(MethodSymbol.class);
    var root = mock(MethodSymbol.class);
    var body = mock(BSLParser.CodeBlockContext.class);
    var key = new ContextualReturnKey(method, ExecutionContext.SERVER);
    var active = new ContextualReturnCache.Guard(key, Set.of(method, root), Set.of(body),
      Set.of(root), Set.of(), 4, false);
    var inactive = new ContextualReturnCache.Guard(key, Set.of(method, root), Set.of(),
      Set.of(), Set.of(), 4, false);
    var result = new ContextualReturnCache.Result(TypeSet.of(TypeRef.UNDEFINED), Set.of(), Set.of(),
      Set.of(), Set.of(body), false, true, false);
    cache.remember(active, result, cache.revision());

    assertThat(cache.get(active)).isSameAs(result);
    assertThat(cache.get(inactive)).isNull();
  }

  @Test
  void cellComputationStateRemainsPartOfTheGuard() {
    var cache = new ContextualReturnCache();
    var method = mock(MethodSymbol.class);
    var key = new ContextualReturnKey(method, ExecutionContext.SERVER);
    var settled = new ContextualReturnCache.Guard(key, Set.of(method), Set.of(), 4);
    var computing = new ContextualReturnCache.Guard(key, Set.of(method), Set.of(), Set.of(), Set.of(), 4, true);
    var result = new ContextualReturnCache.Result(TypeSet.of(TypeRef.UNDEFINED), Set.of(), Set.of(),
      Set.of(method), Set.of(), true, false, false);
    cache.remember(settled, result, cache.revision());

    assertThat(cache.get(settled)).isSameAs(result);
    assertThat(cache.get(computing)).isNull();
  }

  @Test
  void aDependencyChangeDiscardsBothStoredAndStillComputingResults() {
    var cache = new ContextualReturnCache();
    var method = mock(MethodSymbol.class);
    var server = new ContextualReturnKey(method, ExecutionContext.SERVER);
    var client = new ContextualReturnKey(method, ExecutionContext.THIN_CLIENT);
    var revision = cache.revision();
    var value = TypeSet.of(TypeRef.UNDEFINED);
    cache.remember(server, value, revision);
    assertThat(cache.get(server)).isSameAs(value);
    assertThat(cache.get(client)).isNull();

    cache.invalidate();
    cache.remember(client, value, revision);

    assertThat(cache.get(server)).isNull();
    assertThat(cache.get(client)).isNull();
    cache.remember(client, value, cache.revision());
    assertThat(cache.get(client)).isSameAs(value);
  }
}
