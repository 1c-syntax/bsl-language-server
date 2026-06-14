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

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.eclipse.lsp4j.DocumentLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

class SeeReferenceDocumentLinkSupplierTest extends AbstractServerContextAwareTest {

  private static final String PATH_TO_FILE = "./src/test/resources/documentlink/seeReferenceDocumentLinkSupplier.bsl";

  @Autowired
  private SeeReferenceDocumentLinkSupplier supplier;

  @BeforeEach
  void prepareServerContext() {
    initServerContextOnce(Path.of(PATH_TO_METADATA));
  }

  @Test
  void testSameModuleReferenceProducesLink() {
    // given
    var content = """
      // См. ДругойМетод.
      Процедура Тест() Экспорт
      КонецПроцедуры

      Процедура ДругойМетод() Экспорт
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(content);
    var targetMethod = documentContext.getSymbolTree().getMethodSymbol("ДругойМетод").orElseThrow();

    // when
    var documentLinks = supplier.getDocumentLinks(documentContext);

    // then
    assertThat(documentLinks)
      .hasSize(1)
      .first()
      .satisfies(documentLink -> {
        assertThat(documentLink.getRange()).isEqualTo(Ranges.create(0, 7, 18));
        assertThat(documentLink.getTarget())
          .isEqualTo(targetTarget(documentContext.getUri().toString(), targetMethod.getSelectionRange()));
      });
  }

  @Test
  void testCommonModuleReferenceProducesLink() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var commonModule = context.getDocument("CommonModule.ПервыйОбщийМодуль", ModuleType.CommonModule).orElseThrow();
    var targetMethod = commonModule.getSymbolTree().getMethodSymbol("НеУстаревшаяПроцедура").orElseThrow();

    // when
    var documentLinks = supplier.getDocumentLinks(documentContext);

    // then
    assertThat(documentLinks)
      .anySatisfy(documentLink ->
        assertThat(documentLink.getTarget())
          .isEqualTo(targetTarget(commonModule.getUri().toString(), targetMethod.getSelectionRange()))
      );
  }

  @Test
  void testUnresolvedReferenceProducesNothing() {
    // given
    var content = """
      // См. НесуществующийМетод.
      Процедура Тест() Экспорт
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    // when
    var documentLinks = supplier.getDocumentLinks(documentContext);

    // then
    assertThat(documentLinks).isEmpty();
  }

  private static String targetTarget(String uri, org.eclipse.lsp4j.Range range) {
    var start = range.getStart();
    return "%s#L%d,%d".formatted(uri, start.getLine() + 1, start.getCharacter() + 1);
  }
}
