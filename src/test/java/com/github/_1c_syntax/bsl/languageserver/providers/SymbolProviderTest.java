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

import com.github._1c_syntax.bsl.languageserver.lsp.LanguageClientHolder;
import com.github._1c_syntax.bsl.languageserver.configuration.GlobalLanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.types.index.Entry;
import com.github._1c_syntax.bsl.languageserver.types.index.WorkspaceSymbolIndex;
import com.github._1c_syntax.utils.Absolute;
import lombok.SneakyThrows;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.SymbolTag;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
class SymbolProviderTest {

  private static final CancelChecker NO_CANCEL = () -> {
    // no-op: проверка отмены не требуется в тестах поиска
  };

  @Autowired
  private ServerContext context;
  @Autowired
  private ServerContextProvider serverContextProvider;
  @Autowired
  private SymbolProvider symbolProvider;

  @BeforeEach
  void before() {
    // Register workspace for metadata resources
    var metadataPath = new File(PATH_TO_METADATA).getAbsoluteFile();
    var workspaceFolder = new WorkspaceFolder(metadataPath.toURI().toString(), "test-workspace");
    var workspaceContext = serverContextProvider.addWorkspace(workspaceFolder);
    workspaceContext.populateContext();
  }

  @Test
  void getSymbols() {

    // given
    var params = new WorkspaceSymbolParams();

    // when
    var symbols = symbolProvider.getSymbols(params, NO_CANCEL);

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

  @Test
  void getSymbolsContainerName() {

    // given
    // конфигурация фикстуры объявляет ScriptVariant=Russian,
    // поэтому представление ссылки на объект метаданных ожидается в русском варианте
    var params = new WorkspaceSymbolParams("НеУстаревшаяПроцедура");

    // when
    var symbols = symbolProvider.getSymbols(params, NO_CANCEL);

    // then
    assertThat(symbols)
      .anyMatch(workspaceSymbol ->
        uriContains(workspaceSymbol, "ПервыйОбщийМодуль")
          && "ОбщийМодуль.ПервыйОбщийМодуль".equals(workspaceSymbol.getContainerName())
      )
    ;
  }

  @SneakyThrows
  private boolean uriContains(WorkspaceSymbol workspaceSymbol, String name) {
    return Path.of(new URI(workspaceSymbol.getLocation().getLeft().getUri())).toString().contains(name);
  }

  @Test
  void getSymbolsQueryString() {

    // given
    var params = new WorkspaceSymbolParams("НеУстар");

    // when
    var symbols = symbolProvider.getSymbols(params, NO_CANCEL);

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
  void getSymbolsEmptyQueryReturnsAllSymbols() {

    // given
    // Пустой запрос трактуется как «все символы»: индекс возвращает всю выдачу без усечения.
    var params = new WorkspaceSymbolParams("");

    // when
    var symbols = symbolProvider.getSymbols(params, NO_CANCEL);

    // then
    assertThat(symbols)
      .hasSizeGreaterThan(0)
    ;
  }

  @Test
  void getSymbolsQueryStringNoMatch() {

    // given
    // Поиск ведётся буквальным префиксным сопоставлением по дереву (префикс полного имени
    // или начала CamelCase-слова), а не регулярным выражением. В фикстурах нет символов, чьё
    // имя или слово начинается с «Метод(», поэтому результат пуст — без исключения и без
    // ложных совпадений.
    var params = new WorkspaceSymbolParams("Метод(");

    // when
    var symbols = symbolProvider.getSymbols(params, NO_CANCEL);

    // then
    assertThat(symbols).isEmpty();
  }

  @Test
  void getSymbolsMapsIndexEntryWithSpecialChars() {

    // given
    // Имя символа содержит литерал «(»; индекс сопоставляет его буквально, без regex.
    // Провайдер должен корректно отобразить запись индекса в WorkspaceSymbol.
    var symbolName = "Метод(Параметр";
    var symbolUri = Absolute.uri("file:///module.bsl");
    var entry = new Entry(
      symbolUri,
      symbolName,
      symbolName.toLowerCase(),
      SymbolKind.Method,
      new Range(),
      List.of(),
      ""
    );

    var index = mock(WorkspaceSymbolIndex.class);
    when(index.search(eq("Метод("), any())).thenReturn(List.of(entry));

    // без partialResultToken провайдер возвращает только древесную выдачу, клиент не нужен
    var provider = new SymbolProvider(
      index,
      mock(LanguageClientHolder.class),
      new GlobalLanguageServerConfiguration()
    );
    var params = new WorkspaceSymbolParams("Метод(");

    // when
    var symbols = provider.getSymbols(params, NO_CANCEL);

    // then
    assertThat(symbols)
      .hasSize(1)
      .anyMatch(symbolInformation -> symbolInformation.getName().equals(symbolName));
  }

  @Test
  void getSymbolsCancelledCheckerInterruptsSearch() {

    // given
    // Отменённый CancelChecker должен прервать обход документов исключением CancellationException.
    var params = new WorkspaceSymbolParams();
    CancelChecker cancelChecker = () -> {
      throw new CancellationException();
    };

    // when / then
    assertThatThrownBy(() -> symbolProvider.getSymbols(params, cancelChecker))
      .isInstanceOf(CancellationException.class);
  }

  @Test
  void getSymbolsNonCancelledCheckerReturnsFullResult() {

    // given
    // Не-отменённый CancelChecker не должен влиять на результат: возвращается полная выдача.
    var params = new WorkspaceSymbolParams();
    CancelChecker cancelChecker = () -> {
      // not cancelled
    };

    // when
    var symbols = symbolProvider.getSymbols(params, cancelChecker);

    // then
    assertThat(symbols)
      .hasSizeGreaterThan(0);
  }

}