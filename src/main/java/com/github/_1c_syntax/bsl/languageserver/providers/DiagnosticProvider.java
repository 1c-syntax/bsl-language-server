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

import com.github._1c_syntax.bsl.languageserver.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.LanguageClientHolder;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DocumentDiagnosticReport;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.RelatedFullDocumentDiagnosticReport;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Провайдер для диагностических сообщений.
 * <p>
 * Отвечает за публикацию диагностик с использованием {@code textDocument/publishDiagnostics}
 * и предоставление диагностик по запросу {@code textDocument/diagnostic}.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_publishDiagnostics">PublishDiagnostics Notification specification</a>
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_diagnostic">Diagnostic Pull Request specification</a>
 */
@Component
@RequiredArgsConstructor
public final class DiagnosticProvider {

  public static final String SOURCE = "bsl-language-server";

  private final LanguageClientHolder clientHolder;
  private final ClientCapabilitiesHolder clientCapabilitiesHolder;

  /**
   * Вычислить и опубликовать диагностики для документа.
   * <p>
   * Если клиент поддерживает pull-модель диагностик, публикация не выполняется.
   *
   * @param documentContext Контекст документа
   */
  public void computeAndPublishDiagnostics(DocumentContext documentContext) {
    computeAndPublishDiagnostics(documentContext, false);
  }

  /**
   * Вычислить и опубликовать диагностики для документа.
   *
   * @param documentContext Контекст документа
   * @param force Принудительная публикация диагностик (игнорирует поддержку pull-модели клиентом)
   */
  public void computeAndPublishDiagnostics(DocumentContext documentContext, boolean force) {
    if (!force && clientSupportsPullDiagnostics()) {
      return;
    }
    publishDiagnostics(documentContext, documentContext::getDiagnostics);
  }

  /**
   * Получить диагностики для документа (pull-модель).
   *
   * @param documentContext Контекст документа
   * @return Отчет с диагностиками
   */
  public DocumentDiagnosticReport getDiagnosticReport(DocumentContext documentContext) {
    if (documentContext == null) {
      return new DocumentDiagnosticReport(new RelatedFullDocumentDiagnosticReport(Collections.emptyList()));
    }

    var diagnostics = documentContext.getDiagnostics();
    var report = new RelatedFullDocumentDiagnosticReport(diagnostics);
    return new DocumentDiagnosticReport(report);
  }

  /**
   * Опубликовать пустой список диагностик для документа.
   *
   * @param documentContext Контекст документа
   */
  public void publishEmptyDiagnosticList(DocumentContext documentContext) {
    publishDiagnostics(documentContext, Collections::emptyList);
  }

  /**
   * Проверить, поддерживает ли клиент pull-модель диагностик.
   *
   * @return true, если клиент поддерживает pull-модель диагностик
   */
  private boolean clientSupportsPullDiagnostics() {
    return clientCapabilitiesHolder.getCapabilities()
      .map(capabilities -> capabilities.getTextDocument())
      .map(textDocument -> textDocument.getDiagnostic())
      .isPresent();
  }

  private void publishDiagnostics(DocumentContext documentContext, Supplier<List<Diagnostic>> diagnostics) {
    clientHolder.execIfConnected(languageClient ->
      languageClient.publishDiagnostics(
        new PublishDiagnosticsParams(
          documentContext.getUri().toString(),
          diagnostics.get(),
          documentContext.getVersion()
        )
      )
    );
  }

}
