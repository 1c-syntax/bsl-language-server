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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.codelenses.DebugTestCodeLensSupplier;
import com.github._1c_syntax.bsl.languageserver.codelenses.infrastructure.CodeLensesConfiguration;
import com.github._1c_syntax.bsl.languageserver.commands.DefaultCommandArguments;
import com.github._1c_syntax.bsl.languageserver.commands.complexity.ToggleComplexityInlayHintsCommandArguments;
import com.github._1c_syntax.bsl.languageserver.commands.infrastructure.CommandsConfiguration;
import com.github._1c_syntax.bsl.languageserver.completion.CompletionData;
import com.github._1c_syntax.bsl.languageserver.inlayhints.VariableTypeInlayHintData;
import com.github._1c_syntax.bsl.languageserver.inlayhints.infrastructure.InlayHintsConfiguration;
import com.github._1c_syntax.bsl.languageserver.util.AotHints;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Данные, которые сервер отдаёт клиенту в полях {@code data} LSP-объектов и в аргументах команд и
 * читает обратно Jackson'ом, регистрируются для native-image владельцами этих типов.
 */
class LspDataRuntimeHintsTest {

  static Stream<Arguments> dataTypes() {
    return Stream.of(
      Arguments.of(CodeLensesConfiguration.class, DebugTestCodeLensSupplier.DebugTestCodeLensData.class, "getTestId"),
      Arguments.of(CommandsConfiguration.class, DefaultCommandArguments.class, "getUri"),
      Arguments.of(CommandsConfiguration.class, ToggleComplexityInlayHintsCommandArguments.class, "getMethodName"),
      Arguments.of(InlayHintsConfiguration.class, VariableTypeInlayHintData.class, "getTypeName"),
      Arguments.of(CompletionProvider.class, CompletionData.class, "getUri")
    );
  }

  @ParameterizedTest
  @MethodSource("dataTypes")
  void testDataTypesAreRegisteredForBinding(Class<?> owner, Class<?> dataType, String accessor) {
    // when
    var hints = AotHints.hintsOf(owner);

    // then
    assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(dataType, accessor)).accepts(hints);
  }
}
