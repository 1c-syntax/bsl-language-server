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

import java.util.ArrayList;
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
 * {@link WorkspaceSymbolParams#getPartialResultToken()}), и быстрая выдача, и нижнеранжированный
 * «грязный» fuzzy-хвост (подстрока внутри слова и подпоследовательность вразброс,
 * {@link WorkspaceSymbolIndex#searchFuzzyTail(String, java.util.Collection, CancelChecker)}) досылаются
 * потоково чанками через {@code $/progress} (с проверкой отмены между чанками), а синхронный ответ
 * возвращается ПУСТЫМ, чтобы не дублировать уже отправленные прогрессом чанки.
 * <p>
 * Без токена частичных результатов поведение определяется булевым флагом
 * {@code workspaceSymbol.syncFuzzySearch}: при значении {@code true} к древесной выдаче синхронно
 * дописывается результат блокирующего fuzzy-скана; при значении {@code false} (по умолчанию) ответом
 * служит только древесная выдача — медленный скан НЕ запускается.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#workspace_symbol">Workspace Symbols Request specification</a>
 */
@Component
@RequiredArgsConstructor
public class SymbolProvider {

  /**
   * Размер чанка символов, досылаемого одним уведомлением {@code $/progress}.
   */
  private static final int STREAM_BATCH_SIZE = 200;

  private final WorkspaceSymbolIndex workspaceSymbolIndex;
  private final LanguageClientHolder clientHolder;
  private final GlobalLanguageServerConfiguration globalConfiguration;

  /**
   * Выполняет поиск символов рабочей области по запросу {@code workspace/symbol} с поддержкой отмены
   * и потоковой выдачи частичных результатов.
   * <p>
   * Сначала из {@link WorkspaceSymbolIndex#search(String, CancelChecker)} берётся быстрая древесная
   * выдача (точное совпадение, префикс полного имени, многословное camel-hump совпадение, префикс
   * начала слова), ранжированная по релевантности.
   * <p>
   * Если токен частичного результата есть (и запрос непуст, и клиент подключён), включается потоковая
   * выдача: и быстрый набор, и fuzzy-хвост
   * ({@link WorkspaceSymbolIndex#searchFuzzyTail(String, java.util.Collection, CancelChecker)} с быстрой
   * выдачей в качестве exclude) досылаются {@code $/progress}-чанками по {@link #STREAM_BATCH_SIZE}
   * через {@link #streamInBatches(Either, List, CancelChecker)}. Чтобы не пересортировывать выдачу,
   * быстрые чанки уходят ПЕРВЫМИ (клиент дописывает их в порядке прихода). Синхронный ответ при этом
   * ПУСТ: клиент конкатенирует прогресс-чанки, и повторная отдача того же набора в ответе привела бы к
   * дублям. Отмена проверяется индексом периодически и между чанками; при отмене бросается
   * {@link java.util.concurrent.CancellationException}.
   * <p>
   * Если токена нет (либо запрос пуст, либо клиент не подключён), потоковая выдача невозможна. Тогда
   * поведение определяется булевым флагом {@code workspaceSymbol.syncFuzzySearch}: при {@code true}
   * к древесной выдаче синхронно дописывается результат блокирующего fuzzy-скана; при {@code false}
   * (по умолчанию) возвращается только древесная выдача, медленный скан не выполняется.
   *
   * @param params        Параметры запроса {@code workspace/symbol}, в т.ч. строка запроса и токен
   *                      частичного результата
   * @param cancelChecker Проверяющий отмену запроса
   * @return Полный список при синхронном пути; ПУСТОЙ список при потоковой выдаче (символы ушли в
   *         {@code $/progress})
   */
  public List<? extends WorkspaceSymbol> getSymbols(WorkspaceSymbolParams params, CancelChecker cancelChecker) {
    var queryString = Optional.ofNullable(params.getQuery())
      .orElse("");

    var fast = workspaceSymbolIndex.search(queryString, cancelChecker);

    var token = params.getPartialResultToken();
    if (token != null && !queryString.isEmpty() && clientHolder.isConnected()) {
      // Быстрый набор уходит первым: ранг сохраняется порядком прибытия прогресс-чанков.
      streamInBatches(token, fast, cancelChecker);

      var tail = workspaceSymbolIndex.searchFuzzyTail(queryString, identitySetOf(fast), cancelChecker);
      streamInBatches(token, tail, cancelChecker);

      // Клиент прислал partialResultToken, поэтому конкатенирует чанки $/progress. Повторная отдача
      // всего набора в синхронном ответе привела бы к дублям, поэтому ответ пуст.
      return List.of();
    }

    if (globalConfiguration.getWorkspaceSymbol().isSyncFuzzySearch()
      && !queryString.isEmpty()) {
      // Клиент без потоковой выдачи: блокирующий fuzzy-скан дописывается в синхронный ответ.
      var tail = workspaceSymbolIndex.searchFuzzyTail(queryString, identitySetOf(fast), cancelChecker);
      var result = new ArrayList<WorkspaceSymbol>(fast.size() + tail.size());
      fast.forEach(entry -> result.add(createWorkspaceSymbol(entry)));
      tail.forEach(entry -> result.add(createWorkspaceSymbol(entry)));
      return result;
    }

    // syncFuzzySearch == false (по умолчанию): только древесная выдача, медленный fuzzy-скан не запускаем.
    return fast.stream()
      .map(SymbolProvider::createWorkspaceSymbol)
      .toList();
  }

  /**
   * Построить identity-множество записей для исключения из fuzzy-хвоста.
   * <p>
   * Сравнение по ссылке ({@link IdentityHashMap}) гарантирует, что из хвоста исключаются именно те
   * записи, что уже попали в быструю выдачу, без зависимости от {@code equals}/{@code hashCode}.
   *
   * @param entries записи быстрой древесной выдачи
   * @return identity-множество переданных записей
   */
  private static Set<Entry> identitySetOf(List<Entry> entries) {
    Set<Entry> set = Collections.newSetFromMap(new IdentityHashMap<>());
    set.addAll(entries);
    return set;
  }

  /**
   * Досылает записи клиенту чанками {@code $/progress}, маппя каждую запись в {@link WorkspaceSymbol}.
   * <p>
   * Записи нарезаются на чанки по {@link #STREAM_BATCH_SIZE} в исходном порядке, чтобы не забивать
   * канал связи одним крупным уведомлением. Перед отправкой каждого чанка проверяется отмена запроса
   * ({@link CancelChecker#checkCanceled()}); при отмене бросается
   * {@link java.util.concurrent.CancellationException}. Используется для обоих наборов — быстрой
   * древесной выдачи и fuzzy-хвоста.
   *
   * @param token         токен частичного результата из параметров запроса
   * @param entries       записи индекса для потоковой выдачи
   * @param cancelChecker проверяющий отмену запроса между чанками
   */
  private void streamInBatches(Either<String, Integer> token, List<Entry> entries, CancelChecker cancelChecker) {
    for (var from = 0; from < entries.size(); from += STREAM_BATCH_SIZE) {
      cancelChecker.checkCanceled();
      var batch = entries.subList(from, Math.min(from + STREAM_BATCH_SIZE, entries.size())).stream()
        .map(SymbolProvider::createWorkspaceSymbol)
        .toList();
      streamChunk(token, batch);
    }
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
