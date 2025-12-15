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
package com.github._1c_syntax.bsl.languageserver.hover;

import com.github._1c_syntax.bsl.languageserver.context.symbol.AnnotationSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class AnnotationSymbolMarkupContentBuilderTest {

  @Test
  void testGetContentWhenParentIsNotMethodSymbol() {
    // given
    var descriptionFormatter = Mockito.mock(DescriptionFormatter.class);
    var builder = new AnnotationSymbolMarkupContentBuilder(descriptionFormatter);
    var annotationSymbol = Mockito.mock(AnnotationSymbol.class);
    var sourceDefinedSymbol = Mockito.mock(SourceDefinedSymbol.class);
    when(annotationSymbol.getParent()).thenReturn(Optional.of(sourceDefinedSymbol));

    // when
    MarkupContent content = builder.getContent(annotationSymbol);

    // then
    assertThat(content.getValue()).isEmpty();
    assertThat(content.getKind()).isEqualTo(MarkupKind.MARKDOWN);
  }

  @Test
  void testGetContentWhenParentIsEmpty() {
    // given
    var descriptionFormatter = Mockito.mock(DescriptionFormatter.class);
    var builder = new AnnotationSymbolMarkupContentBuilder(descriptionFormatter);
    var annotationSymbol = Mockito.mock(AnnotationSymbol.class);
    when(annotationSymbol.getParent()).thenReturn(Optional.empty());

    // when
    MarkupContent content = builder.getContent(annotationSymbol);

    // then
    assertThat(content.getValue()).isEmpty();
    assertThat(content.getKind()).isEqualTo(MarkupKind.MARKDOWN);
  }

  @Test
  void testGetSymbolKind() {
    // given
    var descriptionFormatter = Mockito.mock(DescriptionFormatter.class);
    var builder = new AnnotationSymbolMarkupContentBuilder(descriptionFormatter);

    // when
    SymbolKind symbolKind = builder.getSymbolKind();

    // then
    assertThat(symbolKind).isEqualTo(SymbolKind.Interface);
  }
}
