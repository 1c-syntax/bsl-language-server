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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

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
   * @param data token data list
   */
  private record CachedTokenData(URI uri, List<Integer> data) {
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

    // Build delta-encoded data
    List<Integer> data = toDeltaEncoded(entries);

    // Generate a unique resultId and cache the data
    String resultId = generateResultId();
    cacheTokenData(resultId, documentContext.getUri(), data);

    return new SemanticTokens(resultId, data);
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

    // Build delta-encoded data
    List<Integer> currentData = toDeltaEncoded(entries);

    // Generate new resultId
    String resultId = generateResultId();

    // If previous data is not available or belongs to a different document, return full tokens
    if (previousData == null || !previousData.uri().equals(documentContext.getUri())) {
      cacheTokenData(resultId, documentContext.getUri(), currentData);
      return Either.forLeft(new SemanticTokens(resultId, currentData));
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

    // Collect tokens from all suppliers in parallel
    var entries = collectTokens(documentContext);

    // Filter tokens that fall within the specified range
    var filteredEntries = filterTokensByRange(entries, range);

    // Build delta-encoded data
    List<Integer> data = toDeltaEncoded(filteredEntries);

    // Range requests do not use resultId caching as per LSP specification
    return new SemanticTokens(data);
  }

  /**
   * Фильтрует токены, оставляя только те, которые попадают в указанный диапазон.
   * <p>
   * Токен считается попадающим в диапазон, если он хотя бы частично пересекается с ним.
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

    return entries.stream()
      .filter(token -> isTokenInRange(token, startLine, startChar, endLine, endChar))
      .toList();
  }

  /**
   * Проверяет, попадает ли токен в указанный диапазон.
   * <p>
   * Токен попадает в диапазон, если он хотя бы частично пересекается с ним.
   */
  private static boolean isTokenInRange(
    SemanticTokenEntry token,
    int startLine,
    int startChar,
    int endLine,
    int endChar
  ) {
    int tokenLine = token.line();
    int tokenStart = token.start();
    int tokenEnd = tokenStart + token.length();

    // Token ends before range starts
    if (tokenLine < startLine || (tokenLine == startLine && tokenEnd <= startChar)) {
      return false;
    }

    // Token starts after range ends
    if (tokenLine > endLine || (tokenLine == endLine && tokenStart >= endChar)) {
      return false;
    }

    return true;
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
  private void cacheTokenData(String resultId, URI uri, List<Integer> data) {
    tokenCache.put(resultId, new CachedTokenData(uri, data));
  }

  /**
   * Compute edits to transform previousData into currentData.
   * Uses a simple algorithm that produces a single edit covering the entire change.
   */
  private static List<SemanticTokensEdit> computeEdits(List<Integer> previousData, List<Integer> currentData) {
    // Find the first differing index
    int minSize = Math.min(previousData.size(), currentData.size());
    int prefixMatch = 0;
    while (prefixMatch < minSize && previousData.get(prefixMatch).equals(currentData.get(prefixMatch))) {
      prefixMatch++;
    }

    // If both are identical, return empty edits
    if (prefixMatch == previousData.size() && prefixMatch == currentData.size()) {
      return List.of();
    }

    // Find the last differing index (from the end)
    int suffixMatch = 0;
    while (suffixMatch < minSize - prefixMatch
      && previousData.get(previousData.size() - 1 - suffixMatch)
      .equals(currentData.get(currentData.size() - 1 - suffixMatch))) {
      suffixMatch++;
    }

    // Calculate the range to replace
    int deleteStart = prefixMatch;
    int deleteCount = previousData.size() - prefixMatch - suffixMatch;
    int insertEnd = currentData.size() - suffixMatch;

    // Extract the data to insert
    List<Integer> insertData = currentData.subList(prefixMatch, insertEnd);

    var edit = new SemanticTokensEdit();
    edit.setStart(deleteStart);
    edit.setDeleteCount(deleteCount);
    if (!insertData.isEmpty()) {
      edit.setData(new ArrayList<>(insertData));
    }

    return List.of(edit);
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

  private static List<Integer> toDeltaEncoded(List<SemanticTokenEntry> entries) {
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

    // Convert to List<Integer> for LSP4J API
    return Arrays.stream(data).boxed().toList();
  }
}
