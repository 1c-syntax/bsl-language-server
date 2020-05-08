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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCode;
import org.eclipse.lsp4j.DocumentLink;

import java.util.ArrayList;
import java.util.List;

/**
 * Класс-провайдер для реализации формирования ссылки на страницу с информацией по диагностике
 */
public class DocumentLinkProvider {
  private final DiagnosticProvider diagnosticProvider;
  private static final String SITE_URL_RU = "https://1c-syntax.github.io/bsl-language-server/diagnostics/";
  private static final String SITE_URL_EN = "https://1c-syntax.github.io/bsl-language-server/en/diagnostics/";
  private final LanguageServerConfiguration configuration;

  public DocumentLinkProvider(LanguageServerConfiguration configuration, DiagnosticProvider diagnosticProvider) {
    this.diagnosticProvider = diagnosticProvider;
    this.configuration = configuration;
  }

  public List<DocumentLink> getDocumentLinks(DocumentContext documentContext) {
    List<DocumentLink> documentLinks = new ArrayList<>();
    var siteDiagnosticsUrl = configuration.getLanguage() == Language.EN ? SITE_URL_EN : SITE_URL_RU;
    diagnosticProvider.getComputedDiagnostics(documentContext)
      .forEach(diagnostic -> documentLinks.add(new DocumentLink(diagnostic.getRange(),
        siteDiagnosticsUrl + DiagnosticCode.getStringValue(diagnostic.getCode()))));
    return documentLinks;
  }
}
