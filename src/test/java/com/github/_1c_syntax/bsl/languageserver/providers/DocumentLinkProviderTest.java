/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DocumentLinkProviderTest {

  @Autowired
  private LanguageServerConfiguration configuration;

  @Autowired
  private DocumentLinkProvider documentLinkProvider;

  private static final String SITE_URL = "https://1c-syntax.github.io/bsl-language-server/";
  private static final String SITE_EN_URL = "https://1c-syntax.github.io/bsl-language-server/en/";
  private static final String DIAGNOSTIC_CODE = "CanonicalSpellingKeywords";

  @Test
  void testGetDocumentLinks() {
    // given
    var documentContext = getDocumentContext();

    // when
    var documentLinks = documentLinkProvider.getDocumentLinks(documentContext);

    // then
    assertThat(documentLinks)
      .isNotEmpty()
      .hasSize(6)
      .allMatch(documentLink -> documentLink.getTarget()
        .startsWith(SITE_URL))
      .filteredOn(documentLink -> !documentLink.getTarget().endsWith(DIAGNOSTIC_CODE))
      .hasSize(2);
  }

  @Test
  void testGetDocumentLinksEn() {

    // given
    var configurationFile = new File("./src/test/resources/.bsl-language-server-only-en-param.json");
    configuration.update(configurationFile);

    var documentContext = getDocumentContext();

    // when
    var documentLinks = documentLinkProvider.getDocumentLinks(documentContext);

    // then
    assertThat(documentLinks)
      .isNotEmpty()
      .hasSize(6)
      .allMatch(documentLink -> documentLink.getTarget()
        .startsWith(SITE_EN_URL))
      .filteredOn(documentLink -> !documentLink.getTarget().endsWith(DIAGNOSTIC_CODE))
      .hasSize(2);
  }

  @Test
  void testDevSite() {
    // given
    var documentLinkOptions = configuration.getDocumentLinkOptions();
    var documentContext = getDocumentContext();

    // when
    documentLinkOptions.setUseDevSite(false);
    var documentLinks = documentLinkProvider.getDocumentLinks(documentContext);

    // then
    assertThat(documentLinks)
      .allMatch(documentLink -> !documentLink.getTarget().contains("/dev/"));

    // when
    documentLinkOptions.setUseDevSite(true);
    documentLinks = documentLinkProvider.getDocumentLinks(documentContext);

    // then
    assertThat(documentLinks)
      .allMatch(documentLink -> documentLink.getTarget().contains("/dev/"));

    // when
    documentLinkOptions.setUseDevSite(true);
    configuration.setLanguage(Language.EN);
    documentLinks = documentLinkProvider.getDocumentLinks(documentContext);

    // then
    assertThat(documentLinks)
      .allMatch(documentLink -> documentLink.getTarget().contains("/dev/"))
      .allMatch(documentLink -> documentLink.getTarget().contains("/en/"));

  }

  @Test
  void testTooltip() {
    // given
    var documentContext = getDocumentContext();

    // when
    configuration.setLanguage(Language.RU);
    var documentLinks = documentLinkProvider.getDocumentLinks(documentContext);

    // then
    assertThat(documentLinks)
      .allMatch(documentLink -> documentLink.getTooltip().contains("Документация"))
      .anyMatch(documentLink -> documentLink.getTooltip().contains(DIAGNOSTIC_CODE))
    ;

    // when
    configuration.setLanguage(Language.EN);
    documentLinks = documentLinkProvider.getDocumentLinks(documentContext);

    // then
    assertThat(documentLinks)
      .allMatch(documentLink -> documentLink.getTooltip().contains("documentation"))
      .anyMatch(documentLink -> documentLink.getTooltip().contains(DIAGNOSTIC_CODE))
    ;
  }

  @Test
  void testSiteRoot() {
    var documentContext = getDocumentContext();

    // when
    var documentLinks = documentLinkProvider.getDocumentLinks(documentContext);

    // then
    assertThat(documentLinks)
      .allMatch(documentLink -> documentLink.getTarget().startsWith("https://1c-syntax"));

    // when
    configuration.getDocumentLinkOptions().setSiteRoot("https://fake");
    documentLinks = documentLinkProvider.getDocumentLinks(documentContext);

    // then
    assertThat(documentLinks)
      .allMatch(documentLink -> !documentLink.getTarget().startsWith("https://1c-syntax"))
      .allMatch(documentLink -> documentLink.getTarget().startsWith("https://fake"))
    ;
  }

  @NotNull
  private DocumentContext getDocumentContext() {
    var filePath = "./src/test/resources/providers/documentLinkProvider.bsl";
    var documentContext = TestUtils.getDocumentContextFromFile(filePath);
    documentContext.getDiagnostics();
    return documentContext;
  }
}