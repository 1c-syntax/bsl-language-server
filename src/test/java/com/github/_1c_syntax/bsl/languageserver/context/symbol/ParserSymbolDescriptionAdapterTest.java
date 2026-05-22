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
package com.github._1c_syntax.bsl.languageserver.context.symbol;

import com.github._1c_syntax.bsl.parser.description.SourceDefinedSymbolDescription;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParserSymbolDescriptionAdapterTest {

  @Mock
  private SourceDefinedSymbolDescription delegate;

  @Test
  void ofNullReturnsEmptySingleton() {
    // when / then
    assertThat(ParserSymbolDescriptionAdapter.of(null)).isSameAs(SymbolDescription.EMPTY);
  }

  @Test
  void ofWrapsDelegateAndDelegatesAllAccessors() {
    // given
    when(delegate.getPurposeDescription()).thenReturn("назначение");
    when(delegate.isDeprecated()).thenReturn(true);
    when(delegate.getDeprecationInfo()).thenReturn("используйте X");

    // when
    var adapter = ParserSymbolDescriptionAdapter.of(delegate);

    // then
    assertThat(adapter.getPurposeDescription()).isEqualTo("назначение");
    assertThat(adapter.isDeprecated()).isTrue();
    assertThat(adapter.getDeprecationInfo()).isEqualTo("используйте X");
  }

  @Test
  void wrappedDelegateReturnsFalseDeprecatedFlagAsIs() {
    // given
    when(delegate.isDeprecated()).thenReturn(false);

    // when
    var adapter = ParserSymbolDescriptionAdapter.of(delegate);

    // then
    assertThat(adapter.isDeprecated()).isFalse();
  }
}
