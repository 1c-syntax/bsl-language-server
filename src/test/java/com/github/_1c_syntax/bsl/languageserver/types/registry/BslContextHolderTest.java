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

import com.github._1c_syntax.bsl.context.api.ContextProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BslContextHolderTest {

  @Mock
  private PlatformContextProviderFactory factory;
  @Mock
  private ContextProvider provider;

  @Test
  void getCachesProviderAndCallsFactoryOnce() throws IOException {
    // given
    when(factory.create()).thenReturn(Optional.of(provider));
    var holder = new BslContextHolder(factory);

    // when
    var first = holder.get();
    var second = holder.get();

    // then
    assertThat(first).contains(provider);
    assertThat(second).contains(provider);
    verify(factory, times(1)).create();
  }

  @Test
  void getReturnsEmptyWhenFactoryFailsWithIoException() throws IOException {
    // given
    when(factory.create()).thenThrow(new IOException("hbk missing"));
    var holder = new BslContextHolder(factory);

    // when
    var result = holder.get();

    // then — load() ловит exception и кэширует empty
    assertThat(result).isEmpty();
    // повторный вызов не должен ретраить
    assertThat(holder.get()).isEmpty();
    verify(factory, times(1)).create();
  }

  @Test
  void getReturnsEmptyWhenFactoryReturnsEmpty() throws IOException {
    // given
    when(factory.create()).thenReturn(Optional.empty());
    var holder = new BslContextHolder(factory);

    // when / then
    assertThat(holder.get()).isEmpty();
  }

  @Test
  void getReturnsEmptyOnUncheckedException() throws IOException {
    // given
    when(factory.create()).thenThrow(new RuntimeException("unexpected"));
    var holder = new BslContextHolder(factory);

    // when
    var result = holder.get();

    // then
    assertThat(result).isEmpty();
  }
}
