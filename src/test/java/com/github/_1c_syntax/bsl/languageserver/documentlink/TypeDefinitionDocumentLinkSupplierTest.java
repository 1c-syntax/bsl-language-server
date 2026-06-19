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
package com.github._1c_syntax.bsl.languageserver.documentlink;

import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class TypeDefinitionDocumentLinkSupplierTest {

  @Test
  void methodParameterTypeWithSourceSymbolProducesLink() {
    // given
    var typeService = mock(TypeService.class);
    var supplier = new TypeDefinitionDocumentLinkSupplier(typeService);

    var content = """
      // Параметры:
      //  П - МойТип - описание
      Процедура Тест(П)
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(content);
    SourceDefinedSymbol targetSymbol = documentContext.getSymbolTree().getMethodSymbol("Тест").orElseThrow();

    when(typeService.resolve(eq("МойТип"), any())).thenReturn(Optional.of(TypeRef.ANY));
    when(typeService.definingSymbol(eq(TypeRef.ANY), any())).thenReturn(Optional.of(targetSymbol));

    // when
    var documentLinks = supplier.getDocumentLinks(documentContext);

    // then - ссылка покрывает имя типа «МойТип» и ведёт к объявляющему символу
    assertThat(documentLinks)
      .hasSize(1)
      .first()
      .satisfies(documentLink -> {
        assertThat(documentLink.getRange()).isEqualTo(Ranges.create(1, 8, 14));
        assertThat(documentLink.getTarget()).isEqualTo(symbolTarget(documentContext, targetSymbol.getSelectionRange()));
      });
  }

  @Test
  void trailingVariableTypeWithSourceSymbolProducesLink() {
    // given - тип переменной из висячего комментария (нотация «тип в начале»)
    var typeService = mock(TypeService.class);
    var supplier = new TypeDefinitionDocumentLinkSupplier(typeService);

    var content = """
      Процедура Тест()
          Перем Л; // МойТип - описание
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(content);
    SourceDefinedSymbol targetSymbol = documentContext.getSymbolTree().getMethodSymbol("Тест").orElseThrow();

    when(typeService.resolve(eq("МойТип"), any())).thenReturn(Optional.of(TypeRef.ANY));
    when(typeService.definingSymbol(eq(TypeRef.ANY), any())).thenReturn(Optional.of(targetSymbol));

    // when
    var documentLinks = supplier.getDocumentLinks(documentContext);

    // then
    assertThat(documentLinks)
      .hasSize(1)
      .first()
      .satisfies(documentLink -> {
        assertThat(documentLink.getRange()).isEqualTo(Ranges.create(1, 16, 22));
        assertThat(documentLink.getTarget()).isEqualTo(symbolTarget(documentContext, targetSymbol.getSelectionRange()));
      });
  }

  @Test
  void typeWithoutSourceSymbolProducesNoLink() {
    // given - тип резолвится, но не имеет объявляющего исходного символа (платформенный тип)
    var typeService = mock(TypeService.class);
    var supplier = new TypeDefinitionDocumentLinkSupplier(typeService);

    var content = """
      // Параметры:
      //  П - Строка - описание
      Процедура Тест(П)
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    when(typeService.resolve(eq("Строка"), any())).thenReturn(Optional.of(TypeRef.ANY));
    when(typeService.definingSymbol(eq(TypeRef.ANY), any())).thenReturn(Optional.empty());

    // when
    var documentLinks = supplier.getDocumentLinks(documentContext);

    // then
    assertThat(documentLinks).isEmpty();
  }

  private static String symbolTarget(
    com.github._1c_syntax.bsl.languageserver.context.DocumentContext documentContext,
    Range selectionRange
  ) {
    var start = selectionRange.getStart();
    return "%s#L%d,%d".formatted(documentContext.getUri(), start.getLine() + 1, start.getCharacter() + 1);
  }
}
