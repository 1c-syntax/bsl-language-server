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

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.scope.GlobalSymbolScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Выбор языкового варианта дескриптора глобальной функции, существующей и в BSL,
 * и в OneScript ({@code ПодробноеПредставлениеОшибки}): в OS-файле должен
 * возвращаться OneScript-дескриптор, а не BSL-вариант с deprecation-метаданными
 * платформы 1С (issue #4054).
 */
@ExtendWith(MockitoExtension.class)
class GlobalScopeProviderLanguageVariantTest {

  private static final String FUNCTION_NAME = "ПодробноеПредставлениеОшибки";

  @Mock
  private BslContextHolder bslContextHolder;

  private GlobalScopeProvider scope;

  @BeforeEach
  void setUp() {
    // bsl-context отсутствует — BSL-часть грузится из builtin-globals.json,
    // где у функции есть deprecatedSinceVersion/recommendedReplacements.
    when(bslContextHolder.get()).thenReturn(Optional.empty());
    scope = new GlobalScopeProvider(bslContextHolder, new GlobalSymbolScope());
  }

  @Test
  void findFunctionReturnsOneScriptVariantInOsFile() {
    // when
    var descriptor = scope.findFunction(FUNCTION_NAME, FileType.OS).orElseThrow();

    // then — OneScript-вариант: без платформенного deprecation, с параметром errInfo
    assertThat(descriptor.metadata().deprecatedSinceVersion()).isEmpty();
    assertThat(descriptor.metadata().recommendedReplacements()).isEmpty();
    assertThat(descriptor.signatures()).hasSize(1);
    assertThat(descriptor.signatures().get(0).parameters())
      .extracting(ParameterDescriptor::name)
      .containsExactly("errInfo");
  }

  @Test
  void findFunctionByAliasReturnsOneScriptVariantInOsFile() {
    // when
    var descriptor = scope.findFunction("DetailErrorDescription", FileType.OS).orElseThrow();

    // then
    assertThat(descriptor.metadata().deprecatedSinceVersion()).isEmpty();
  }

  @Test
  void findFunctionReturnsBslVariantInBslFile() {
    // when
    var descriptor = scope.findFunction(FUNCTION_NAME, FileType.BSL).orElseThrow();

    // then — BSL-вариант с deprecation-метаданными платформы
    assertThat(descriptor.metadata().deprecatedSinceVersion()).isEqualTo("8.3.17");
    assertThat(descriptor.metadata().recommendedReplacements())
      .containsExactly("ОбработкаОшибок.ПодробноеПредставлениеОшибки");
  }

  @Test
  void getFunctionsReturnsOneScriptVariantForOsFileType() {
    // when
    var descriptor = scope.getFunctions(FileType.OS).stream()
      .filter(fn -> fn.name().equalsIgnoreCase(FUNCTION_NAME))
      .findFirst()
      .orElseThrow();

    // then — completion в OS-файле тоже должен видеть OneScript-вариант
    assertThat(descriptor.metadata().deprecatedSinceVersion()).isEmpty();
  }

  @Test
  void getFunctionsReturnsBslVariantForBslFileType() {
    // when
    var descriptor = scope.getFunctions(FileType.BSL).stream()
      .filter(fn -> fn.name().equalsIgnoreCase(FUNCTION_NAME))
      .findFirst()
      .orElseThrow();

    // then
    assertThat(descriptor.metadata().deprecatedSinceVersion()).isEqualTo("8.3.17");
  }

  @Test
  void findFunctionWithoutFileTypeKeepsMergedBehaviour() {
    // when — без типа файла отдаём merged-набор (BSL-приоритет)
    var descriptor = scope.findFunction(FUNCTION_NAME).orElseThrow();

    // then
    assertThat(descriptor.metadata().deprecatedSinceVersion()).isEqualTo("8.3.17");
  }
}
