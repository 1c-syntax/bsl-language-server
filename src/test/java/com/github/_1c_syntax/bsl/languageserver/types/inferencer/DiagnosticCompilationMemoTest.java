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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

class DiagnosticCompilationMemoTest {
  private static final URI A = URI.create("file:///compilation-a");
  private static final URI B = URI.create("file:///compilation-b");

  @Test
  void outsideDiagnosticsEachRequestStillGetsItsOwnMetadata() {
    assertThat(DiagnosticDeclarationSession.current()).isNull();
    assertThat(memo(A, 7)).isNotSameAs(memo(A, 7));
  }

  @Test
  void oneTaskSharesOnlyCompilationMetadata() {
    try (var scope = new DiagnosticDeclarationSession().open()) {
      var document = mock(DocumentContext.class);
      var first = new InferenceContext(document, ExecutionContext.SERVER, memo(A, 7));
      var second = new InferenceContext(document, ExecutionContext.THIN_CLIENT, memo(A, 7));
      assertThat(first.compilation).isSameAs(second.compilation);
      assertThat(first.contextualReturns).isNotSameAs(second.contextualReturns);
      assertThat(first.flowSession).isNotSameAs(second.flowSession);
      first.sawMissing = true;
      assertThat(second.sawMissing).isFalse();
    }
    assertThat(DiagnosticDeclarationSession.current()).isNull();
  }

  @Test
  void changedInputRevisionReplacesTheMemoInsteadOfRevivingOldEntries() {
    try (var scope = new DiagnosticDeclarationSession().open()) {
      var initial = memo(A, 7);
      var changed = memo(A, 8);
      assertThat(changed).isNotSameAs(initial);
      assertThat(memo(A, 8)).isSameAs(changed);
      assertThat(memo(A, 7)).isNotSameAs(initial).isNotSameAs(changed);
    }
  }

  @Test
  void workspaceSwitchDoesNotReuseMetadataWithAnEqualRevisionNumber() {
    try (var scope = new DiagnosticDeclarationSession().open()) {
      var first = memo(A, 7);
      assertThat(memo(URI.create(A.toString()), 7)).isSameAs(first);
      assertThat(memo(B, 7)).isNotSameAs(first);
      assertThat(memo(A, 7)).isNotSameAs(first);
    }
  }

  @Test
  void nestedFailureRestoresTheOuterSessionAndItsMemo() {
    var session = new DiagnosticDeclarationSession();
    try (var outer = session.open()) {
      var previous = memo(A, 7);
      assertThatThrownBy(() -> {
        try (var inner = new DiagnosticDeclarationSession().open()) {
          assertThat(memo(A, 7)).isNotSameAs(previous);
          throw new IllegalArgumentException("nested");
        }
      }).isInstanceOf(IllegalArgumentException.class);
      assertThat(DiagnosticDeclarationSession.current()).isSameAs(session);
      assertThat(memo(A, 7)).isSameAs(previous);
    }
    assertThat(DiagnosticDeclarationSession.current()).isNull();
  }

  @Test
  void sharedDeclarationSessionKeepsCompilationMapsConfinedToEachWorker() throws Exception {
    var session = new DiagnosticDeclarationSession();
    var executor = Executors.newFixedThreadPool(2);
    var ready = new CountDownLatch(2);
    var release = new CountDownLatch(1);
    Callable<CompilationContextMemo> task = () -> {
      try (var scope = session.open()) {
        var result = memo(A, 7);
        ready.countDown();
        assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(memo(A, 7)).isSameAs(result);
        return result;
      }
    };
    try {
      var first = executor.submit(task);
      var second = executor.submit(task);
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      release.countDown();
      assertThat(first.get(5, TimeUnit.SECONDS)).isNotSameAs(second.get(5, TimeUnit.SECONDS));
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
    assertThat(DiagnosticDeclarationSession.current()).isNull();
  }

  private static CompilationContextMemo memo(URI workspace, long revision) {
    return DiagnosticDeclarationSession.compilationMemo(workspace, revision);
  }

  @Test
  void transferredContextsReuseTheSameMetadataSafely() throws Exception {
    var document = mock(DocumentContext.class);
    var resolver = mock(CompilationContextResolver.class);
    var first = Optional.of(new CompilationContextResolver.MethodContext(Optional.empty(), Set.of(ExecutionContext.SERVER)));
    var second = Optional.of(new CompilationContextResolver.MethodContext(Optional.empty(), Set.of(ExecutionContext.THIN_CLIENT)));
    when(resolver.methodContext(document, Optional.empty())).thenReturn(first, second);
    var executor = Executors.newSingleThreadExecutor();
    try (var scope = new DiagnosticDeclarationSession().open()) {
      var metadata = memo(A, 7);
      assertThat(metadata.methodContext(document, Optional.empty(), resolver)).isEqualTo(first);
      var other = executor.submit(() -> metadata.methodContext(document, Optional.empty(), resolver));
      assertThat(other.get(5, TimeUnit.SECONDS)).isEqualTo(first);
      assertThat(metadata.methodContext(document, Optional.empty(), resolver)).isEqualTo(first);
      verify(resolver, times(1)).methodContext(document, Optional.empty());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void concurrentMissesComputeOutsideTheCacheLock() throws Exception {
    var document = mock(DocumentContext.class);
    var resolver = mock(CompilationContextResolver.class);
    var entered = new CountDownLatch(2);
    var value = Optional.of(new CompilationContextResolver.MethodContext(Optional.empty(), Set.of(ExecutionContext.SERVER)));
    when(resolver.methodContext(document, Optional.empty())).thenAnswer(invocation -> {
      entered.countDown();
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      return value;
    });
    var executor = Executors.newFixedThreadPool(2);
    try (var scope = new DiagnosticDeclarationSession().open()) {
      var metadata = memo(A, 7);
      var first = executor.submit(() -> metadata.methodContext(document, Optional.empty(), resolver));
      var second = executor.submit(() -> metadata.methodContext(document, Optional.empty(), resolver));
      assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(value);
      assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(value);
      assertThat(metadata.methodContext(document, Optional.empty(), resolver)).isEqualTo(value);
      verify(resolver, times(2)).methodContext(document, Optional.empty());
    } finally {
      executor.shutdownNow();
    }
  }
}
