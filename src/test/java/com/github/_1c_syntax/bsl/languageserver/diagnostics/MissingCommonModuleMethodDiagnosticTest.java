/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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

import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterEachTestMethod
class MissingCommonModuleMethodDiagnosticTest extends AbstractDiagnosticTest<MissingCommonModuleMethodDiagnostic> {

  private static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";

  MissingCommonModuleMethodDiagnosticTest() {
    super(MissingCommonModuleMethodDiagnostic.class);
  }

  @Test
  void test() {
    initServerContext(Absolute.path(PATH_TO_METADATA));

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasMessageOnRange("Метод МетодНесуществующий общего модуля ПервыйОбщийМодуль не существует", 1, 22, 41)
      .hasMessageOnRange("Метод ДругойМетодНесуществующий общего модуля ПервыйОбщийМодуль не существует", 2, 26, 51)
      .hasMessageOnRange("Метод ЕщеМетодНесуществующий общего модуля ПервыйОбщийМодуль не существует", 3, 22, 44)
      .hasMessageOnRange("Метод ЕщеОдинМетодНесуществующий общего модуля ПервыйОбщийМодуль не существует", 4, 22, 48)
      .hasMessageOnRange("Метод ЕщеДругойМетодНесуществующий общего модуля ПервыйОбщийМодуль не существует", 5, 26, 54)

      .hasMessageOnRange("Исправьте обращение к закрытому, неэкспортному методу РегистрацияИзмененийПередУдалением общего модуля ПервыйОбщийМодуль", 11, 22, 56)
      .hasMessageOnRange("Исправьте обращение к закрытому, неэкспортному методу Тест общего модуля ПервыйОбщийМодуль", 12, 26, 30)
      .hasMessageOnRange("Исправьте обращение к закрытому, неэкспортному методу Тест общего модуля ПервыйОбщийМодуль", 13, 22, 26)
      .hasMessageOnRange("Исправьте обращение к закрытому, неэкспортному методу Тест общего модуля ПервыйОбщийМодуль", 14, 22, 26)
      .hasMessageOnRange("Исправьте обращение к закрытому, неэкспортному методу Тест общего модуля ПервыйОбщийМодуль", 15, 26, 30)
      .hasSize(10);
  }

  @Test
  void testWithoutMetadata() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).isEmpty();
  }
}
