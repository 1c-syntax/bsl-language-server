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
package com.github._1c_syntax.bsl.languageserver.inlayhints;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Mockito-юнит для {@link PlatformMethodCallInlayHintSupplier} — покрывает
 * пути без полной интеграции (null AST, defaults для конфигурации).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformMethodCallInlayHintSupplierUnitTest {

  @Mock
  private TypeService typeService;
  @Mock
  private LanguageServerConfiguration configuration;
  @Mock
  private Resources resources;
  @Mock
  private DocumentContext documentContext;

  private PlatformMethodCallInlayHintSupplier supplier;

  @BeforeEach
  void setUp() {
    supplier = new PlatformMethodCallInlayHintSupplier(configuration, typeService, resources);
  }

  @Test
  void getInlayHintsWithNullAstReturnsEmpty() {
    // given — documentContext.getAst() возвращает null.
    when(documentContext.getAst()).thenReturn(null);
    var params = new InlayHintParams();
    params.setRange(new Range(new Position(0, 0), new Position(0, 0)));

    // when
    var hints = supplier.getInlayHints(documentContext, params);

    // then — L96 return List.of().
    assertThat(hints).isEmpty();
  }
}
