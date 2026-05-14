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
package com.github._1c_syntax.bsl.languageserver.types.oscript;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class OScriptLibraryFileParserTest extends AbstractServerContextAwareTest {

  @Autowired
  private OScriptLibraryFileParser parser;

  @Test
  void parsesModuleExports() {
    initServerContext();
    var file = Path.of("src/test/resources/oscript-libraries/mylib/src/MyModule.os").toAbsolutePath();

    var result = parser.parse(file, context);

    assertThat(result).isPresent();
    var lib = result.get();
    assertThat(lib.exportVars()).containsExactly("СтатусМодуля");
    assertThat(lib.exportMethods()).extracting(OScriptLibraryFileParser.MethodInfo::name)
      .containsExactlyInAnyOrder("ВывестиСообщение", "СформироватьСтроку");
    assertThat(lib.constructor()).isEmpty();

    var format = lib.exportMethods().stream()
      .filter(m -> m.name().equals("СформироватьСтроку"))
      .findFirst().orElseThrow();
    assertThat(format.function()).isTrue();
    assertThat(format.signatures()).hasSize(1);
    assertThat(format.signatures().get(0).parameters())
      .extracting(p -> p.name())
      .containsExactly("Префикс", "Часть");
  }

  @Test
  void parsesClassWithConstructor() {
    initServerContext();
    var file = Path.of("src/test/resources/oscript-libraries/mylib/src/MyClass.os").toAbsolutePath();

    var result = parser.parse(file, context);

    assertThat(result).isPresent();
    var lib = result.get();
    assertThat(lib.constructor()).isPresent();
    var ctor = lib.constructor().get();
    assertThat(ctor.name()).isEqualToIgnoringCase("ПриСозданииОбъекта");
    assertThat(ctor.signatures().get(0).parameters())
      .extracting(p -> p.name())
      .containsExactly("Имя");

    assertThat(lib.exportMethods()).extracting(OScriptLibraryFileParser.MethodInfo::name)
      .containsExactly("ПолучитьСтроку");
    assertThat(lib.exportVars()).containsExactly("СтатусМодуля");
  }
}
