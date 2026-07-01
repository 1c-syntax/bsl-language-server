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

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Inlay hints в типичных позициях BSL: глобальные функции с разной арностью,
 * конструкторы, метод-вызовы.
 */
@CleanupContextBeforeClassAndAfterClass
class InlayHintPatternsTest extends AbstractServerContextAwareTest {

  @Autowired
  private PlatformMethodCallInlayHintCollector supplier;

  @Test
  void hintsForGlobalFunctionWithMultipleArgs() {
    var hints = hintsFromFixture();
    // СтрНайти, СтрЗаменить, СтрРазделить — каждый имеет именованные параметры.
    assertThat(hints)
      .anySatisfy(h -> assertThat(h.getKind()).isEqualTo(InlayHintKind.Parameter));
  }

  @Test
  void hintsForConstructorWithSingleArg() {
    var hints = hintsFromFixture();
    // НеПустМ = Новый Массив(10) — hint на параметр конструктора.
    assertThat(hints).isNotEmpty();
  }

  @Test
  void hintsForMethodCallWithArgs() {
    var hints = hintsFromFixture();
    // НеПустМ.Добавить(99) — hint на параметр Значение.
    assertThat(hints).isNotEmpty();
  }

  @Test
  void hintsForGlobalFunctionInExpression() {
    var hints = hintsFromFixture();
    // СтрДлина внутри + — supplier должен обработать как обычно.
    assertThat(hints).isNotEmpty();
  }

  @Test
  void hintsForCallWithVariableArg() {
    var hints = hintsFromFixture();
    // Длина = СтрДлина(Стр) — переменная как аргумент.
    assertThat(hints).isNotEmpty();
  }

  private java.util.List<InlayHint> hintsFromFixture() {
    initServerContext("./src/test/resources/types", false);
    var dc = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/InlayHintPatterns.bsl", context);
    var params = new InlayHintParams();
    params.setRange(new Range(new Position(0, 0),
      new Position(dc.getContent().split("\n").length, 0)));
    return supplier.getInlayHints(dc, params);
  }
}
