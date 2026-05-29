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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.MethodCallNode;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Покрытие private-ветвей {@link DereferenceMemberMatcher}, до которых не
 * доходят integration-тесты диагностик (например, метод-вызов без аргументов).
 */
class DereferenceMemberMatcherReflectionTest {

  @Test
  void countMeaningfulArgsZeroForEmptyCall() {
    var call = Mockito.mock(MethodCallNode.class);
    Mockito.when(call.arguments()).thenReturn(List.of());

    int n = invokeCountMeaningfulArgs(call);

    assertThat(n).isZero();
  }

  @Test
  void inferArgTypesEmptyForEmptyCall() {

    // given — MethodCallNode без аргументов: ветвь n==0 в inferArgTypes
    // (integration-тесты диагностик передают только не-пустые вызовы).
    var matcher = new DereferenceMemberMatcher(
      Mockito.mock(TypeRegistry.class),
      Mockito.mock(ExpressionTypeInferencer.class));
    var call = Mockito.mock(MethodCallNode.class);
    Mockito.when(call.arguments()).thenReturn(List.of());

    List<TypeSet> result = invokeInferArgTypes(matcher, call);

    assertThat(result).isEmpty();
  }

  @SneakyThrows
  private static int invokeCountMeaningfulArgs(MethodCallNode call) {
    var method = findMethod("countMeaningfulArgs");
    return (int) method.invoke(null, call);
  }

  @SneakyThrows
  @SuppressWarnings("unchecked")
  private static List<TypeSet> invokeInferArgTypes(DereferenceMemberMatcher matcher,
                                                   MethodCallNode call) {
    var method = findMethod("inferArgTypes");
    return (List<TypeSet>) method.invoke(matcher, call, null);
  }

  private static Method findMethod(String name) {
    for (var m : DereferenceMemberMatcher.class.getDeclaredMethods()) {
      if (m.getName().equals(name)) {
        m.setAccessible(true);
        return m;
      }
    }
    throw new IllegalStateException(name + " not found");
  }
}
