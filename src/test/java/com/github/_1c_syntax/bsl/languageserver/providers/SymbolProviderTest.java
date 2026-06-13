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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.utils.Absolute;
import lombok.SneakyThrows;
import org.eclipse.lsp4j.Position;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
class SymbolProviderTest {

  private static final CancelChecker NO_CANCEL = () -> {
    // no-op: проверка отмены не требуется в тестах поиска
  };

  /**
   * Ожидаемый лимит выдачи {@code workspace/symbol}; зеркалит {@code SymbolProvider.MAX_RESULTS}.
   */
  private static final int MAX_RESULTS = 1000;

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
  void getSymbolsQueryStringAllSymbols() {

    // given
    var params = new WorkspaceSymbolParams(".*");

    // when
    var symbols = symbolProvider.getSymbols(params, NO_CANCEL);

    // then
    assertThat(symbols)
      .hasSizeGreaterThan(0)
    ;
  }

  @Test
  void getSymbolsQueryStringErrorRegex() {

    // given
    // Запрос «Метод(» невалиден как регулярное выражение (незакрытая группа),
    // поэтому обрабатывается как буквальная подстрока. В фикстурах нет символов,
    // имя которых содержит литерал «Метод(», поэтому ожидаем пустой результат,
    // но без исключения и без отбрасывания валидных совпадений.
    var params = new WorkspaceSymbolParams("Метод(");

    // when
    var symbols = symbolProvider.getSymbols(params, NO_CANCEL);

    // then
    assertThat(symbols).isEmpty();
  }

  @Test
  void getSymbolsQueryWithRegexSpecialCharsFallsBackToLiteralMatch() {

    // given
    // Имя символа содержит литерал «(», а сам запрос невалиден как регулярное выражение.
    // До исправления такой запрос приводил к PatternSyntaxException и пустому результату.
    var symbolName = "Метод(Параметр";
    var symbolUri = Absolute.uri("file:///module.bsl");
    var symbol = mockMethodSymbol(symbolName);

    var symbolTree = mock(SymbolTree.class);
    when(symbolTree.getChildrenFlat()).thenReturn(List.of(symbol));

    var documentContext = mock(DocumentContext.class);
    when(documentContext.getUri()).thenReturn(symbolUri);
    when(documentContext.getSymbolTree()).thenReturn(symbolTree);
    when(documentContext.getMdObject()).thenReturn(Optional.empty());
    when(symbol.getOwner()).thenReturn(documentContext);

    var serverContext = mock(ServerContext.class);
    when(serverContext.getDocuments()).thenReturn(Map.of(symbolUri, documentContext));

    var serverContextProvider = mock(ServerContextProvider.class);
    when(serverContextProvider.getAllContexts()).thenReturn(Map.of(symbolUri, serverContext));

    var provider = new SymbolProvider(serverContextProvider);
    var params = new WorkspaceSymbolParams("Метод(");

    // when
    var symbols = provider.getSymbols(params, NO_CANCEL);

    // then
    assertThat(symbols)
      .hasSize(1)
      .anyMatch(symbolInformation -> symbolInformation.getName().equals(symbolName));
  }

  @Test
  void getSymbolsFuzzySubsequenceMatch() {

    // given
    // Запрос «ГСП» не является непрерывной подстрокой имени «ГлобальнаяСервернаяПроцедура»,
    // поэтому regex-сопоставление его не находит. Однако символы Г, С, П встречаются
    // в имени в том же порядке, поэтому fuzzy-сопоставление по подпоследовательности
    // должно вернуть символ.
    var params = new WorkspaceSymbolParams("ГСП");

    // when
    var symbols = symbolProvider.getSymbols(params, NO_CANCEL);

    // then
    assertThat(symbols)
      .anyMatch(symbolInformation ->
        symbolInformation.getName().equals("ГлобальнаяСервернаяПроцедура")
          && symbolInformation.getKind() == SymbolKind.Method
      );
  }

  @Test
  void getSymbolsFuzzySubsequenceCaseInsensitive() {

    // given
    // Регистр символов запроса не должен влиять на сопоставление по подпоследовательности
    // (кириллица приводится к нижнему регистру).
    var params = new WorkspaceSymbolParams("гсп");

    // when
    var symbols = symbolProvider.getSymbols(params, NO_CANCEL);

    // then
    assertThat(symbols)
      .anyMatch(symbolInformation ->
        symbolInformation.getName().equals("ГлобальнаяСервернаяПроцедура")
          && symbolInformation.getKind() == SymbolKind.Method
      );
  }

  @Test
  void getSymbolsLimitsResultSizeForShortQuery() {

    // given
    // Запрос из одной буквы «a» по подпоследовательности совпадает с каждым символом, чьё имя
    // содержит «a». Подаём заведомо больше символов, чем лимит выдачи, и ожидаем, что результат
    // усечён ровно до лимита (короткое замыкание стрима до сборки).
    var matchingSymbolsCount = MAX_RESULTS + 100;
    var symbolUri = Absolute.uri("file:///module.bsl");

    var symbols = IntStream.range(0, matchingSymbolsCount)
      .mapToObj(index -> mockMethodSymbol("aMethod" + index))
      .collect(Collectors.toList());

    var symbolTree = mock(SymbolTree.class);
    when(symbolTree.getChildrenFlat()).thenReturn(symbols);

    var documentContext = mock(DocumentContext.class);
    when(documentContext.getUri()).thenReturn(symbolUri);
    when(documentContext.getSymbolTree()).thenReturn(symbolTree);
    when(documentContext.getMdObject()).thenReturn(Optional.empty());
    symbols.forEach(symbol -> when(symbol.getOwner()).thenReturn(documentContext));

    var serverContext = mock(ServerContext.class);
    when(serverContext.getDocuments()).thenReturn(Map.of(symbolUri, documentContext));

    var serverContextProvider = mock(ServerContextProvider.class);
    when(serverContextProvider.getAllContexts()).thenReturn(Map.of(symbolUri, serverContext));

    var provider = new SymbolProvider(serverContextProvider);
    var params = new WorkspaceSymbolParams("a");

    // when
    var result = provider.getSymbols(params, NO_CANCEL);

    // then
    assertThat(result).hasSize(MAX_RESULTS);
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

  /**
   * Создаёт mock символа-метода с заданным именем для проверки сопоставления имён.
   *
   * @param name имя символа, возвращаемое методом {@link SourceDefinedSymbol#getName()}
   * @return настроенный mock {@link SourceDefinedSymbol} с типом {@link SymbolKind#Method}
   */
  private static SourceDefinedSymbol mockMethodSymbol(String name) {
    var symbol = mock(SourceDefinedSymbol.class);
    when(symbol.getName()).thenReturn(name);
    when(symbol.getSymbolKind()).thenReturn(SymbolKind.Method);
    when(symbol.getRange()).thenReturn(new Range(new Position(0, 0), new Position(0, 0)));
    when(symbol.getTags()).thenReturn(List.of());
    return symbol;
  }

}