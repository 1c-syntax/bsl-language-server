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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SynchronousMethodTest {

  @Test
  void analogIsNamedInTheLanguageOfTheCall() {
    assertThat(SynchronousMethod.analogOf("вопрос")).isEqualTo("ПоказатьВопрос");
    assertThat(SynchronousMethod.analogOf("doQueryBox")).isEqualTo("ShowQueryBox");
  }

  @Test
  void modalMethodsAreASubsetOfSynchronousOnes() {
    assertThat(SynchronousMethod.byName("Предупреждение")).hasValueSatisfying(
      method -> assertThat(method.isModal()).isTrue());
    assertThat(SynchronousMethod.byName("FileCopy")).hasValueSatisfying(
      method -> assertThat(method.isModal()).isFalse());
    assertThat(SynchronousMethod.byName("Сообщить")).isEmpty();
  }

  @Test
  void nonModalFormOpeningIsNotACallbackAnalog() {
    assertThat(SynchronousMethod.isCallbackAnalog("showQueryBox")).isTrue();
    assertThat(SynchronousMethod.isCallbackAnalog("НачатьКопированиеФайла")).isTrue();
    assertThat(SynchronousMethod.isCallbackAnalog("ОткрытьФорму")).isFalse();
    assertThat(SynchronousMethod.isCallbackAnalog("Вопрос")).isFalse();
  }
}
