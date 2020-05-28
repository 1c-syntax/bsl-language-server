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
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DocumentLink;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Класс-провайдер для реализации формирования ссылки на страницу с информацией по диагностике
 */
public class DocumentLinkProvider {
  private final DiagnosticProvider diagnosticProvider;
  private final LanguageServerConfiguration configuration;

  public DocumentLinkProvider(LanguageServerConfiguration configuration, DiagnosticProvider diagnosticProvider) {
    this.diagnosticProvider = diagnosticProvider;
    this.configuration = configuration;
  }

  public List<DocumentLink> getDocumentLinks(DocumentContext documentContext) {

    var linkOptions = configuration.getDocumentLinkOptions();
    var language = configuration.getLanguage();

    var siteRoot = linkOptions.getSiteRoot();
    var devSuffix = linkOptions.useDevSite() ? "/dev" : "";
    var languageSuffix = language == Language.EN ? "/en" : "";

    var siteDiagnosticsUrl = String.format(
      "%s%s%s/diagnostics/",
      siteRoot,
      devSuffix,
      languageSuffix
    );

    return diagnosticProvider.getComputedDiagnostics(documentContext).stream()
      .map((Diagnostic diagnostic) -> {
        var diagnosticCode = DiagnosticCode.getStringValue(diagnostic.getCode());

        return new DocumentLink(
          diagnostic.getRange(),
          siteDiagnosticsUrl + diagnosticCode,
          null,
          Resources.getResourceString(language, this.getClass(), "tooltip", diagnosticCode)
        );
      })
      .collect(Collectors.toList());
  }
}
