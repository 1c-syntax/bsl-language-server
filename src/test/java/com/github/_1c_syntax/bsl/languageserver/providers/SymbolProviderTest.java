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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.utils.Absolute;
import lombok.SneakyThrows;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.SymbolTag;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.nio.file.Paths;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SymbolProviderTest {

  @Autowired
  private ServerContext context;
  @Autowired
  private SymbolProvider symbolProvider;

  @BeforeEach
  void before() {
    var configurationRoot = Absolute.path(PATH_TO_METADATA);
    context.setConfigurationRoot(configurationRoot);
    context.populateContext();
  }

  @Test
  void getSymbols() {

    // given
    var params = new WorkspaceSymbolParams();

    // when
    var symbols = symbolProvider.getSymbols(params);

    // then
    assertThat(symbols)
      .hasSizeGreaterThan(0)
      .anyMatch(symbolInformation ->
        symbolInformation.getName().equals("НеУстаревшаяПроцедура")
          && uriContains(symbolInformation, "ПервыйОбщийМодуль")
          && symbolInformation.getKind() == SymbolKind.Method
          && !symbolInformation.getTags().contains(SymbolTag.Deprecated)
      )
      .anyMatch(symbolInformation ->
        symbolInformation.getName().equals("НеУстаревшаяПроцедура")
          && uriContains(symbolInformation, "РегистрСведений1")
          && symbolInformation.getKind() == SymbolKind.Method
          && !symbolInformation.getTags().contains(SymbolTag.Deprecated)
      )
      .anyMatch(symbolInformation ->
        symbolInformation.getName().equals("УстаревшаяПроцедура")
          && uriContains(symbolInformation, "ПервыйОбщийМодуль")
          && symbolInformation.getKind() == SymbolKind.Method
          && symbolInformation.getTags().contains(SymbolTag.Deprecated)
      )
      .anyMatch(symbolInformation ->
        symbolInformation.getName().equals("ВалютаУчета")
          && uriContains(symbolInformation, "ManagedApplicationModule")
          && symbolInformation.getKind() == SymbolKind.Variable
          && !symbolInformation.getTags().contains(SymbolTag.Deprecated)
      )
    ;
  }

  @SneakyThrows
  private boolean uriContains(WorkspaceSymbol workspaceSymbol, String name) {
    return Paths.get(new URI(workspaceSymbol.getLocation().getLeft().getUri())).toString().contains(name);
  }

  @Test
  void getSymbolsQueryString() {

    // given
    var params = new WorkspaceSymbolParams("НеУстар");

    // when
    var symbols = symbolProvider.getSymbols(params);

    // then
    assertThat(symbols)
      .hasSize(4)
      .anyMatch(symbolInformation ->
        symbolInformation.getName().contains("НеУстаревшаяПроцедура")
          && symbolInformation.getKind() == SymbolKind.Method
      )
    ;
  }

  @Test
  void getSymbolsQueryStringAllSymbols() {

    // given
    var params = new WorkspaceSymbolParams(".*");

    // when
    var symbols = symbolProvider.getSymbols(params);

    // then
    assertThat(symbols)
      .hasSizeGreaterThan(0)
    ;
  }

  @Test
  void getSymbolsQueryStringErrorRegex() {

    // given
    var params = new WorkspaceSymbolParams("\\");

    // when
    var symbols = symbolProvider.getSymbols(params);

    // then
    assertThat(symbols).isEmpty();
  }


}