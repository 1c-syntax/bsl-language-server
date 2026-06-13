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
package com.github._1c_syntax.bsl.languageserver.types.index;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentClearedEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.types.ScriptVariant;
import org.apache.commons.collections4.trie.PatriciaTrie;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Инкрементальный индекс символов рабочей области для запросов {@code workspace/symbol}.
 * <p>
 * Заменяет полный обход всех документов с последующим слепым усечением выдачи: символы
 * собираются один раз на событии {@link DocumentContextContentChangedEvent} в лёгкие
 * неизменяемые записи {@link Entry} (имя, его lowercase-форма, {@link org.eclipse.lsp4j.SymbolKind}, диапазон,
 * URI, теги и готовое имя контейнера). Запрос обслуживается из индекса с ранжированием по
 * релевантности; выдача возвращается целиком, без усечения.
 * <p>
 * Хранилище — {@link PatriciaTrie} по ключу = lowercase-имя символа; значение — список записей
 * с этим именем (имена не уникальны). Дерево даёт {@link PatriciaTrie#prefixMap(Object)} —
 * сублинейный префиксный поиск, на котором строится основной (самый частый) путь запроса.
 * Подстрочные и fuzzy-совпадения (подпоследовательность) дерево по префиксу не покрывает,
 * поэтому добираются проходом по остальным ключам.
 * <p>
 * Индекс наследует {@link AbstractDocumentLifecycleClearableIndex}: его {@code @EventListener}'ы
 * сбрасывают записи документа на изменение содержимого, освобождение данных, закрытие и удаление.
 * <p>
 * Потокобезопасность: {@link PatriciaTrie} не потокобезопасен, поэтому доступ к нему защищён
 * {@link ReadWriteLock}. Запросы ({@link #search(String, CancelChecker)}) держат read-lock,
 * переиндексация и сброс ({@link #index(DocumentContext)}, {@link #clear(URI)}) — write-lock,
 * так что чтение и запись не гоняются. Записи {@link Entry} неизменяемы и безопасно покидают
 * блокировку в составе результата.
 */
@Component
@WorkspaceScope
public class WorkspaceSymbolIndex extends AbstractDocumentLifecycleClearableIndex {

  /**
   * Частота проверки отмены запроса (раз в N просмотренных записей).
   */
  private static final int CANCEL_CHECK_INTERVAL = 1024;

  /**
   * Скор точного совпадения имени с запросом (наиболее релевантно).
   */
  private static final int SCORE_EXACT = 0;

  /**
   * Скор совпадения имени по префиксу запроса.
   */
  private static final int SCORE_PREFIX = 1;

  /**
   * Скор совпадения запроса как непрерывной подстроки имени.
   */
  private static final int SCORE_SUBSTRING = 2;

  /**
   * Базовый скор совпадения запроса как подпоследовательности имени; к нему прибавляется
   * позиция первого совпавшего символа (более ранняя позиция — релевантнее).
   */
  private static final int SCORE_SUBSEQUENCE = 3;

  private static final Set<VariableKind> SUPPORTED_VARIABLE_KINDS = EnumSet.of(
    VariableKind.MODULE,
    VariableKind.GLOBAL
  );

  /**
   * Дерево записей по ключу = lowercase-имя символа.
   * <p>
   * Несколько символов с одинаковым именем хранятся одним списком под общим ключом.
   * Не потокобезопасен — любой доступ только под {@link #lock}.
   */
  private final PatriciaTrie<List<Entry>> trie = new PatriciaTrie<>();

  /**
   * Записи, добавленные в индекс каждым URI — для точечного сброса по событию жизненного цикла.
   */
  private final ConcurrentMap<URI, List<Entry>> indexedByUri = new ConcurrentHashMap<>();

  /**
   * Защита {@link #trie}: read-lock на запросах, write-lock на переиндексации и сбросе.
   */
  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  /**
   * Пересобрать записи документа на изменении его содержимого.
   * <p>
   * В отличие от базового обработчика, который только сбрасывает записи документа, здесь после
   * сброса ({@link #clear(URI)}) из дерева символов собираются поддерживаемые символы (методы,
   * конструкторы и модульные/глобальные переменные) в неизменяемые записи с уже вычисленным
   * именем контейнера. Метод переопределяет родительский, чтобы остаться единственным слушателем
   * {@link DocumentContextContentChangedEvent} и не получить гонку «индексация против сброса».
   *
   * @param event событие изменения содержимого документа
   */
  @EventListener
  @Override
  public void handleContentChanged(DocumentContextContentChangedEvent event) {
    index(event.getSource());
  }

  /**
   * Сохранить записи документа при освобождении его вторичных данных.
   * <p>
   * Базовый обработчик сбрасывает индекс на {@link ServerContextDocumentClearedEvent} (batch-анализ
   * выбрасывает AST после каждого файла). Записи этого индекса не ссылаются на AST или дерево
   * символов — это автономные снимки (имя, kind, range, теги, containerName), которые остаются
   * валидны и после освобождения вторичных данных. Поэтому обработчик переопределён в no-op: иначе
   * массовое наполнение workspace очищало бы индекс сразу после индексации каждого документа, и
   * глобальный поиск {@code workspace/symbol} не находил бы ничего до следующего инкрементального
   * изменения. Актуальность записей при реальном изменении содержимого гарантирует
   * {@link #handleContentChanged(DocumentContextContentChangedEvent)}, а закрытие и удаление
   * документа по-прежнему чистят индекс через унаследованные обработчики.
   *
   * @param event событие освобождения вторичных данных документа
   */
  @EventListener
  @Override
  public void handleDataCleared(ServerContextDocumentClearedEvent event) {
    // no-op: записи автономны и должны пережить освобождение AST при batch-наполнении
  }

  /**
   * Удалить записи индекса, относящиеся к данному URI документа.
   * <p>
   * Записи документа вычищаются из {@link #trie} под write-lock; ключи, оставшиеся без записей,
   * удаляются из дерева, чтобы не копить пустые узлы.
   *
   * @param uri URI документа
   */
  @Override
  public void clear(URI uri) {
    var removed = indexedByUri.remove(uri);
    if (removed == null || removed.isEmpty()) {
      return;
    }
    var removedSet = Collections.<Entry>newSetFromMap(new IdentityHashMap<>());
    removedSet.addAll(removed);

    lock.writeLock().lock();
    try {
      for (var entry : removed) {
        removeEntryFromKey(entry.lowerName(), removedSet);
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Найти символы рабочей области по запросу с ранжированием по релевантности.
   * <p>
   * Сопоставление регистронезависимое. Скоринг (меньше — релевантнее): точное совпадение,
   * затем совпадение по префиксу, затем непрерывная подстрока, затем подпоследовательность;
   * совпадения по подпоследовательности дополнительно штрафуются позицией первого символа.
   * При равном скоре раньше идёт более короткое имя, затем — более ранняя позиция в документе.
   * Несовпавшие записи отбрасываются. Выдача возвращается целиком, без усечения: пустой запрос
   * отдаёт все записи индекса, непустой — все совпадения, отсортированные по релевантности.
   * <p>
   * Префиксная часть обслуживается сублинейно через {@link PatriciaTrie#prefixMap(Object)};
   * подстрочные и fuzzy-совпадения (подпоследовательность) дерево по префиксу не покрывает,
   * поэтому добираются проходом по остальным ключам.
   * <p>
   * Отмена проверяется периодически в ходе сканирования; при отмене бросается
   * {@link java.util.concurrent.CancellationException}.
   *
   * @param query         строка запроса пользователя (пустая строка означает «вернуть всё»)
   * @param cancelChecker проверяющий отмену запроса
   * @return полный ранжированный список найденных записей
   */
  public List<Entry> search(String query, CancelChecker cancelChecker) {
    cancelChecker.checkCanceled();

    var lowerQuery = query.toLowerCase(Locale.ENGLISH);

    lock.readLock().lock();
    try {
      if (lowerQuery.isEmpty()) {
        return collectAll(cancelChecker);
      }
      return searchMatching(lowerQuery, cancelChecker);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Собрать совпадения непустого запроса: сначала сублинейный префиксный путь через дерево,
   * затем добор подстрочных и fuzzy-совпадений проходом по остальным ключам.
   *
   * @param lowerQuery    lowercase-запрос (непустой)
   * @param cancelChecker проверяющий отмену запроса
   * @return ранжированный список совпадений без дублей
   */
  private List<Entry> searchMatching(String lowerQuery, CancelChecker cancelChecker) {
    var matches = new ArrayList<Scored>();
    var matchedKeys = new HashSet<String>();
    var progress = new ScanProgress(cancelChecker);

    collectPrefixMatches(lowerQuery, matches, matchedKeys, progress);
    collectFuzzyMatches(lowerQuery, matches, matchedKeys, progress);

    matches.sort(Comparator.naturalOrder());
    return matches.stream().map(Scored::entry).toList();
  }

  /**
   * Быстрый путь: префиксные совпадения сублинейно через {@link PatriciaTrie#prefixMap(Object)}.
   * Точное совпадение получает {@link #SCORE_EXACT}, остальные префиксные — {@link #SCORE_PREFIX}.
   * Просмотренные ключи добавляются в {@code matchedKeys}, чтобы добор их не пересчитывал.
   *
   * @param lowerQuery  lowercase-запрос (непустой)
   * @param matches     накопитель кандидатов со скором
   * @param matchedKeys накопитель уже учтённых ключей
   * @param progress    счётчик прогресса для периодической проверки отмены
   */
  private void collectPrefixMatches(
    String lowerQuery,
    List<Scored> matches,
    Set<String> matchedKeys,
    ScanProgress progress
  ) {
    for (var bucket : trie.prefixMap(lowerQuery).entrySet()) {
      matchedKeys.add(bucket.getKey());
      var score = bucket.getKey().equals(lowerQuery) ? SCORE_EXACT : SCORE_PREFIX;
      for (var entry : bucket.getValue()) {
        progress.advance();
        matches.add(new Scored(score, entry));
      }
    }
  }

  /**
   * Добор подстрочных и fuzzy-совпадений: префиксное дерево их не ускоряет, поэтому идёт общий
   * проход по ключам, не покрытым префиксным путём. Несовпавшие ключи отсеивает
   * {@link #fuzzyScore(String, String)}.
   *
   * @param lowerQuery  lowercase-запрос (непустой)
   * @param matches     накопитель кандидатов со скором
   * @param matchedKeys ключи, уже учтённые префиксным путём
   * @param progress    счётчик прогресса для периодической проверки отмены
   */
  private void collectFuzzyMatches(
    String lowerQuery,
    List<Scored> matches,
    Set<String> matchedKeys,
    ScanProgress progress
  ) {
    for (var bucket : trie.entrySet()) {
      var fuzzyScore = matchedKeys.contains(bucket.getKey())
        ? -1
        : fuzzyScore(bucket.getKey(), lowerQuery);
      if (fuzzyScore < 0) {
        continue;
      }
      for (var entry : bucket.getValue()) {
        progress.advance();
        matches.add(new Scored(fuzzyScore, entry));
      }
    }
  }

  /**
   * Собрать все записи индекса в детерминированном порядке (для пустого запроса).
   *
   * @param cancelChecker проверяющий отмену запроса
   * @return все записи индекса
   */
  private List<Entry> collectAll(CancelChecker cancelChecker) {
    var result = new ArrayList<Entry>();
    var progress = new ScanProgress(cancelChecker);
    for (var bucket : trie.values()) {
      for (var entry : bucket) {
        progress.advance();
        result.add(entry);
      }
    }
    return result;
  }

  private void index(DocumentContext documentContext) {
    var uri = documentContext.getUri();
    clear(uri);

    var scriptVariant = scriptVariantOf(documentContext);
    var collected = new ArrayList<Entry>();
    for (var symbol : documentContext.getSymbolTree().getChildrenFlat()) {
      // Безымянные символы не индексируются: для workspace/symbol они бесполезны.
      if (!isSupported(symbol) || symbol.getName().isEmpty()) {
        continue;
      }
      collected.add(toEntry(uri, symbol, scriptVariant));
    }

    if (collected.isEmpty()) {
      return;
    }

    indexedByUri.put(uri, List.copyOf(collected));

    lock.writeLock().lock();
    try {
      for (var entry : collected) {
        trie.merge(entry.lowerName(), List.of(entry), WorkspaceSymbolIndex::concat);
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Удалить из {@link #trie} записи с данным ключом, попавшие в {@code toRemove};
   * пустой после фильтрации ключ удаляется целиком. Вызывается под write-lock.
   *
   * @param key      lowercase-имя (ключ дерева)
   * @param toRemove удаляемые записи (по идентичности)
   */
  private void removeEntryFromKey(String key, Set<Entry> toRemove) {
    var bucket = trie.get(key);
    if (bucket == null) {
      return;
    }
    var filtered = withoutEntries(bucket, toRemove);
    if (filtered.isEmpty()) {
      trie.remove(key);
    } else {
      trie.put(key, filtered);
    }
  }

  private static Entry toEntry(URI uri, SourceDefinedSymbol symbol, ScriptVariant scriptVariant) {
    var name = symbol.getName();
    var containerName = getContainerName(symbol, scriptVariant).orElse("");
    return new Entry(
      uri,
      name,
      name.toLowerCase(Locale.ENGLISH),
      symbol.getSymbolKind(),
      symbol.getRange(),
      List.copyOf(symbol.getTags()),
      containerName
    );
  }

  private static boolean isSupported(Symbol symbol) {
    return switch (symbol.getSymbolKind()) {
      case Method, Constructor -> true;
      case Variable -> SUPPORTED_VARIABLE_KINDS.contains(((VariableSymbol) symbol).getKind());
      default -> false;
    };
  }

  private static Optional<String> getContainerName(SourceDefinedSymbol symbol, ScriptVariant scriptVariant) {
    return symbol.getOwner().getMdObject()
      .map(MD::getMdoReference)
      .map(mdoReference -> mdoReference.getMdoRef(scriptVariant));
  }

  private static ScriptVariant scriptVariantOf(DocumentContext documentContext) {
    return documentContext.getScriptVariantLanguage() == Language.EN
      ? ScriptVariant.ENGLISH
      : ScriptVariant.RUSSIAN;
  }

  /**
   * Вычислить скор «не-префиксного» совпадения lowercase-имени с lowercase-запросом.
   * <p>
   * Применяется только к ключам вне префиксного множества дерева (точное и префиксное
   * совпадения там уже исключены). Меньшее значение — релевантнее: {@link #SCORE_SUBSTRING} —
   * непрерывная подстрока, {@link #SCORE_SUBSEQUENCE}{@code  + позиция} — подпоследовательность.
   *
   * @param lowerName  lowercase-имя символа
   * @param lowerQuery lowercase-запрос (непустой, не префикс имени)
   * @return скор {@code >= SCORE_SUBSTRING}, либо {@code -1}, если совпадения нет
   */
  private static int fuzzyScore(String lowerName, String lowerQuery) {
    if (lowerName.contains(lowerQuery)) {
      return SCORE_SUBSTRING;
    }
    var firstMatch = subsequenceFirstIndex(lowerName, lowerQuery);
    if (firstMatch >= 0) {
      return SCORE_SUBSEQUENCE + firstMatch;
    }
    return -1;
  }

  /**
   * Проверить, что {@code lowerQuery} — подпоследовательность {@code lowerName}, и вернуть индекс
   * символа имени, на котором совпал первый символ запроса.
   *
   * @param lowerName  lowercase-имя символа
   * @param lowerQuery lowercase-запрос
   * @return индекс первого совпавшего символа, либо {@code -1}, если не подпоследовательность
   */
  private static int subsequenceFirstIndex(String lowerName, String lowerQuery) {
    var firstMatch = -1;
    var queryIndex = 0;
    for (var nameIndex = 0; nameIndex < lowerName.length() && queryIndex < lowerQuery.length(); nameIndex++) {
      if (lowerName.charAt(nameIndex) == lowerQuery.charAt(queryIndex)) {
        if (queryIndex == 0) {
          firstMatch = nameIndex;
        }
        queryIndex++;
      }
    }
    return queryIndex == lowerQuery.length() ? firstMatch : -1;
  }

  private static List<Entry> concat(List<Entry> existing, List<Entry> added) {
    var copy = new ArrayList<Entry>(existing.size() + added.size());
    copy.addAll(existing);
    copy.addAll(added);
    return List.copyOf(copy);
  }

  private static List<Entry> withoutEntries(List<Entry> source, Set<Entry> toRemove) {
    var copy = new ArrayList<Entry>(source.size());
    for (var entry : source) {
      if (!toRemove.contains(entry)) {
        copy.add(entry);
      }
    }
    return List.copyOf(copy);
  }

  /**
   * Счётчик просмотренных записей для периодической проверки отмены запроса.
   * <p>
   * Один экземпляр разделяется между этапами сканирования, чтобы интервал проверки отмены
   * ({@link #CANCEL_CHECK_INTERVAL}) считался по суммарному прогрессу, а не сбрасывался на
   * каждом этапе.
   */
  private static final class ScanProgress {
    private final CancelChecker cancelChecker;
    private int seen;

    private ScanProgress(CancelChecker cancelChecker) {
      this.cancelChecker = cancelChecker;
    }

    /**
     * Учесть просмотр одной записи и проверить отмену по достижении интервала.
     */
    private void advance() {
      seen++;
      if ((seen & (CANCEL_CHECK_INTERVAL - 1)) == 0) {
        cancelChecker.checkCanceled();
      }
    }
  }

  /**
   * Кандидат поиска со своим скором.
   * <p>
   * Естественный порядок (от лучшего к худшему): меньший скор, затем более короткое имя,
   * затем более ранняя позиция в документе. Используется для финальной сортировки выдачи.
   *
   * @param score скор совпадения (меньше — релевантнее)
   * @param entry запись индекса
   */
  private record Scored(int score, Entry entry) implements Comparable<Scored> {
    @Override
    public int compareTo(Scored other) {
      var byScore = Integer.compare(score, other.score);
      if (byScore != 0) {
        return byScore;
      }
      var byLength = Integer.compare(entry.name().length(), other.entry.name().length());
      if (byLength != 0) {
        return byLength;
      }
      var start = entry.range().getStart();
      var otherStart = other.entry.range().getStart();
      var byLine = Integer.compare(start.getLine(), otherStart.getLine());
      if (byLine != 0) {
        return byLine;
      }
      return Integer.compare(start.getCharacter(), otherStart.getCharacter());
    }
  }
}
