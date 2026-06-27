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

import com.github._1c_syntax.bsl.languageserver.client.LanguageClientHolder;
import com.github._1c_syntax.bsl.languageserver.configuration.GlobalLanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.types.index.Entry;
import com.github._1c_syntax.bsl.languageserver.types.index.WorkspaceSymbolIndex;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Юнит-тесты потоковой выдачи fuzzy-хвоста через partial result progress в {@link SymbolProvider}.
 * <p>
 * SUT не зависит от поднятого Spring-контекста: индекс, держатель клиента и сам клиент мокаются,
 * поэтому проверяется чистая логика диспетчеризации (что уходит в {@code $/progress}, а что — в
 * синхронный ответ) без {@code @SpringBootTest}.
 */
class SymbolProviderStreamingTest {

  private static final CancelChecker NO_CANCEL = () -> {
    // no-op: проверка отмены не требуется в тестах диспетчеризации
  };

  private WorkspaceSymbolIndex index;
  private LanguageClientHolder clientHolder;
  private LanguageClient client;
  private GlobalLanguageServerConfiguration globalConfiguration;
  private SymbolProvider provider;

  private final Entry fastEntry = entry("ПровестиДокумент");
  private final Entry tailEntry = entry("КонецМенюДокумент");

  @BeforeEach
  void before() {
    index = mock(WorkspaceSymbolIndex.class);
    clientHolder = mock(LanguageClientHolder.class);
    client = mock(LanguageClient.class);
    globalConfiguration = new GlobalLanguageServerConfiguration();
    provider = new SymbolProvider(index, clientHolder, globalConfiguration);
  }

  @Test
  void streamsFastChunkFirstAndFuzzyTailLaterReturningEmptyWhenTokenPresent() {

    // given — клиент подключён и прислал partialResultToken
    when(index.search(eq("док"), any())).thenReturn(List.of(fastEntry));
    when(index.searchFuzzyTail(eq("док"), anySet(), any())).thenReturn(List.of(tailEntry));
    connectClient();
    var params = new WorkspaceSymbolParams("док");
    params.setPartialResultToken(Either.forLeft("token-1"));

    // when
    var result = provider.getSymbols(params, NO_CANCEL);

    // then — синхронный ответ пуст: клиент конкатенирует $/progress-чанки
    assertThat(result).isEmpty();

    // then — первый чанк несёт быстрые (trie) символы, более поздний — fuzzy-хвост
    var captor = ArgumentCaptor.forClass(ProgressParams.class);
    verify(client, atLeastOnce()).notifyProgress(captor.capture());
    var chunks = captor.getAllValues();
    assertThat(symbolNames(chunks.get(0))).containsExactly("ПровестиДокумент");
    assertThat(indexOfChunkWith(chunks, "КонецМенюДокумент")).isPositive();

    // then — searchFuzzyTail вызван с быстрыми записями в качестве exclude
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Set<Entry>> excludeCaptor = ArgumentCaptor.forClass(Set.class);
    verify(index).searchFuzzyTail(eq("док"), excludeCaptor.capture(), any());
    assertThat(excludeCaptor.getValue()).contains(fastEntry);
  }

  @Test
  void returnsFastSymbolsWithoutScanningOrStreamingWhenNoToken() {

    // given — клиент подключён, но partialResultToken не прислан
    when(index.search(eq("док"), any())).thenReturn(List.of(fastEntry));
    connectClient();
    var params = new WorkspaceSymbolParams("док");

    // when
    var result = provider.getSymbols(params, NO_CANCEL);

    // then — возвращаются быстрые символы, прогресс не шлётся, fuzzy-скан не выполняется
    assertThat(result)
      .extracting(WorkspaceSymbol::getName)
      .containsExactly("ПровестиДокумент");
    verify(client, never()).notifyProgress(any());
    verify(index, never()).searchFuzzyTail(any(), anySet(), any());
  }

  @Test
  void doesNotStreamOrScanForEmptyQuery() {

    // given — пустой запрос даже при наличии токена не запускает fuzzy-хвост
    when(index.search(eq(""), any())).thenReturn(List.of(fastEntry));
    connectClient();
    var params = new WorkspaceSymbolParams("");
    params.setPartialResultToken(Either.forLeft("token-1"));

    // when
    var result = provider.getSymbols(params, NO_CANCEL);

    // then — возвращается trie-выдача, без прогресса и без fuzzy-скана
    assertThat(result)
      .extracting(WorkspaceSymbol::getName)
      .containsExactly("ПровестиДокумент");
    verify(client, never()).notifyProgress(any());
    verify(index, never()).searchFuzzyTail(any(), anySet(), any());
  }

  @Test
  void streamsFastSetInBatchesCheckingCancellationWhenTokenPresent() {

    // given — быстрый набор крупнее размера чанка (200), клиент прислал partialResultToken
    var fastEntries = IntStream.range(0, 450)
      .mapToObj(i -> entry("Документ" + i))
      .toList();
    when(index.search(eq("док"), any())).thenReturn(fastEntries);
    when(index.searchFuzzyTail(eq("док"), anySet(), any())).thenReturn(List.of(tailEntry));
    connectClient();
    var params = new WorkspaceSymbolParams("док");
    params.setPartialResultToken(Either.forLeft("token-1"));
    var cancelChecks = new AtomicInteger();
    CancelChecker countingChecker = cancelChecks::incrementAndGet;

    // when
    var result = provider.getSymbols(params, countingChecker);

    // then — синхронный ответ пуст, быстрый набор ушёл несколькими прогресс-чанками (450 / 200 = 3)
    assertThat(result).isEmpty();
    var captor = ArgumentCaptor.forClass(ProgressParams.class);
    verify(client, atLeastOnce()).notifyProgress(captor.capture());
    var fastChunkCount = captor.getAllValues().stream()
      .filter(progress -> symbolNames(progress).stream().anyMatch(name -> name.startsWith("Документ")))
      .count();
    assertThat(fastChunkCount).isGreaterThan(1);

    // then — отмена проверялась перед чанками
    assertThat(cancelChecks.get()).isPositive();
  }

  @Test
  void stopsStreamingFastSetWhenCancelledBetweenBatches() {

    // given — отмена срабатывает между чанками быстрого набора
    var fastEntries = IntStream.range(0, 450)
      .mapToObj(i -> entry("Документ" + i))
      .toList();
    when(index.search(eq("док"), any())).thenReturn(fastEntries);
    connectClient();
    var params = new WorkspaceSymbolParams("док");
    params.setPartialResultToken(Either.forLeft("token-1"));
    var calls = new AtomicInteger();
    CancelChecker cancelAfterFirstBatch = () -> {
      if (calls.getAndIncrement() >= 1) {
        throw new CancellationException();
      }
    };

    // when / then
    assertThatThrownBy(() -> provider.getSymbols(params, cancelAfterFirstBatch))
      .isInstanceOf(CancellationException.class);
    // fuzzy-хвост не запрашивался: отмена прервала уже потоковую выдачу быстрого набора
    verify(index, never()).searchFuzzyTail(any(), anySet(), any());
  }

  @Test
  void appendsFuzzyTailSynchronouslyWhenNoTokenAndSyncFuzzySearchEnabled() {

    // given — нет токена, но включён синхронный fuzzy-поиск
    globalConfiguration.getWorkspaceSymbol().setSyncFuzzySearch(true);
    when(index.search(eq("док"), any())).thenReturn(List.of(fastEntry));
    when(index.searchFuzzyTail(eq("док"), anySet(), any())).thenReturn(List.of(tailEntry));
    connectClient();
    var params = new WorkspaceSymbolParams("док");

    // when
    var result = provider.getSymbols(params, NO_CANCEL);

    // then — ответ = быстрый набор ++ fuzzy-хвост, прогресс не шлётся
    assertThat(result)
      .extracting(WorkspaceSymbol::getName)
      .containsExactly("ПровестиДокумент", "КонецМенюДокумент");
    verify(index).searchFuzzyTail(eq("док"), anySet(), any());
    verify(client, never()).notifyProgress(any());
  }

  @Test
  void returnsFastSymbolsOnlyWhenNoTokenAndSyncFuzzySearchDisabled() {

    // given — нет токена, синхронный fuzzy-поиск по умолчанию выключен (false)
    when(index.search(eq("док"), any())).thenReturn(List.of(fastEntry));
    connectClient();
    var params = new WorkspaceSymbolParams("док");

    // when
    var result = provider.getSymbols(params, NO_CANCEL);

    // then — только быстрая выдача, fuzzy-скан не выполняется, прогресс не шлётся
    assertThat(result)
      .extracting(WorkspaceSymbol::getName)
      .containsExactly("ПровестиДокумент");
    verify(index, never()).searchFuzzyTail(any(), anySet(), any());
    verify(client, never()).notifyProgress(any());
  }

  @SuppressWarnings("unchecked")
  private void connectClient() {
    when(clientHolder.isConnected()).thenReturn(true);
    doAnswer(invocation -> {
      var consumer = (Consumer<LanguageClient>) invocation.getArgument(0);
      consumer.accept(client);
      return null;
    }).when(clientHolder).execIfConnected(any());
  }

  private static List<String> symbolNames(ProgressParams progress) {
    @SuppressWarnings("unchecked")
    var chunk = (List<WorkspaceSymbol>) progress.getValue().getRight();
    return chunk.stream().map(WorkspaceSymbol::getName).toList();
  }

  private static int indexOfChunkWith(List<ProgressParams> chunks, String name) {
    for (var i = 0; i < chunks.size(); i++) {
      if (symbolNames(chunks.get(i)).contains(name)) {
        return i;
      }
    }
    return -1;
  }

  private static Entry entry(String name) {
    return new Entry(
      Absolute.uri("file:///module.bsl"),
      name,
      name.toLowerCase(),
      SymbolKind.Method,
      new Range(),
      List.of(),
      ""
    );
  }
}
