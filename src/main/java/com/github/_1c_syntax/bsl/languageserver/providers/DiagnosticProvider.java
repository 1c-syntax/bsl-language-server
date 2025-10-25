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

import com.github._1c_syntax.bsl.languageserver.LanguageClientHolder;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Провайдер для публикации диагностических сообщений.
 * <p>
 * Отвечает за публикацию диагностик с использованием {@code textDocument/publishDiagnostics}.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_publishDiagnostics">PublishDiagnostics Notification specification</a>
 */
@Component
@RequiredArgsConstructor
public final class DiagnosticProvider {

  public static final String SOURCE = "bsl-language-server";

  private final LanguageClientHolder clientHolder;

  public void computeAndPublishDiagnostics(DocumentContext documentContext) {
    publishDiagnostics(documentContext, documentContext::getDiagnostics);
  }

  public void publishEmptyDiagnosticList(DocumentContext documentContext) {
    publishDiagnostics(documentContext, Collections::emptyList);
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
