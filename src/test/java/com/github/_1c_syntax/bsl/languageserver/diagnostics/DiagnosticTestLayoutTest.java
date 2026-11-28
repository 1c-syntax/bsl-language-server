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

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Контракт: у каждой диагностики есть свой тестовый класс {@code XxxDiagnosticTest} — наследник
 * {@link AbstractDiagnosticTest}, сверяющий срабатывания на её фикстуре.
 */
class DiagnosticTestLayoutTest {

  @Test
  void everyDiagnosticHasItsOwnTestClass() throws IOException {
    Set<String> withoutTest = new TreeSet<>();
    for (var code : DiagnosticDocsLayoutTest.diagnosticCodes()) {
      if (!hasTestClass(code + "DiagnosticTest")) {
        withoutTest.add(code);
      }
    }

    assertThat(withoutTest).as("диагностики без XxxDiagnosticTest").isEmpty();
  }

  private static boolean hasTestClass(String simpleClassName) {
    try {
      var testClass = Class.forName(DiagnosticTestLayoutTest.class.getPackageName() + "." + simpleClassName);
      return AbstractDiagnosticTest.class.isAssignableFrom(testClass);
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}
