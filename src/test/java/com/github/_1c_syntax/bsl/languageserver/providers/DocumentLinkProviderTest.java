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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterClass
class DocumentLinkProviderTest {

  @Autowired
  private LanguageServerConfiguration configuration;

  @Autowired
  private DocumentLinkProvider documentLinkProvider;

  @Test
  void testProviderCanGetResultFromEnabledComputers() {
    // given
    configuration.getDocumentLinkOptions().setShowDiagnosticDescription(true);

    var filePath = "./src/test/resources/providers/documentLinkProvider.bsl";
    var documentContext = TestUtils.getDocumentContextFromFile(filePath);
    // На текущий момент два DocumentLinkSupplier:
    // 1. Показ ссылок на документацию по рассчитанным диагностикам
    // 2. Показ URL из описаний методов и переменных
    // Поэтому перед вызовом получения списка ссылок нужно вызвать расчет диагностик.
    documentContext.getDiagnostics();

    // when
    var documentLinks = documentLinkProvider.getDocumentLinks(documentContext);

    // then
    assertThat(documentLinks).isNotEmpty();
    
    // Verify we have at least one link from descriptions (URL)
    assertThat(documentLinks)
      .anyMatch(link -> link.getTarget().equals("https://example.com/docs"));
  }
}