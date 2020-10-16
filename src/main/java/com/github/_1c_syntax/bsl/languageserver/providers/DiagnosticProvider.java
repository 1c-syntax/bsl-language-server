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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.springframework.stereotype.Component;

import javax.annotation.CheckForNull;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public final class DiagnosticProvider implements LanguageClientAware {

  public static final String SOURCE = "bsl-language-server";

  @CheckForNull
  private LanguageClient client;

  public void computeAndPublishDiagnostics(DocumentContext documentContext) {
    publishDiagnostics(documentContext, documentContext::getDiagnostics);
  }

  public void publishEmptyDiagnosticList(DocumentContext documentContext) {
    publishDiagnostics(documentContext, Collections::emptyList);
  }

  private void publishDiagnostics(DocumentContext documentContext, Supplier<List<Diagnostic>> diagnostics) {
    if (client == null) {
      return;
    }

    client.publishDiagnostics(
      new PublishDiagnosticsParams(documentContext.getUri().toString(), diagnostics.get())
    );
  }

  @Override
  public void connect(LanguageClient client) {
    this.client = client;
  }

}
