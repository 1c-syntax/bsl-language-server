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
package com.github._1c_syntax.bsl.languageserver.types.scope;

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class UseDirectiveScannerTest {

  @Test
  void extractsSingleLibrary() {
    var dc = TestUtils.getDocumentContext("#Использовать fs\n");
    assertThat(UseDirectiveScanner.usedLibraries(dc)).containsExactly("fs");
  }

  @Test
  void extractsMultipleLibrariesInOrder() {
    var dc = TestUtils.getDocumentContext("#Использовать fs\n#Использовать tempfiles\n#Использовать json\n");
    assertThat(UseDirectiveScanner.usedLibrariesList(dc)).containsExactly("fs", "tempfiles", "json");
  }

  @Test
  void returnsEmptyWhenNoDirective() {
    var dc = TestUtils.getDocumentContext("Сообщить(\"Hello\");\n");
    assertThat(UseDirectiveScanner.usedLibraries(dc)).isEmpty();
  }

  @Test
  void extractsRelativePathLibraryByDirectoryName() {
    // #Использовать "путь" — подключение каталога-библиотеки относительным путём;
    // имя библиотеки для gating'а — имя каталога (последний сегмент пути).
    var dc = TestUtils.getDocumentContext("#Использовать \"lib\"\n");
    assertThat(UseDirectiveScanner.usedLibraries(dc)).containsExactly("lib");
  }

  @Test
  void extractsNestedRelativePathLibraryByLastSegment() {
    var dc = TestUtils.getDocumentContext("#Использовать \"./libs/mylib\"\n");
    assertThat(UseDirectiveScanner.usedLibraries(dc)).containsExactly("mylib");
  }

  @Test
  void extractsBothIdentifierAndStringDirectives() {
    var dc = TestUtils.getDocumentContext("#Использовать fs\n#Использовать \"lib\"\n");
    assertThat(UseDirectiveScanner.usedLibrariesList(dc)).containsExactly("fs", "lib");
  }
}
