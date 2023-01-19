/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import javax.annotation.PostConstruct;
import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext
class DiagnosticDescriptionDocumentLinkSupplierTest {

  @Autowired
  private LanguageServerConfiguration configuration;

  @Autowired
  private DiagnosticDescriptionDocumentLinkSupplier supplier;

  private static final String SITE_URL = "https://1c-syntax.github.io/bsl-language-server/";
  private static final String SITE_EN_URL = "https://1c-syntax.github.io/bsl-language-server/en/";
  private static final String DIAGNOSTIC_CODE = "CanonicalSpellingKeywords";

  @PostConstruct
  void init() {
    configuration.reset();
    configuration.getDocumentLinkOptions().setShowDiagnosticDescription(true);
  }

  @Test
  void testGetDocumentLinks() {
    // given
    var documentContext = getDocumentContext();

    // when
    var documentLinks = supplier.getDocumentLinks(documentContext);

    // then
    assertThat(documentLinks)
      .isNotEmpty()
      .hasSizeGreaterThanOrEqualTo(6)
      .allMatch(documentLink -> documentLink.getTarget()
        .startsWith(SITE_URL))
      .filteredOn(documentLink -> !documentLink.getTarget().endsWith(DIAGNOSTIC_CODE))
      .hasSize(2);
  }

  @Test
  void testDisabledSupplier() {
    // given
    var documentContext = getDocumentContext();
    configuration.getDocumentLinkOptions().setShowDiagnosticDescription(false);

    // when
    var documentLinks = supplier.getDocumentLinks(documentContext);

    // then
    assertThat(documentLinks).isEmpty();
  }

  @Test
  void testGetDocumentLinksEn() {
    // given
    var configurationFile = new File("./src/test/resources/.bsl-language-server-only-en-param.json");
    configuration.update(configurationFile);
    configuration.getDocumentLinkOptions().setShowDiagnosticDescription(true);

    var documentContext = getDocumentContext();

    // when
    var documentLinks = supplier.getDocumentLinks(documentContext);

    // then
    assertThat(documentLinks)
      .isNotEmpty()
      .hasSizeGreaterThanOrEqualTo(6)
      .allMatch(documentLink -> documentLink.getTarget()
        .startsWith(SITE_EN_URL))
      .filteredOn(documentLink -> !documentLink.getTarget().endsWith(DIAGNOSTIC_CODE))
      .hasSizeGreaterThanOrEqualTo(2);
  }

  @Test
  void testDevSite() {
    // given
    var documentContext = getDocumentContext();

    // when
    configuration.setUseDevSite(false);
    documentContext = getDocumentContext();
    var documentLinks = supplier.getDocumentLinks(documentContext);

    // then
    assertThat(documentLinks)
      .allMatch(documentLink -> !documentLink.getTarget().contains("/dev/"));

    // when
    configuration.setUseDevSite(true);
    documentContext = getDocumentContext();
    documentLinks = supplier.getDocumentLinks(documentContext);

    // then
    assertThat(documentLinks)
      .allMatch(documentLink -> documentLink.getTarget().contains("/dev/"));

    // when
    configuration.setUseDevSite(true);
    configuration.setLanguage(Language.EN);
    documentContext = getDocumentContext();
    documentLinks = supplier.getDocumentLinks(documentContext);

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
    documentContext = getDocumentContext();
    var documentLinks = supplier.getDocumentLinks(documentContext);

    // then
    assertThat(documentLinks)
      .allMatch(documentLink -> documentLink.getTooltip().contains("Документация"))
      .anyMatch(documentLink -> documentLink.getTooltip().contains(DIAGNOSTIC_CODE))
    ;

    // when
    configuration.setLanguage(Language.EN);
    documentContext = getDocumentContext();
    documentLinks = supplier.getDocumentLinks(documentContext);

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
    var documentLinks = supplier.getDocumentLinks(documentContext);

    // then
    assertThat(documentLinks)
      .allMatch(documentLink -> documentLink.getTarget().startsWith("https://1c-syntax"));

    // when
    configuration.setSiteRoot("https://fake");
    documentContext = getDocumentContext();
    documentLinks = supplier.getDocumentLinks(documentContext);

    // then
    assertThat(documentLinks)
      .allMatch(documentLink -> !documentLink.getTarget().startsWith("https://1c-syntax"))
      .allMatch(documentLink -> documentLink.getTarget().startsWith("https://fake"))
    ;
  }

  private DocumentContext getDocumentContext() {
    var filePath = "./src/test/resources/documentlink/diagnosticDescriptionDocumentLinkSupplier.bsl";
    var documentContext = TestUtils.getDocumentContextFromFile(filePath);
    documentContext.getDiagnostics();
    return documentContext;
  }
}