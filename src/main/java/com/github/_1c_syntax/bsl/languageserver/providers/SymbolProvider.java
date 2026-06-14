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

import com.github._1c_syntax.bsl.languageserver.LanguageClientHolder;
import com.github._1c_syntax.bsl.languageserver.types.index.Entry;
import com.github._1c_syntax.bsl.languageserver.types.index.WorkspaceSymbolIndex;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Провайдер для поиска символов в рабочей области.
 * <p>
 * Обрабатывает запросы {@code workspace/symbol}, делегируя поиск инкрементальному
 * {@link WorkspaceSymbolIndex}: индекс хранит уже подготовленные записи символов, поэтому провайдер
 * лишь маппит записи в {@link WorkspaceSymbol} без обхода всех документов.
 * <p>
 * Быстрая древесная выдача ({@link WorkspaceSymbolIndex#search(String, CancelChecker)}) отдаётся
 * мгновенно. Если клиент поддерживает частичные результаты (прислал
 * {@link WorkspaceSymbolParams#getPartialResultToken()}), нижнеранжированный «грязный» fuzzy-хвост
 * (подстрока внутри слова и подпоследовательность вразброс,
 * {@link WorkspaceSymbolIndex#searchFuzzyTail(String, Set, CancelChecker)}) досылается потоково через
 * {@code $/progress}, а синхронный ответ возвращается ПУСТЫМ, чтобы не дублировать уже отправленные
 * прогрессом чанки. Без токена ответом служит только древесная выдача — медленный скан НЕ запускается.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#workspace_symbol">Workspace Symbols Request specification</a>
 */
@Component
@RequiredArgsConstructor
public class SymbolProvider {

  /**
   * Размер чанка fuzzy-хвоста, досылаемого одним уведомлением {@code $/progress}.
   */
  private static final int TAIL_BATCH_SIZE = 200;

  private final WorkspaceSymbolIndex workspaceSymbolIndex;
  private final LanguageClientHolder clientHolder;

  /**
   * Выполняет поиск символов рабочей области по запросу {@code workspace/symbol} с поддержкой отмены
   * и потоковой выдачи частичных результатов.
   * <p>
   * Сначала из {@link WorkspaceSymbolIndex#search(String, CancelChecker)} берётся быстрая древесная
   * выдача (точное совпадение, префикс полного имени, многословное camel-hump совпадение, префикс
   * начала слова), ранжированная по релевантности. Если клиент НЕ прислал
   * {@link WorkspaceSymbolParams#getPartialResultToken()}, либо запрос пуст, либо клиент не подключён,
   * эта выдача возвращается синхронным ответом, а медленный fuzzy-скан не выполняется (поведение без
   * изменений).
   * <p>
   * Если токен есть (и запрос непуст, и клиент подключён), включается потоковая выдача: быстрый чанк
   * уходит {@code $/progress} ПЕРВЫМ (порядок прибытия чанков сохраняет ранжирование — клиент
   * дописывает их в порядке прихода и не пересортировывает), затем
   * {@link WorkspaceSymbolIndex#searchFuzzyTail(String, Set, CancelChecker)} (с быстрой выдачей в
   * качестве exclude) досылается чанками по {@link #TAIL_BATCH_SIZE}. Синхронный ответ при этом ПУСТ:
   * клиент конкатенирует прогресс-чанки, и повторная отдача того же набора в ответе привела бы к
   * дублям. Отмена проверяется индексом периодически и между чанками; при отмене бросается
   * {@link java.util.concurrent.CancellationException}.
   *
   * @param params        Параметры запроса {@code workspace/symbol}, в т.ч. строка запроса и токен
   *                      частичного результата
   * @param cancelChecker Проверяющий отмену запроса
   * @return Полный список при древесном пути; ПУСТОЙ список при потоковой выдаче (символы ушли в
   *         {@code $/progress})
   */
  public List<? extends WorkspaceSymbol> getSymbols(WorkspaceSymbolParams params, CancelChecker cancelChecker) {
    var queryString = Optional.ofNullable(params.getQuery())
      .orElse("");

    var fast = workspaceSymbolIndex.search(queryString, cancelChecker);
    var fastSymbols = fast.stream()
      .map(SymbolProvider::createWorkspaceSymbol)
      .toList();

    var token = params.getPartialResultToken();
    if (token == null || queryString.isEmpty() || !clientHolder.isConnected()) {
      // Клиент не поддерживает частичные результаты (нет токена), либо пустой запрос/нет клиента:
      // отдаём только быструю древесную выдачу, медленный fuzzy-скан не запускаем.
      return fastSymbols;
    }

    // Быстрый чанк уходит первым: ранг сохраняется порядком прибытия прогресс-чанков.
    streamChunk(token, fastSymbols);

    Set<Entry> fastSet = Collections.newSetFromMap(new IdentityHashMap<>());
    fastSet.addAll(fast);
    var tail = workspaceSymbolIndex.searchFuzzyTail(queryString, fastSet, cancelChecker);

    for (var from = 0; from < tail.size(); from += TAIL_BATCH_SIZE) {
      cancelChecker.checkCanceled();
      var batch = tail.subList(from, Math.min(from + TAIL_BATCH_SIZE, tail.size())).stream()
        .map(SymbolProvider::createWorkspaceSymbol)
        .toList();
      streamChunk(token, batch);
    }

    // Клиент прислал partialResultToken, поэтому конкатенирует чанки $/progress. Повторная отдача
    // всего набора в синхронном ответе привела бы к дублям, поэтому ответ пуст.
    return List.of();
  }

  /**
   * Отправить чанк символов клиенту как частичный результат через {@code $/progress}.
   * <p>
   * Пустые чанки пропускаются. Значением прогресса служит список символов
   * ({@code Either.forRight(chunk)}); уведомление уходит только подключённому клиенту.
   *
   * @param token токен частичного результата из параметров запроса
   * @param chunk чанк символов рабочей области для отправки
   */
  private void streamChunk(Either<String, Integer> token, List<? extends WorkspaceSymbol> chunk) {
    if (chunk.isEmpty()) {
      return;
    }
    var progressParams = new ProgressParams(token, Either.forRight((Object) chunk));
    clientHolder.execIfConnected(languageClient -> languageClient.notifyProgress(progressParams));
  }

  /**
   * Строит {@link WorkspaceSymbol} из записи индекса.
   * <p>
   * Имя контейнера и теги уже вычислены на момент индексации, поэтому повторный обход дерева
   * символов не требуется. Пустое имя контейнера трактуется как «контейнер отсутствует».
   *
   * @param entry запись индекса символов рабочей области
   * @return заполненный символ рабочей области
   */
  private static WorkspaceSymbol createWorkspaceSymbol(Entry entry) {
    var location = new Location(entry.uri().toString(), entry.range());

    var workspaceSymbol = new WorkspaceSymbol();
    workspaceSymbol.setName(entry.name());
    workspaceSymbol.setKind(entry.kind());
    workspaceSymbol.setLocation(Either.forLeft(location));
    workspaceSymbol.setTags(entry.tags());
    if (!entry.containerName().isEmpty()) {
      workspaceSymbol.setContainerName(entry.containerName());
    }

    return workspaceSymbol;
  }
}
