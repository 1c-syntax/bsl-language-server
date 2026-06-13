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
package com.github._1c_syntax.bsl.languageserver.hover;

import com.github._1c_syntax.bsl.languageserver.context.symbol.KeywordSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KeywordSymbolMarkupContentBuilderTest {

  @Mock
  private Resources resources;

  private KeywordSymbolMarkupContentBuilder builder;

  @BeforeEach
  void setUp() {
    builder = new KeywordSymbolMarkupContentBuilder(resources);
    when(resources.getResourceString(eq(KeywordSymbolMarkupContentBuilder.class), any(String.class)))
      .thenAnswer(inv -> "[" + inv.getArgument(1) + "]");
  }

  @Test
  void getContentRendersKeywordLabelAndDescription() {
    // given
    var symbol = new KeywordSymbol("Если", "Условный оператор.",
      new Range(new Position(0, 0), new Position(0, 4)));

    // when
    var content = builder.getContent(referenceTo(symbol));

    // then
    assertThat(content.getKind()).isEqualTo(MarkupKind.MARKDOWN);
    assertThat(content.getValue())
      .contains("```bsl\nЕсли\n```")
      .contains("[keywordLabel]")
      .contains("Условный оператор.");
  }

  @Test
  void getSymbolClassReturnsKeywordSymbol() {
    // given / when
    var symbolClass = builder.getSymbolClass();

    // then
    assertThat(symbolClass).isEqualTo(KeywordSymbol.class);
  }

  private static Reference referenceTo(Symbol symbol) {
    return Reference.of(Mockito.mock(SourceDefinedSymbol.class), symbol,
      new Location("file:///t", new Range(new Position(0, 0), new Position(0, 0))));
  }
}
