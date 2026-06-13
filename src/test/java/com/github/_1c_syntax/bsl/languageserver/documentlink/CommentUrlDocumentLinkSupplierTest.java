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

import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.DocumentLink;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterClass
class CommentUrlDocumentLinkSupplierTest {

  @Autowired
  private CommentUrlDocumentLinkSupplier supplier;

  @Test
  void testCommentWithUrlProducesLink() {
    // given
    var content = """
      // См. https://its.1c.ru/db/v8std
      Процедура Тест()
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    // when
    var documentLinks = supplier.getDocumentLinks(documentContext);

    // then
    assertThat(documentLinks)
      .hasSize(1)
      .first()
      .satisfies(documentLink -> {
        assertThat(documentLink.getTarget()).isEqualTo("https://its.1c.ru/db/v8std");
        assertThat(documentLink.getRange()).isEqualTo(Ranges.create(0, 7, 33));
      });
  }

  @Test
  void testMultipleUrlsInSingleComment() {
    // given
    var content = "// http://example.com и https://example.org";
    var documentContext = TestUtils.getDocumentContext(content);

    // when
    var documentLinks = supplier.getDocumentLinks(documentContext);

    // then
    assertThat(documentLinks)
      .extracting(DocumentLink::getTarget)
      .containsExactly("http://example.com", "https://example.org");
  }

  @Test
  void testCommentWithoutUrlProducesNothing() {
    // given
    var content = "// Просто комментарий без ссылок";
    var documentContext = TestUtils.getDocumentContext(content);

    // when
    var documentLinks = supplier.getDocumentLinks(documentContext);

    // then
    assertThat(documentLinks).isEmpty();
  }

  @Test
  void testUrlOutsideCommentIsIgnored() {
    // given
    var content = """
      Процедура Тест()
        Адрес = "https://example.com";
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    // when
    var documentLinks = supplier.getDocumentLinks(documentContext);

    // then
    assertThat(documentLinks).isEmpty();
  }
}
