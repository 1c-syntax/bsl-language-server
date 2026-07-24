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

import org.eclipse.lsp4j.SemanticTokenTypes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticTokensLegendConfigurationTest {

  /**
   * Легенда обязана перечислять {@link SemanticTokenTypes#Event}: иначе индекс, который
   * {@link SemanticTokensHelper} ищет по имени типа токена через
   * {@code legend.getTokenTypes().indexOf(...)}, окажется {@code -1}, и токен обработчика
   * события молча потеряется при кодировании ответа {@code textDocument/semanticTokens}.
   */
  @Test
  void legendContainsEventTokenType() {
    var legend = new SemanticTokensLegendConfiguration().semanticTokensLegend();

    assertThat(legend.getTokenTypes()).contains(SemanticTokenTypes.Event);
  }
}
