/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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
import com.github._1c_syntax.bsl.languageserver.inlayhints.InlayHintSupplier;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.InlayHintWorkspaceCapabilities;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.services.LanguageClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class InlayHintProvider {

  private final Collection<InlayHintSupplier> suppliers;

  private final ClientCapabilitiesHolder clientCapabilitiesHolder;
  private final LanguageClientHolder clientHolder;

  public List<InlayHint> getInlayHint(DocumentContext documentContext, InlayHintParams params) {
    return suppliers.stream()
      .map(supplier -> supplier.getInlayHints(documentContext, params))
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
  }

  public void refreshInlayHints() {
    boolean refreshSupport = clientCapabilitiesHolder.getCapabilities()
      .map(ClientCapabilities::getWorkspace)
      .map(WorkspaceClientCapabilities::getInlayHint)
      .map(InlayHintWorkspaceCapabilities::getRefreshSupport)
      .orElse(Boolean.FALSE);

    if (refreshSupport) {
      clientHolder.execIfConnected(LanguageClient::refreshInlayHints);
    }
  }
}
