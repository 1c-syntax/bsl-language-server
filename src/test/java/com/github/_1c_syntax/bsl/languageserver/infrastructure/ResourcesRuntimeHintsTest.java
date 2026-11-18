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
package com.github._1c_syntax.bsl.languageserver.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.beans.factory.aot.AotServices;

import static org.assertj.core.api.Assertions.assertThat;

class ResourcesRuntimeHintsTest {

  private RuntimeHints hints;

  @BeforeEach
  void setUp() {
    hints = new RuntimeHints();
    new ResourcesRuntimeHints().registerHints(hints, getClass().getClassLoader());
  }

  @Test
  void testRegistrarIsAppliedByAotProcessing() {
    assertThat(AotServices.factories().load(RuntimeHintsRegistrar.class))
      .hasAtLeastOneElementOfType(ResourcesRuntimeHints.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "com.github._1c_syntax.bsl.languageserver.diagnostics.TypoDiagnostic",
    "com.github._1c_syntax.bsl.languageserver.aop.sentry.PermissionFilterBeforeSendCallback",
    "com.github._1c_syntax.bsl.languageserver.codelenses.CognitiveComplexityCodeLensSupplier"
  })
  void testResourceBundlesOfProjectAreRegistered(String baseName) {
    assertThat(RuntimeHintsPredicates.resource().forBundle(baseName)).accepts(hints);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "com/github/_1c_syntax/bsl/languageserver/diagnostics/ru/Typo.md",
    "com/github/_1c_syntax/bsl/languageserver/types/registry/builtin-globals.json",
    "com/github/_1c_syntax/bsl/languageserver/diagnostics/TypoDiagnostic_ru.properties"
  })
  void testProjectResourcesAreRegistered(String resource) {
    assertThat(RuntimeHintsPredicates.resource().forResource(resource)).accepts(hints);
  }
}
