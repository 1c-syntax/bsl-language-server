/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentClosedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.semantictokens.SemanticTokenEntry;
import com.github._1c_syntax.bsl.languageserver.semantictokens.SemanticTokensSupplier;
import com.github._1c_syntax.bsl.languageserver.utils.NamedForkJoinWorkerThreadFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensDelta;
import org.eclipse.lsp4j.SemanticTokensDeltaParams;
import org.eclipse.lsp4j.SemanticTokensEdit;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

/**
 * Провайдер для предоставления семантических токенов.
 * <p>
 * Обрабатывает запросы {@code textDocument/semanticTokens/full} и {@code textDocument/semanticTokens/full/delta}.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_semanticTokens">Semantic Tokens specification</a>
 */
@Component
@RequiredArgsConstructor
public class SemanticTokensProvider {

  @SuppressWarnings("NullAway.Init")
  private ExecutorService executorService;

  private final List<SemanticTokensSupplier> suppliers;

  /**
   * Cache for storing previous token data by resultId.
   * Key: resultId, Value: token data list
   */
  private final Map<String, CachedTokenData> tokenCache = new ConcurrentHashMap<>();

  @PostConstruct
  private void init() {
    var factory = new NamedForkJoinWorkerThreadFactory("semantic-tokens-");
    executorService = new ForkJoinPool(ForkJoinPool.getCommonPoolParallelism(), factory, null, true);
  }

  @PreDestroy
  private void onDestroy() {
    executorService.shutdown();
  }

  /**
   * Cached semantic token data associated with a document.
   *
   * @param uri  URI of the document
   * @param data token data as int array (more efficient than List<Integer>)
   */
  private record CachedTokenData(URI uri, int[] data) {
  }

  /**
   * Получить семантические токены для всего документа.
   *
   * @param documentContext Контекст документа
   * @param params          Параметры запроса
   * @return Семантические токены в дельта-кодированном формате
   */
  public SemanticTokens getSemanticTokensFull(
    DocumentContext documentContext,
    @SuppressWarnings("unused") SemanticTokensParams params
  ) {
    // Collect tokens from all suppliers in parallel
    var entries = collectTokens(documentContext);

    // Build delta-encoded data as int array
    int[] data = toDeltaEncodedArray(entries);

    // Generate a unique resultId and cache the data
    String resultId = generateResultId();
    cacheTokenData(resultId, documentContext.getUri(), data);

    return new SemanticTokens(resultId, toList(data));
  }

  /**
   * Получить дельту семантических токенов относительно предыдущего результата.
   *
   * @param documentContext Контекст документа
   * @param params          Параметры запроса с previousResultId
   * @return Либо дельту токенов, либо полные токены, если предыдущий результат недоступен
   */
  public Either<SemanticTokens, SemanticTokensDelta> getSemanticTokensFullDelta(
    DocumentContext documentContext,
    SemanticTokensDeltaParams params
  ) {
    String previousResultId = params.getPreviousResultId();
    CachedTokenData previousData = tokenCache.get(previousResultId);

    // Collect tokens from all suppliers in parallel
    var entries = collectTokens(documentContext);

    // Build delta-encoded data as int array
    int[] currentData = toDeltaEncodedArray(entries);

    // Generate new resultId
    String resultId = generateResultId();

    // If previous data is not available or belongs to a different document, return full tokens
    if (previousData == null || !previousData.uri().equals(documentContext.getUri())) {
      cacheTokenData(resultId, documentContext.getUri(), currentData);
      return Either.forLeft(new SemanticTokens(resultId, toList(currentData)));
    }

    // Compute delta edits
    List<SemanticTokensEdit> edits = computeEdits(previousData.data(), currentData);

    // Cache the new data
    cacheTokenData(resultId, documentContext.getUri(), currentData);

    // Remove the old cached data
    tokenCache.remove(previousResultId);

    var delta = new SemanticTokensDelta();
    delta.setResultId(resultId);
    delta.setEdits(edits);
    return Either.forRight(delta);
  }

  /**
   * Обрабатывает событие закрытия документа в контексте сервера.
   * <p>
   * При закрытии документа очищает кэшированные данные семантических токенов.
   *
   * @param event событие закрытия документа
   */
  @EventListener
  public void handleDocumentClosed(ServerContextDocumentClosedEvent event) {
    clearCache(event.getDocumentContext().getUri());
  }

  /**
   * Обрабатывает событие удаления документа из контекста сервера.
   * <p>
   * При удалении документа очищает кэшированные данные семантических токенов.
   *
   * @param event событие удаления документа
   */
  @EventListener
  public void handleDocumentRemoved(ServerContextDocumentRemovedEvent event) {
    clearCache(event.getUri());
  }

  /**
   * Очищает кэшированные данные токенов для указанного документа.
   *
   * @param uri URI документа, для которого нужно очистить кэш
   */
  protected void clearCache(URI uri) {
    tokenCache.entrySet().removeIf(entry -> entry.getValue().uri().equals(uri));
  }

  /**
   * Generate a unique result ID for caching.
   */
  private static String generateResultId() {
    return UUID.randomUUID().toString();
  }

  /**
   * Cache token data with the given resultId.
   */
  private void cacheTokenData(String resultId, URI uri, int[] data) {
    tokenCache.put(resultId, new CachedTokenData(uri, data));
  }

  /**
   * Compute edits to transform previousData into currentData.
   * <p>
   * Учитывает структуру семантических токенов (группы по 5 элементов: deltaLine, deltaStart, length, type, modifiers)
   * и смещение строк при вставке/удалении строк в документе.
   */
  private static List<SemanticTokensEdit> computeEdits(int[] prev, int[] curr) {
    final int TOKEN_SIZE = 5;

    int prevTokenCount = prev.length / TOKEN_SIZE;
    int currTokenCount = curr.length / TOKEN_SIZE;

    if (prevTokenCount == 0 && currTokenCount == 0) {
      return List.of();
    }

    // Находим первый отличающийся токен и одновременно вычисляем сумму deltaLine для prefix
    int firstDiffToken = 0;
    int prefixAbsLine = 0;
    int minTokens = Math.min(prevTokenCount, currTokenCount);

    outer:
    for (int i = 0; i < minTokens; i++) {
      int base = i * TOKEN_SIZE;
      for (int j = 0; j < TOKEN_SIZE; j++) {
        if (prev[base + j] != curr[base + j]) {
          firstDiffToken = i;
          break outer;
        }
      }
      prefixAbsLine += prev[base]; // накапливаем deltaLine
      firstDiffToken = i + 1;
    }

    // Если все токены одинаковые
    if (firstDiffToken == minTokens && prevTokenCount == currTokenCount) {
      return List.of();
    }

    // Вычисляем смещение строк инкрементально от prefixAbsLine
    int prevSuffixAbsLine = prefixAbsLine;
    for (int i = firstDiffToken; i < prevTokenCount; i++) {
      prevSuffixAbsLine += prev[i * TOKEN_SIZE];
    }
    int currSuffixAbsLine = prefixAbsLine;
    for (int i = firstDiffToken; i < currTokenCount; i++) {
      currSuffixAbsLine += curr[i * TOKEN_SIZE];
    }
    int lineOffset = currSuffixAbsLine - prevSuffixAbsLine;

    // Находим последний отличающийся токен с учётом смещения строк
    int suffixMatchTokens = findSuffixMatchWithOffset(prev, curr, firstDiffToken, lineOffset, TOKEN_SIZE);

    // Вычисляем границы редактирования
    int deleteEndToken = prevTokenCount - suffixMatchTokens;
    int insertEndToken = currTokenCount - suffixMatchTokens;

    int deleteStart = firstDiffToken * TOKEN_SIZE;
    int deleteCount = (deleteEndToken - firstDiffToken) * TOKEN_SIZE;
    int insertEnd = insertEndToken * TOKEN_SIZE;

    if (deleteCount == 0 && deleteStart == insertEnd) {
      return List.of();
    }

    // Создаём список для вставки из среза массива
    List<Integer> insertData = toList(Arrays.copyOfRange(curr, deleteStart, insertEnd));

    var edit = new SemanticTokensEdit();
    edit.setStart(deleteStart);
    edit.setDeleteCount(deleteCount);
    if (!insertData.isEmpty()) {
      edit.setData(insertData);
    }

    return List.of(edit);
  }

  /**
   * Находит количество совпадающих токенов с конца, учитывая смещение строк.
   * <p>
   * При дельта-кодировании токены после точки вставки идентичны,
   * кроме первого токена, у которого deltaLine смещён на lineOffset.
   */
  private static int findSuffixMatchWithOffset(int[] prev, int[] curr, int firstDiffToken, int lineOffset, int tokenSize) {
    int prevTokenCount = prev.length / tokenSize;
    int currTokenCount = curr.length / tokenSize;

    int maxPrevSuffix = prevTokenCount - firstDiffToken;
    int maxCurrSuffix = currTokenCount - firstDiffToken;
    int maxSuffix = Math.min(maxPrevSuffix, maxCurrSuffix);

    int suffixMatch = 0;
    boolean foundBoundary = false;

    for (int i = 0; i < maxSuffix; i++) {
      int prevIdx = (prevTokenCount - 1 - i) * tokenSize;
      int currIdx = (currTokenCount - 1 - i) * tokenSize;

      // Сначала проверяем все поля кроме deltaLine
      boolean otherFieldsMatch = true;
      for (int j = 1; j < tokenSize; j++) {
        if (prev[prevIdx + j] != curr[currIdx + j]) {
          otherFieldsMatch = false;
          break;
        }
      }

      if (!otherFieldsMatch) {
        break;
      }

      // Теперь проверяем deltaLine
      int prevDeltaLine = prev[prevIdx];
      int currDeltaLine = curr[currIdx];

      if (prevDeltaLine == currDeltaLine) {
        // Полное совпадение
        suffixMatch++;
      } else if (!foundBoundary && currDeltaLine - prevDeltaLine == lineOffset) {
        // Граничный токен — deltaLine отличается ровно на lineOffset
        suffixMatch++;
        foundBoundary = true;
      } else {
        // Не совпадает
        break;
      }
    }

    return suffixMatch;
  }

  /**
   * Collect tokens from all suppliers in parallel using ForkJoinPool.
   */
  private List<SemanticTokenEntry> collectTokens(DocumentContext documentContext) {
    return CompletableFuture
      .supplyAsync(
        () -> suppliers.parallelStream()
          .map(supplier -> supplier.getSemanticTokens(documentContext))
          .flatMap(Collection::stream)
          .toList(),
        executorService
      )
      .join();
  }

  private static int[] toDeltaEncodedArray(List<SemanticTokenEntry> entries) {
    // de-dup and sort
    Set<SemanticTokenEntry> uniq = new HashSet<>(entries);
    List<SemanticTokenEntry> sorted = new ArrayList<>(uniq);
    sorted.sort(Comparator
      .comparingInt(SemanticTokenEntry::line)
      .thenComparingInt(SemanticTokenEntry::start));

    // Use int[] to avoid boxing overhead during computation
    int[] data = new int[sorted.size() * 5];
    var prevLine = 0;
    var prevChar = 0;
    var index = 0;
    var first = true;

    for (SemanticTokenEntry tokenEntry : sorted) {
      int deltaLine = first ? tokenEntry.line() : (tokenEntry.line() - prevLine);
      int prevCharOrZero = (deltaLine == 0) ? prevChar : 0;
      int deltaStart = first ? tokenEntry.start() : (tokenEntry.start() - prevCharOrZero);

      data[index++] = deltaLine;
      data[index++] = deltaStart;
      data[index++] = tokenEntry.length();
      data[index++] = tokenEntry.type();
      data[index++] = tokenEntry.modifiers();

      prevLine = tokenEntry.line();
      prevChar = tokenEntry.start();
      first = false;
    }

    return data;
  }

  private static List<Integer> toList(int[] array) {
    return Arrays.stream(array).boxed().toList();
  }
}
