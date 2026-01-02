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

import com.github._1c_syntax.bsl.languageserver.context.symbol.AnnotationParamSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class AnnotationParamSymbolMarkupContentBuilderTest {

  @Test
  void testGetContentWhenParentIsNotMethodSymbol() {
    // given
    var descriptionFormatter = Mockito.mock(DescriptionFormatter.class);
    var builder = new AnnotationParamSymbolMarkupContentBuilder(descriptionFormatter);

    var symbol = Mockito.mock(AnnotationParamSymbol.class);
    var parentSymbol = Mockito.mock(SourceDefinedSymbol.class);
    when(symbol.getParent()).thenReturn(Optional.of(parentSymbol));

    // when
    var result = builder.getContent(symbol);

    // then
    assertThat(result).isEqualTo(new MarkupContent(MarkupKind.MARKDOWN, ""));
  }

  @Test
  void testGetContentWhenParameterNotFound() {
    // given
    var descriptionFormatter = Mockito.mock(DescriptionFormatter.class);
    var builder = new AnnotationParamSymbolMarkupContentBuilder(descriptionFormatter);

    var symbol = Mockito.mock(AnnotationParamSymbol.class);
    when(symbol.getName()).thenReturn("paramName");

    var methodSymbol = Mockito.mock(MethodSymbol.class);
    when(methodSymbol.getParameters()).thenReturn(List.of());
    when(symbol.getParent()).thenReturn(Optional.of(methodSymbol));

    // when
    var result = builder.getContent(symbol);

    // then
    assertThat(result).isEqualTo(new MarkupContent(MarkupKind.MARKDOWN, ""));
  }

  @Test
  void testGetSymbolKind() {
    // given
    var descriptionFormatter = Mockito.mock(DescriptionFormatter.class);
    var builder = new AnnotationParamSymbolMarkupContentBuilder(descriptionFormatter);

    // when
    var result = builder.getSymbolKind();

    // then
    assertThat(result).isEqualTo(SymbolKind.TypeParameter);
  }
}
