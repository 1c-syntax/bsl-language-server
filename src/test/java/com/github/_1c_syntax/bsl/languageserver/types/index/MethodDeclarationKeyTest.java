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
package com.github._1c_syntax.bsl.languageserver.types.index;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegularMethodSymbol;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MethodDeclarationKeyTest {

  @Test
  void declarationOrderIgnoresDocumentVersion() {
    var uri = URI.create("file:///Module.bsl");
    var server = mock(ServerContext.class);
    var firstDocument = new DocumentContext(uri, server);
    var editedDocument = new DocumentContext(uri, server);
    ReflectionTestUtils.setField(editedDocument, "version", 2);
    var first = key(firstDocument, "Run", 3, 9);
    var edited = key(editedDocument, "Run", 3, 9);

    assertThat(firstDocument).isNotEqualByComparingTo(editedDocument);
    assertThat(first).isEqualTo(edited).hasSameHashCodeAs(edited).isEqualByComparingTo(edited);
    assertThat(edited).isEqualByComparingTo(first);
  }

  @Test
  void declarationOrderDistinguishesNameAndSelectionRange() {
    var document = new DocumentContext(URI.create("file:///Module.bsl"), mock(ServerContext.class));
    var first = key(document, "Run", 3, 9);
    var differentCase = key(document, "run", 3, 9);
    var laterLine = key(document, "Run", 4, 9);
    var longerRange = key(document, "Run", 3, 10);

    assertThat(first).isLessThan(differentCase).isLessThan(laterLine).isLessThan(longerRange);
    assertThat(longerRange).isGreaterThan(first);
  }

  private static MethodDeclarationKey key(DocumentContext document, String name, int line, int endCharacter) {
    var method = RegularMethodSymbol.builder()
      .owner(document)
      .name(name)
      .subNameLine(line)
      .subNameStartCharacter(6)
      .subNameEndCharacter(endCharacter)
      .build();
    return new MethodDeclarationKey(method);
  }
}
