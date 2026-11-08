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

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.references.ReferencesOptions;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.utils.ModuleReference;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslExpression;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.MethodCallNode;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class CommonModuleAccessorReuseTest {
  private final LanguageServerConfiguration configuration = new LanguageServerConfiguration();
  private final TypeRegistry registry = mock(TypeRegistry.class);
  private final CommonModuleByNameInference inference = new CommonModuleByNameInference(configuration, registry);
  private final TypeRef module = new TypeRef(TypeKind.CONFIGURATION, "Модуль");

  @Test
  void repeatedLocalAndQualifiedCallsReuseTheConfigurationSnapshot(TestReporter reporter) {
    var options = new ReferencesOptions(new ArrayList<>(List.of("Получить", "Служба.Получить")));
    configuration.setReferencesOptions(options);
    when(registry.resolve("Модуль", FileType.BSL)).thenReturn(Optional.of(module));
    var call = call("Получить");
    var receiver = TypeSet.of(new TypeRef(TypeKind.CONFIGURATION, "Служба"));
    var parses = new AtomicInteger();
    try (var counted = mockStatic(ModuleReference.class, CALLS_REAL_METHODS)) {
      counted.when(() -> ModuleReference.parseAccessors(anyList())).thenAnswer(invocation -> {
        parses.incrementAndGet();
        return invocation.callRealMethod();
      });
      for (var i = 0; i < 100; i++) {
        assertThat(inference.localCallTypes(call, FileType.BSL).refs()).containsExactly(module);
        assertThat(inference.refinedCallTypes(receiver, "Получить", call, FileType.BSL).refs()).containsExactly(module);
      }
      reporter.publishEntry("accessorParses", Integer.toString(parses.get()));
      assertThat(parses.get()).isZero();
    }
  }

  @Test
  void changedPatternsApplyThroughTheConfigurationSetter() {
    var options = new ReferencesOptions(new ArrayList<>(List.of("Первый")));
    configuration.setReferencesOptions(options);
    when(registry.resolve("Модуль", FileType.BSL)).thenReturn(Optional.of(module));
    assertThat(inference.localCallTypes(call("Первый"), FileType.BSL)).isNotNull();
    assertThat(inference.localCallTypes(call("Второй"), FileType.BSL)).isNull();
    options.setCommonModuleAccessors(new ArrayList<>(List.of("Второй")));
    assertThat(inference.localCallTypes(call("Первый"), FileType.BSL)).isNotNull();
    assertThat(inference.localCallTypes(call("Второй"), FileType.BSL)).isNull();
    configuration.setReferencesOptions(options);
    assertThat(inference.localCallTypes(call("Первый"), FileType.BSL)).isNull();
    assertThat(inference.localCallTypes(call("Второй"), FileType.BSL)).isNotNull();
    options.setCommonModuleAccessors(List.of("Второй", "Первый"));
    assertThat(inference.localCallTypes(call("Первый"), FileType.BSL)).isNull();
    configuration.setReferencesOptions(options);
    assertThat(inference.localCallTypes(call("Первый"), FileType.BSL)).isNotNull();
    var current = new ReferencesOptions(configuration.getReferencesOptions().getCommonModuleAccessors());
    current.setCommonModuleAccessors(List.of("Первый"));
    assertThat(inference.localCallTypes(call("Второй"), FileType.BSL)).isNotNull();
    configuration.setReferencesOptions(current);
    assertThat(inference.localCallTypes(call("Второй"), FileType.BSL)).isNull();
    configuration.setReferencesOptions(new ReferencesOptions(List.of("Третий")));
    assertThat(inference.localCallTypes(call("Первый"), FileType.BSL)).isNull();
    assertThat(inference.localCallTypes(call("Третий"), FileType.BSL)).isNotNull();
  }

  private static MethodCallNode call(String name) {
    var call = MethodCallNode.create(new TerminalNodeImpl(new CommonToken(0, name)));
    var argument = mock(BslExpression.class);
    when(argument.getRepresentingAst()).thenReturn(new TerminalNodeImpl(new CommonToken(0, "\"Модуль\"")));
    call.addArgument(argument);
    return call;
  }
}
