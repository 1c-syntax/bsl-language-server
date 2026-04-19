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
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentClosedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.infrastructure.BslLsExecutors;
import com.github._1c_syntax.bsl.languageserver.semantictokens.SemanticTokenEntry;
import com.github._1c_syntax.bsl.languageserver.semantictokens.SemanticTokensSupplier;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensDelta;
import org.eclipse.lsp4j.SemanticTokensDeltaParams;
import org.eclipse.lsp4j.SemanticTokensEdit;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.SemanticTokensRangeParams;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.function.Supplier;

/**
 * Провайдер для предоставления семантических токенов.
 * <p>
 * Обрабатывает запросы {@code textDocument/semanticTokens/full}, {@code textDocument/semanticTokens/full/delta}
 * и {@code textDocument/semanticTokens/range}.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_semanticTokens">Semantic Tokens specification</a>
 */
@Component
@RequiredArgsConstructor
public class SemanticTokensProvider {

  /**
   * Порог количества токенов, при превышении которого используется параллельная обработка.
   */
  private static final int PARALLEL_PROCESSING_THRESHOLD = 1000;

  /**
   * Минимальное число поставщиков, при котором имеет смысл идти в parallel stream.
   */
  private static final int PARALLEL_SUPPLIERS_THRESHOLD = 4;

  private final List<SemanticTokensSupplier> suppliers;
  private final BslLsExecutors executors;

  /**
   * Cache for storing previous token data by resultId.
   * Key: resultId, Value: token data list
   */
  private final Map<String, CachedTokenData> tokenCache = new ConcurrentHashMap<>();

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
    var entries = collectTokens(documentContext);
    int[] data = toDeltaEncodedArray(entries);

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

    var entries = collectTokens(documentContext);
    int[] currentData = toDeltaEncodedArray(entries);

    String resultId = generateResultId();

    if (previousData == null || !previousData.uri().equals(documentContext.getUri())) {
      cacheTokenData(resultId, documentContext.getUri(), currentData);
      return Either.forLeft(new SemanticTokens(resultId, toList(currentData)));
    }

    List<SemanticTokensEdit> edits = computeEdits(previousData.data(), currentData);

    cacheTokenData(resultId, documentContext.getUri(), currentData);
    tokenCache.remove(previousResultId);

    var delta = new SemanticTokensDelta();
    delta.setResultId(resultId);
    delta.setEdits(edits);
    return Either.forRight(delta);
  }

  /**
   * Получить семантические токены для указанного диапазона документа.
   *
   * @param documentContext Контекст документа
   * @param params          Параметры запроса с диапазоном
   * @return Семантические токены для указанного диапазона в дельта-кодированном формате
   */
  public SemanticTokens getSemanticTokensRange(
    DocumentContext documentContext,
    SemanticTokensRangeParams params
  ) {
    Range range = params.getRange();

    var entries = collectTokens(documentContext);
    var filteredEntries = filterTokensByRange(entries, range);
    int[] data = toDeltaEncodedArray(filteredEntries);

    return new SemanticTokens(toList(data));
  }

  /**
   * Фильтрует токены, оставляя только те, которые попадают в указанный диапазон.
   * <p>
   * Токен считается попадающим в диапазон, если он хотя бы частично пересекается с ним.
   * <p>
   * Оптимизация: сначала выполняется быстрая фильтрация по строкам (простое сравнение целых чисел),
   * затем для граничных строк проверяются позиции символов. Использует параллельную обработку
   * для больших объемов данных.
   *
   * @param entries Список токенов
   * @param range   Диапазон для фильтрации
   * @return Отфильтрованный список токенов
   */
  private static List<SemanticTokenEntry> filterTokensByRange(List<SemanticTokenEntry> entries, Range range) {
    int startLine = range.getStart().getLine();
    int startChar = range.getStart().getCharacter();
    int endLine = range.getEnd().getLine();
    int endChar = range.getEnd().getCharacter();

    var stream = entries.size() > PARALLEL_PROCESSING_THRESHOLD
      ? entries.parallelStream()
      : entries.stream();

    return stream
      .filter(token -> token.line() >= startLine && token.line() <= endLine)
      .filter(token -> isTokenInRangeDetailed(token, startLine, startChar, endLine, endChar))
      .toList();
  }

  /**
   * Проверяет позицию символов для токенов на граничных строках.
   * <p>
   * Предполагается, что токен уже прошел проверку по строкам (находится между startLine и endLine).
   */
  private static boolean isTokenInRangeDetailed(
    SemanticTokenEntry token,
    int startLine,
    int startChar,
    int endLine,
    int endChar
  ) {
    int tokenLine = token.line();
    int tokenStart = token.start();
    int tokenEnd = tokenStart + token.length();

    if (tokenLine == startLine && tokenEnd <= startChar) {
      return false;
    }

    return tokenLine != endLine || tokenStart < endChar;
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

  private static String generateResultId() {
    return UUID.randomUUID().toString();
  }

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
      prefixAbsLine += prev[base];
      firstDiffToken = i + 1;
    }

    if (firstDiffToken == minTokens && prevTokenCount == currTokenCount) {
      return List.of();
    }

    int prevSuffixAbsLine = prefixAbsLine;
    for (int i = firstDiffToken; i < prevTokenCount; i++) {
      prevSuffixAbsLine += prev[i * TOKEN_SIZE];
    }
    int currSuffixAbsLine = prefixAbsLine;
    for (int i = firstDiffToken; i < currTokenCount; i++) {
      currSuffixAbsLine += curr[i * TOKEN_SIZE];
    }
    int lineOffset = currSuffixAbsLine - prevSuffixAbsLine;

    int suffixMatchTokens = findSuffixMatchWithOffset(prev, curr, firstDiffToken, lineOffset, TOKEN_SIZE);

    int deleteEndToken = prevTokenCount - suffixMatchTokens;
    int insertEndToken = currTokenCount - suffixMatchTokens;

    int deleteStart = firstDiffToken * TOKEN_SIZE;
    int deleteCount = (deleteEndToken - firstDiffToken) * TOKEN_SIZE;
    int insertEnd = insertEndToken * TOKEN_SIZE;

    if (deleteCount == 0 && deleteStart == insertEnd) {
      return List.of();
    }

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
   * При вставке текста без перевода строки (lineOffset == 0), первый токен
   * может иметь смещённый deltaStart.
   * <p>
   * ВАЖНО: Граничный токен (с изменённым deltaLine при lineOffset != 0) НЕ включается
   * в suffix match, чтобы клиент получил обновлённое значение deltaLine через edit.
   * Это критично для случая, когда добавляются только пустые строки без нового кода.
   */
  private static int findSuffixMatchWithOffset(int[] prev,
                                               int[] curr,
                                               int firstDiffToken,
                                               int lineOffset,
                                               int tokenSize) {
    final int DELTA_LINE_INDEX = 0;
    final int DELTA_START_INDEX = 1;

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

      int firstFieldToCheck = (!foundBoundary && lineOffset == 0) ? DELTA_START_INDEX + 1 : DELTA_START_INDEX;

      boolean otherFieldsMatch = true;
      for (int j = firstFieldToCheck; j < tokenSize; j++) {
        if (prev[prevIdx + j] != curr[currIdx + j]) {
          otherFieldsMatch = false;
          break;
        }
      }

      if (!otherFieldsMatch) {
        break;
      }

      int prevDeltaLine = prev[prevIdx + DELTA_LINE_INDEX];
      int currDeltaLine = curr[currIdx + DELTA_LINE_INDEX];

      if (prevDeltaLine == currDeltaLine) {
        if (!foundBoundary && lineOffset == 0) {
          int prevDeltaStart = prev[prevIdx + DELTA_START_INDEX];
          int currDeltaStart = curr[currIdx + DELTA_START_INDEX];
          if (prevDeltaStart != currDeltaStart) {
            foundBoundary = true;
            continue;
          }
        }
        suffixMatch++;
      } else if (!foundBoundary && currDeltaLine - prevDeltaLine == lineOffset) {
        foundBoundary = true;
      } else {
        break;
      }
    }

    return suffixMatch;
  }

  /**
   * Собирает токены со всех поставщиков на общем CPU-пуле из {@link BslLsExecutors}.
   * Распараллеливается только когда поставщиков достаточно много.
   */
  private List<SemanticTokenEntry> collectTokens(DocumentContext documentContext) {
    if (suppliers.isEmpty()) {
      return List.of();
    }

    Supplier<List<SemanticTokenEntry>> task = () -> {
      var stream = suppliers.size() >= PARALLEL_SUPPLIERS_THRESHOLD
        ? suppliers.parallelStream()
        : suppliers.stream();
      return stream
        .map(supplier -> supplier.getSemanticTokens(documentContext))
        .flatMap(Collection::stream)
        .toList();
    };

    if (executors != null && executors.isInCpuPool()) {
      return task.get();
    }

    var pool = executors == null ? ForkJoinPool.commonPool() : executors.getCpuExecutor();
    return pool.invoke(ForkJoinTask.adapt(
      (Callable<List<SemanticTokenEntry>>) task::get
    ));
  }

  private static int[] toDeltaEncodedArray(List<SemanticTokenEntry> entries) {
    if (entries.isEmpty()) {
      return new int[0];
    }
    Set<SemanticTokenEntry> uniq = new HashSet<>(entries);
    List<SemanticTokenEntry> sorted = new ArrayList<>(uniq);
    sorted.sort(Comparator
      .comparingInt(SemanticTokenEntry::line)
      .thenComparingInt(SemanticTokenEntry::start));

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

  /**
   * Преобразует {@code int[]} в {@code List<Integer>}, обязательный для LSP4J.
   */
  private static List<Integer> toList(int[] array) {
    if (array.length == 0) {
      return List.of();
    }
    var boxed = new Integer[array.length];
    for (var i = 0; i < array.length; i++) {
      boxed[i] = array[i];
    }
    return Arrays.asList(boxed);
  }
}
