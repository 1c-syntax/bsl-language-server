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
package com.github._1c_syntax.bsl.languageserver.documentlink;

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DescriptionDocumentLinkSupplierTest {

  @Autowired
  private DescriptionDocumentLinkSupplier supplier;

  @Test
  void testGetDocumentLinks() {
    // given
    var filePath = "./src/test/resources/documentlink/descriptionDocumentLinkSupplier.bsl";
    var documentContext = TestUtils.getDocumentContextFromFile(filePath);

    // when
    var documentLinks = supplier.getDocumentLinks(documentContext);

    // then
    assertThat(documentLinks)
      .isNotEmpty()
      .hasSize(5);

    // Verify we have the expected URLs
    var urls = documentLinks.stream()
      .map(link -> link.getTarget())
      .toList();

    assertThat(urls)
      .contains("https://example.com/info")
      .contains("https://docs.example.com/api/method")
      .contains("ftp://files.example.com/docs")
      .contains("https://main.example.com/docs")
      .contains("http://additional.example.org/info");
  }

  @Test
  void testEmptyDescriptions() {
    // given - file with no method/variable descriptions
    var filePath = "./src/test/resources/cli/test.bsl";
    var documentContext = TestUtils.getDocumentContextFromFile(filePath);

    // when
    var documentLinks = supplier.getDocumentLinks(documentContext);

    // then - should have no links from descriptions
    assertThat(documentLinks).isEmpty();
  }
}
