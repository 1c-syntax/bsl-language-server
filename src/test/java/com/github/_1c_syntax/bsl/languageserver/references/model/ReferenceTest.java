/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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
package com.github._1c_syntax.bsl.languageserver.references.model;

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.FAKE_DOCUMENT_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReferenceTest {

  @Test
  void testSourceDefinedSymbol() {
    // given
    var methodSymbol = mock(MethodSymbol.class);
    var moduleSymbol = mock(ModuleSymbol.class);
    var selectionRange = mock(Range.class);

    when(methodSymbol.getSelectionRange()).thenReturn(selectionRange);

    // when
    var reference = new Reference(
      moduleSymbol,
      methodSymbol,
      FAKE_DOCUMENT_URI,
      selectionRange,
      OccurrenceType.REFERENCE
    );

    // then
    assertThat(reference.getSourceDefinedSymbol()).hasValue(methodSymbol);
    assertThat(reference.isSourceDefinedSymbolReference()).isTrue();
  }

  @Test
  void toLocation() {
    // given
    var methodSymbol = mock(MethodSymbol.class);
    var moduleSymbol = mock(ModuleSymbol.class);
    var selectionRange = mock(Range.class);

    when(methodSymbol.getSelectionRange()).thenReturn(selectionRange);

    var reference = new Reference(
      moduleSymbol,
      methodSymbol,
      FAKE_DOCUMENT_URI,
      selectionRange,
      OccurrenceType.REFERENCE
    );

    // when
    Location location = reference.toLocation();

    // then
    assertThat(location.getUri()).isEqualTo(FAKE_DOCUMENT_URI.toString());
    assertThat(location.getRange()).isEqualTo(methodSymbol.getSelectionRange());
  }

  @Test
  void testOf() {
    // given
    var methodSymbol = mock(MethodSymbol.class);
    var moduleSymbol = mock(ModuleSymbol.class);
    var selectionRange = mock(Range.class);

    when(methodSymbol.getSelectionRange()).thenReturn(selectionRange);

    var reference = new Reference(
      moduleSymbol,
      methodSymbol,
      FAKE_DOCUMENT_URI,
      selectionRange,
      OccurrenceType.REFERENCE
    );
    var location = reference.toLocation();

    // when
    var newReference = Reference.of(moduleSymbol, methodSymbol, location);

    // then
    assertThat(newReference).isEqualTo(reference);
  }
}
