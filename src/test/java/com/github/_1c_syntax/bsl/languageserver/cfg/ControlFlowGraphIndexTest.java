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
package com.github._1c_syntax.bsl.languageserver.cfg;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLTokenizer;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ControlFlowGraphIndexTest {

  private static final String CODE = """
    Если Истина Тогда
      А = 1;
    Иначе
      А = 2;
    КонецЕсли;
    Возврат А;
    """;

  private ControlFlowGraphIndex index;

  @BeforeEach
  void setUp() {
    index = new ControlFlowGraphIndex();
  }

  @Test
  void shouldReturnSameGraphOnRepeatedRequest() {
    // given
    var documentContext = documentContext("fake-uri.bsl");
    var codeBlock = codeBlockOf(documentContext);

    // when
    var first = index.graphOf(documentContext, codeBlock, CfgBuildOptions.defaults());
    var second = index.graphOf(documentContext, codeBlock, CfgBuildOptions.defaults());

    // then
    assertThat(second).isSameAs(first);
  }

  @Test
  void shouldBuildSeparateGraphsForDifferentOptions() {
    // given
    var documentContext = documentContext("fake-uri.bsl");
    var codeBlock = codeBlockOf(documentContext);

    // when
    var withLoops = index.graphOf(documentContext, codeBlock, CfgBuildOptions.defaults());
    var withoutLoops = index.graphOf(
      documentContext,
      codeBlock,
      CfgBuildOptions.defaults().withLoopIterations(false)
    );

    // then
    assertThat(withoutLoops).isNotSameAs(withLoops);
  }

  @Test
  void shouldRebuildGraphAfterContentChange() {
    // given
    var documentContext = documentContext("fake-uri.bsl");
    var codeBlock = codeBlockOf(documentContext);
    var before = index.graphOf(documentContext, codeBlock, CfgBuildOptions.defaults());

    // when
    index.handleContentChanged(new DocumentContextContentChangedEvent(documentContext));
    var after = index.graphOf(documentContext, codeBlock, CfgBuildOptions.defaults());

    // then
    assertThat(after).isNotSameAs(before);
  }

  @Test
  void shouldKeepGraphsOfOtherDocumentsOnClear() {
    // given
    var cleared = documentContext("cleared.bsl");
    var kept = documentContext("kept.bsl");
    var keptBlock = codeBlockOf(kept);
    var before = index.graphOf(kept, keptBlock, CfgBuildOptions.defaults());
    index.graphOf(cleared, codeBlockOf(cleared), CfgBuildOptions.defaults());

    // when
    index.clear(cleared.getUri());
    var after = index.graphOf(kept, keptBlock, CfgBuildOptions.defaults());

    // then
    assertThat(after).isSameAs(before);
  }

  private static DocumentContext documentContext(String fileName) {
    var documentContext = mock(DocumentContext.class);
    when(documentContext.getUri()).thenReturn(uriOf(fileName));
    when(documentContext.getAst()).thenReturn(new BSLTokenizer(CODE).getAst());
    return documentContext;
  }

  private static URI uriOf(String fileName) {
    return Absolute.path("src/test/resources/empty-workspace/" + fileName).toUri();
  }

  private static BSLParser.CodeBlockContext codeBlockOf(DocumentContext documentContext) {
    return documentContext.getAst().fileCodeBlock().codeBlock();
  }
}
