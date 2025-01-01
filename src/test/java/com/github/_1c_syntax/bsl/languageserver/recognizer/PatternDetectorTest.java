/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.recognizer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PatternDetectorTest {

  @Test
  void runTest() {
    PatternDetector detector = new PatternDetector(1,
      "^[/\\s]*(?:Процедура|Функция|Procedure|Function)\\s+[а-яА-Яё\\w]+\\s*?\\(",
      "^[/\\s]*(?:&На[а-яА-Яё]+|&At[\\w]+)\\s*?$*");

    assertThat(detector.detect("Процедура Какой-то текст")).isZero();
    assertThat(detector.detect("Процедура МояПро0цедура()")).isEqualTo(1);
    assertThat(detector.detect("Функция   МояФункция (  Параметр)")).isEqualTo(1);
    assertThat(detector.detect("Какой-то текст МояФункция была(")).isZero();
    assertThat(detector.detect("Функция или Процедура А")).isZero();
    assertThat(detector.detect("&НаКлиенте")).isEqualTo(1);
    assertThat(detector.detect("//&НаКлиенте")).isEqualTo(1);
    assertThat(detector.detect("&НаКлиенте\n\nПроцедура МояПро0цедура2(CGf)")).isEqualTo(1);
    assertThat(detector.detect("//&НаКлиенте")).isEqualTo(1);
    assertThat(detector.detect("//&AtServer")).isEqualTo(1);
    assertThat(detector.detect("&AtClient\nProcedure МояПро0цедура2(CGf)")).isEqualTo(1);
    assertThat(detector.detect("Функция MyFunc(Param, Param2)")).isEqualTo(1);
    assertThat(detector.detect("//&AtКлиент")).isZero();
  }

}
