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

import com.github._1c_syntax.bsl.context.PlatformContextGrabber;
import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.platform.V8PlatformOptions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Проверяет логику включения/отключения загрузки платформенного контекста
 * и корректную работу happy-path с замоканным {@link PlatformContextGrabber}.
 * <p>
 * Тесты не зависят от установленной 1С — статические фабрики
 * {@code PlatformContextGrabber.autoDetect/fromPlatformBin} замокированы через
 * {@code Mockito.mockStatic}, поэтому ветка автодетекта реально вызывается
 * на CI без падений.
 */
class PlatformContextProviderFactoryTest {

  private static final String ENABLED_FIELD = "platformContextEnabled";

  @Test
  void create_returnsEmpty_whenPropertyDisabled_withoutTouchingConfiguration() throws IOException {
    var configuration = mock(LanguageServerConfiguration.class);
    var factory = new PlatformContextProviderFactory(configuration);
    ReflectionTestUtils.setField(factory, ENABLED_FIELD, false);

    Optional<ContextProvider> result = factory.create();

    assertThat(result).isEmpty();
    verify(configuration, never()).getPlatformOptions();
  }

  @Test
  void create_returnsEmpty_whenPropertyEnabledButPlatformOptionsDisabled() throws IOException {
    var configuration = mock(LanguageServerConfiguration.class);
    var options = mock(V8PlatformOptions.class);
    when(configuration.getPlatformOptions()).thenReturn(options);
    when(options.isEnabled()).thenReturn(false);

    try (MockedStatic<PlatformContextGrabber> grabbers = mockStatic(PlatformContextGrabber.class)) {
      var factory = new PlatformContextProviderFactory(configuration);
      ReflectionTestUtils.setField(factory, ENABLED_FIELD, true);

      Optional<ContextProvider> result = factory.create();

      assertThat(result).isEmpty();
      verify(options, times(1)).isEnabled();
      verify(options, never()).getBinPath();
      grabbers.verifyNoInteractions();
    }
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void create_runsAutoDetect_whenBinPathNotConfigured() throws IOException {
    var configuration = mock(LanguageServerConfiguration.class);
    var options = mock(V8PlatformOptions.class);
    when(configuration.getPlatformOptions()).thenReturn(options);
    when(options.isEnabled()).thenReturn(true);
    when(options.getBinPath()).thenReturn(null);

    var grabber = mock(PlatformContextGrabber.class);
    var provider = mock(ContextProvider.class);
    when(grabber.getProvider()).thenReturn(provider);
    when(provider.getContexts()).thenReturn((List) Collections.<Context>emptyList());

    try (MockedStatic<PlatformContextGrabber> grabbers = mockStatic(PlatformContextGrabber.class)) {
      grabbers.when(PlatformContextGrabber::autoDetect).thenReturn(grabber);

      var factory = new PlatformContextProviderFactory(configuration);
      ReflectionTestUtils.setField(factory, ENABLED_FIELD, true);

      Optional<ContextProvider> result = factory.create();

      assertThat(result).contains(provider);
      grabbers.verify(PlatformContextGrabber::autoDetect, times(1));
      verify(grabber, times(1)).parse();
      verify(grabber, times(1)).getProvider();
    }
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void create_runsFromPlatformBin_whenBinPathConfigured() throws IOException {
    var configuration = mock(LanguageServerConfiguration.class);
    var options = mock(V8PlatformOptions.class);
    Path binPath = Paths.get("/opt/1cv8/bin");
    when(configuration.getPlatformOptions()).thenReturn(options);
    when(options.isEnabled()).thenReturn(true);
    when(options.getBinPath()).thenReturn(binPath);

    var grabber = mock(PlatformContextGrabber.class);
    var provider = mock(ContextProvider.class);
    when(grabber.getProvider()).thenReturn(provider);
    when(provider.getContexts()).thenReturn((List) Collections.<Context>emptyList());

    try (MockedStatic<PlatformContextGrabber> grabbers = mockStatic(PlatformContextGrabber.class)) {
      grabbers.when(() -> PlatformContextGrabber.fromPlatformBin(eq(binPath))).thenReturn(grabber);

      var factory = new PlatformContextProviderFactory(configuration);
      ReflectionTestUtils.setField(factory, ENABLED_FIELD, true);

      Optional<ContextProvider> result = factory.create();

      assertThat(result).contains(provider);
      grabbers.verify(() -> PlatformContextGrabber.fromPlatformBin(eq(binPath)), times(1));
      grabbers.verify(PlatformContextGrabber::autoDetect, never());
      verify(grabber, times(1)).parse();
    }
  }

  @Test
  void create_returnsEmpty_whenGrabberProviderIsNull() throws IOException {
    var configuration = mock(LanguageServerConfiguration.class);
    var options = mock(V8PlatformOptions.class);
    when(configuration.getPlatformOptions()).thenReturn(options);
    when(options.isEnabled()).thenReturn(true);
    when(options.getBinPath()).thenReturn(null);

    var grabber = mock(PlatformContextGrabber.class);
    when(grabber.getProvider()).thenReturn(null);

    try (MockedStatic<PlatformContextGrabber> grabbers = mockStatic(PlatformContextGrabber.class)) {
      grabbers.when(PlatformContextGrabber::autoDetect).thenReturn(grabber);

      var factory = new PlatformContextProviderFactory(configuration);
      ReflectionTestUtils.setField(factory, ENABLED_FIELD, true);

      Optional<ContextProvider> result = factory.create();

      assertThat(result).isEmpty();
      verify(grabber, times(1)).parse();
    }
  }
}
