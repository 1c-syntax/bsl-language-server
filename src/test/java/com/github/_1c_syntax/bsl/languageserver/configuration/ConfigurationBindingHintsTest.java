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
package com.github._1c_syntax.bsl.languageserver.configuration;

import com.github._1c_syntax.bsl.languageserver.configuration.codelens.TestRunnerAdapterOptions;
import com.github._1c_syntax.bsl.languageserver.configuration.databind.AnnotationsDeserializer;
import com.github._1c_syntax.bsl.languageserver.configuration.databind.DiagnosticMetadataMapDeserializer;
import com.github._1c_syntax.bsl.languageserver.configuration.databind.ParametersDeserializer;
import com.github._1c_syntax.bsl.languageserver.configuration.diagnostics.DiagnosticsOptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.annotation.ReflectiveRuntimeHintsRegistrar;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Конфигурация, которую Jackson читает из файла, регистрируется для native-image.
 */
class ConfigurationBindingHintsTest {

  static Stream<Arguments> boundTypes() {
    return Stream.of(
      Arguments.of(LanguageServerConfiguration.class, DiagnosticsOptions.class, "setMode"),
      Arguments.of(LanguageServerConfiguration.class, TestRunnerAdapterOptions.class, "setTestSources"),
      Arguments.of(GlobalLanguageServerConfiguration.class, GlobalLanguageServerConfiguration.class, "setSendErrors")
    );
  }

  @ParameterizedTest
  @MethodSource("boundTypes")
  void testConfigurationTypesAreRegisteredForBinding(Class<?> configuration, Class<?> boundType, String accessor) {
    var hints = registerHints(configuration);

    assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(boundType, accessor)).accepts(hints);
  }

  @ParameterizedTest
  @ValueSource(classes = {
    ParametersDeserializer.class,
    AnnotationsDeserializer.class,
    DiagnosticMetadataMapDeserializer.class
  })
  void testCustomDeserializersAreRegistered(Class<?> deserializer) {
    var hints = registerHints(LanguageServerConfiguration.class);

    assertThat(RuntimeHintsPredicates.reflection().onType(deserializer)
      .withMemberCategory(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)).accepts(hints);
  }

  private static RuntimeHints registerHints(Class<?> configuration) {
    var hints = new RuntimeHints();
    new ReflectiveRuntimeHintsRegistrar().registerRuntimeHints(hints, configuration);
    return hints;
  }
}
