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

import com.github._1c_syntax.bsl.languageserver.lsp.client.LanguageClientHolder;
import com.github._1c_syntax.bsl.languageserver.configuration.GlobalLanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.types.index.WorkspaceSymbolIndex;
import com.github._1c_syntax.bsl.languageserver.utils.FuzzyMatcher;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.types.MDOType;
import com.github._1c_syntax.bsl.types.MdoReference;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SymbolProviderScriptVariantTest {

  private static final org.eclipse.lsp4j.jsonrpc.CancelChecker NO_CANCEL = () -> {
    // no-op: проверка отмены не требуется в тесте поиска
  };

  private static Stream<Arguments> scriptVariantCases() {
    return Stream.of(
      Arguments.of(Language.RU, "ОбщийМодуль.ПервыйОбщийМодуль"),
      Arguments.of(Language.EN, "CommonModule.ПервыйОбщийМодуль")
    );
  }

  @ParameterizedTest
  @MethodSource("scriptVariantCases")
  void getSymbolsUsesScriptVariantFromConfiguration(Language scriptVariantLanguage, String expectedContainerName) {

    // given
    var mdoReference = MdoReference.create(MDOType.COMMON_MODULE, "ПервыйОбщийМодуль");
    var mdObject = mock(MD.class);
    when(mdObject.getMdoReference()).thenReturn(mdoReference);

    var documentUri = Absolute.uri(URI.create("file:///FirstCommonModule/Module.bsl"));
    var documentContext = mock(DocumentContext.class);
    when(documentContext.getUri()).thenReturn(documentUri);
    when(documentContext.getScriptVariantLanguage()).thenReturn(scriptVariantLanguage);
    when(documentContext.getMdObject()).thenReturn(Optional.of(mdObject));

    var symbol = mock(SourceDefinedSymbol.class);
    when(symbol.getName()).thenReturn("НеУстаревшаяПроцедура");
    when(symbol.getSymbolKind()).thenReturn(SymbolKind.Method);
    when(symbol.getRange()).thenReturn(Ranges.create(0, 0, 0, 0));
    when(symbol.getTags()).thenReturn(List.of());
    when(symbol.getOwner()).thenReturn(documentContext);

    var symbolTree = mock(SymbolTree.class);
    when(symbolTree.getChildrenFlat()).thenReturn(List.of(symbol));
    when(documentContext.getSymbolTree()).thenReturn(symbolTree);

    // имя контейнера вычисляется индексом на момент индексации в варианте языка проекта
    var index = new WorkspaceSymbolIndex(new FuzzyMatcher());
    index.handleContentChanged(new DocumentContextContentChangedEvent(documentContext));

    // запрос без partialResultToken: клиент не используется, древесная выдача отдаётся синхронно
    var symbolProvider = new SymbolProvider(
      index,
      mock(LanguageClientHolder.class),
      new GlobalLanguageServerConfiguration()
    );
    var params = new WorkspaceSymbolParams("НеУстаревшаяПроцедура");

    // when
    var symbols = symbolProvider.getSymbols(params, NO_CANCEL);

    // then
    assertThat(symbols)
      .hasSize(1)
      .first()
      .extracting(WorkspaceSymbol::getContainerName)
      .isEqualTo(expectedContainerName);
  }
}
