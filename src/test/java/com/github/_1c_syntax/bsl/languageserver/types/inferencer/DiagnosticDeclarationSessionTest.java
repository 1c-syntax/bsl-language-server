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

import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DiagnosticDeclarationSessionTest {
  @Test
  void aCompletedEmptyDeclarationIsDifferentFromAMiss() {
    var session = new DiagnosticDeclarationSession();
    var variable = mock(VariableSymbol.class);
    assertThat(session.get(variable, 1)).isNull();
    session.remember(variable, 1, TypeSet.EMPTY);
    assertThat(session.get(variable, 1)).isSameAs(TypeSet.EMPTY);
  }

  @Test
  void changedInputRevisionDoesNotReturnAnEarlierDeclaration() {
    var session = new DiagnosticDeclarationSession();
    var variable = mock(VariableSymbol.class);
    session.remember(variable, 1, TypeSet.EMPTY);
    assertThat(session.get(variable, 2)).isNull();
  }

  @Test
  void differentSymbolsDoNotShareDeclarations() {
    var session = new DiagnosticDeclarationSession();
    var first = mock(VariableSymbol.class);
    var second = mock(VariableSymbol.class);
    session.remember(first, 1, TypeSet.EMPTY);
    assertThat(session.get(second, 1)).isNull();
  }

  @Test
  void nestedScopesRestoreThePreviousSession() {
    var outer = new DiagnosticDeclarationSession();
    var inner = new DiagnosticDeclarationSession();
    assertThat(DiagnosticDeclarationSession.current()).isNull();
    try (var first = outer.open()) {
      assertThat(DiagnosticDeclarationSession.current()).isSameAs(outer);
      try (var second = inner.open()) {
        assertThat(DiagnosticDeclarationSession.current()).isSameAs(inner);
      }
      assertThat(DiagnosticDeclarationSession.current()).isSameAs(outer);
    }
    assertThat(DiagnosticDeclarationSession.current()).isNull();
  }

  @Test
  void aFailedTaskClearsItsSession() {
    var session = new DiagnosticDeclarationSession();
    assertThatThrownBy(() -> {
      try (var scope = session.open()) {
        throw new IllegalStateException("failed diagnostic");
      }
    }).isInstanceOf(IllegalStateException.class);
    assertThat(DiagnosticDeclarationSession.current()).isNull();
  }

  @Test
  void separateBatchesDoNotShareDeclarations() {
    var first = new DiagnosticDeclarationSession();
    var second = new DiagnosticDeclarationSession();
    var variable = mock(VariableSymbol.class);
    first.remember(variable, 1, TypeSet.EMPTY);
    assertThat(second.get(variable, 1)).isNull();
  }

  @Test
  void workersShareCompletedValuesAndClearTheirThreadState() throws Exception {
    var session = new DiagnosticDeclarationSession();
    var variable = mock(VariableSymbol.class);
    var ready = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().factory());
    try {
      var writer = executor.submit(() -> {
        assertThat(DiagnosticDeclarationSession.current()).isNull();
        try (var scope = session.open()) {
          session.remember(variable, 1, TypeSet.EMPTY);
          ready.countDown();
        }
        assertThat(DiagnosticDeclarationSession.current()).isNull();
      });
      var reader = executor.submit(() -> {
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(DiagnosticDeclarationSession.current()).isNull();
        try (var scope = session.open()) {
          assertThat(DiagnosticDeclarationSession.current().get(variable, 1)).isSameAs(TypeSet.EMPTY);
        }
        assertThat(DiagnosticDeclarationSession.current()).isNull();
        return null;
      });
      writer.get(5, TimeUnit.SECONDS);
      reader.get(5, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }
  }
}
