/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
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
package com.github._1c_syntax.bsl.languageserver.semantictokens;

import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.SemanticTokensCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticTokensHelperCapabilitiesTest {

  @Test
  void multilineTokensSupportedOnlyWhenClientDeclaresIt() {
    assertThat(SemanticTokensHelper.supportsMultilineTokens(params(Boolean.TRUE))).isTrue();
    assertThat(SemanticTokensHelper.supportsMultilineTokens(params(Boolean.FALSE))).isFalse();
    assertThat(SemanticTokensHelper.supportsMultilineTokens(params(null))).isFalse();
    assertThat(SemanticTokensHelper.supportsMultilineTokens(new InitializeParams())).isFalse();
    assertThat(SemanticTokensHelper.supportsMultilineTokens(null)).isFalse();
  }

  private static InitializeParams params(Boolean multilineTokenSupport) {
    var semanticTokens = new SemanticTokensCapabilities();
    semanticTokens.setMultilineTokenSupport(multilineTokenSupport);
    var textDocument = new TextDocumentClientCapabilities();
    textDocument.setSemanticTokens(semanticTokens);
    var capabilities = new ClientCapabilities();
    capabilities.setTextDocument(textDocument);
    var params = new InitializeParams();
    params.setCapabilities(capabilities);
    return params;
  }
}
