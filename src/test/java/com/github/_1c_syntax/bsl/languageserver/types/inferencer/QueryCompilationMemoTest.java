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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.CompilerDirectiveKind;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class QueryCompilationMemoTest extends AbstractServerContextAwareTest {
  @Autowired
  private CompilationContextResolver resolver;

  @Test
  void profilesAreSharedButEachPositionRetainsItsOwnPreprocessorBranch() {
    var root = Path.of("src/test/resources/metadata/commonModuleCalls");
    initServerContext(root, false);
    var source = """
      &AtClient
      Procedure Client()
        #If WebClient Then
          WebValue = 1;
        #Else
          OtherValue = 2;
        #EndIf
        #If Windows Then
          UnknownValue = 3;
        #EndIf
      EndProcedure
      &AtServer
      Procedure Server()
        ServerValue = 4;
      EndProcedure
      """;
    var document = TestUtils.getDocumentContext(root.resolve("CommonModules/КлиентСерверныйМодуль/Ext/Module.bsl").toUri(),
      source, context);
    CompilationContextResolver original = AopTestUtils.getUltimateTargetObject(resolver);
    var observed = spy(original);
    var memo = new CompilationContextMemo();
    var lines = source.split("\n", -1);
    for (int line = 0; line < lines.length; line++) {
      for (int column = 0; column <= lines[line].length(); column++) {
        var position = new Position(line, column);
        assertThat(memo.contextAt(document, position, observed))
          .as("position %s:%s", line, column).isEqualTo(original.contextAt(document, position));
      }
    }
    verify(observed, times(1)).methodContext(document, Optional.of(CompilerDirectiveKind.AT_CLIENT));
    verify(observed, times(1)).methodContext(document, Optional.of(CompilerDirectiveKind.AT_SERVER));
    context.rebuildDocument(document, source.replace("WebClient", "ThinClient"), 1);
    var refreshed = new CompilationContextMemo();
    var after = new Position(3, 4);
    assertThat(refreshed.contextAt(document, after, original)).isEqualTo(original.contextAt(document, after));
  }
}
