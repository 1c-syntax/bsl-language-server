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
package com.github._1c_syntax.bsl.languageserver.aop;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.computer.CognitiveComplexityComputer;
import com.github._1c_syntax.bsl.languageserver.context.computer.ComplexityData;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import org.junit.jupiter.params.ParameterizedTest;
import org.springframework.aop.aspectj.annotation.ReflectiveAspectJAdvisorFactory;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import static org.assertj.core.api.Assertions.assertThat;

class AspectJConfigurationTest {

  @ParameterizedTest
  @ValueSource(classes = {EventPublisherAspect.class, SentryAspect.class, MeasuresAspect.class})
  void testAspectOfIsRegisteredForReflectiveInvocation(Class<?> aspectClass) {
    // given
    var hints = new RuntimeHints();

    // when
    new AspectJConfiguration.AspectsRuntimeHints().registerHints(hints, getClass().getClassLoader());

    // then
    assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(aspectClass, "aspectOf"))
      .accepts(hints);
  }

  /**
   * Аспекты вплетаются при компиляции (ajc); Spring AOP не должен применять их повторно через
   * прокси, иначе advice выполняется дважды (например, события публикуются по два раза).
   */
  @ParameterizedTest
  @ValueSource(classes = {EventPublisherAspect.class, SentryAspect.class, MeasuresAspect.class})
  void testCompileTimeWovenAspectsAreNotAppliedBySpringAop(Class<?> aspectClass) {
    assertThat(new ReflectiveAspectJAdvisorFactory().isAspect(aspectClass)).isFalse();
  }

  /**
   * Статические части join point'ов, вплетённых ajc, разрешают типы своих сигнатур по имени
   * ({@code Factory.makeClass}).
   */
  @ParameterizedTest
  @ValueSource(classes = {
    DocumentContext.class,              // объявляющий тип вплетённого метода
    SymbolTree.class,                   // тип результата DocumentContext.computeSymbolTree
    CognitiveComplexityComputer.class,
    ComplexityData.class
  })
  void testTypesOfWovenJoinPointSignaturesAreRegistered(Class<?> type) {
    // given
    var hints = new RuntimeHints();

    // when
    new AspectJConfiguration.AspectsRuntimeHints().registerHints(hints, getClass().getClassLoader());

    // then
    assertThat(RuntimeHintsPredicates.reflection().onType(type)).accepts(hints);
  }
}
