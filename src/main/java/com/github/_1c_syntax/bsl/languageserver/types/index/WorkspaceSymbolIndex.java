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
import org.apache.commons.lang3.StringUtils;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * Хранилище — {@link PatriciaTrie}; значение — список записей под ключом (имена не уникальны).
 * Одна запись кладётся под НЕСКОЛЬКИМИ ключами: lowercase-суффиксы имени, начинающиеся с каждого
 * CamelCase-слова (имена 1С — CamelCase: {@code ПровестиДокумент} = Провести + Документ). Так для
 * {@code ПровестиДокумент} ключи — {@code провестидокумент} (полное имя) и {@code документ} (начало
 * второго слова). Это даёт сублинейный префиксный поиск {@link PatriciaTrie#prefixMap(Object)} не
 * только по началу полного имени, но и по началу любого слова: запрос {@code Док} находит
 * {@code ПровестиДокумент} через дерево, а не сканом. Слова режутся
 * {@link StringUtils#splitByCharacterTypeCamelCase(String)} (кириллица режется корректно).
 * <p>
 * Что покрывает префиксное дерево: префикс полного имени И префикс любого CamelCase-слова.
 * <p>
 * Многословные (camel-hump) запросы обслуживаются сублинейно. Запрос режется на
 * CamelCase-фрагменты ({@code ПрДок} → {@code пр},{@code док}); для каждого фрагмента из дерева
 * берётся множество записей по {@link PatriciaTrie#prefixMap(Object)}, и множества пересекаются
 * (запись должна иметь слово, начинающееся с КАЖДОГО фрагмента). Пересечение начинается с самого
 * мелкого множества, чтобы оставаться дешёвым. Среди выживших записей те, у которых фрагменты
 * назначаются словам строго по возрастанию (порядок фрагментов = порядок слов), ранжируются выше
 * ({@link #SCORE_MULTI_WORD_IN_ORDER}), чем «не по порядку» ({@link #SCORE_MULTI_WORD_UNORDERED});
 * совпадения не по порядку не отфильтровываются, лишь понижаются в приоритете.
 * <p>
 * Порядок релевантности (от лучшего к худшему): точное имя → префикс полного имени → многословное
 * in-order camel-hump совпадение ({@link #SCORE_MULTI_WORD_IN_ORDER}) → префикс начала одиночного
 * слова из середины имени ({@link #SCORE_WORD_PREFIX}) → многословное совпадение не по порядку
 * ({@link #SCORE_MULTI_WORD_UNORDERED}).
 * <p>
 * Быстрый путь {@link #search(String, CancelChecker)} идёт ИСКЛЮЧИТЕЛЬНО по дереву — линейного скана
 * по всем записям в нём нет ни в одном пути. Произвольная подстрока ВНУТРИ слова (не с его начала) и
 * подпоследовательность вразброс в {@code search} НЕ участвуют: запрос находит запись только как
 * префикс полного имени, как префикс начала любого CamelCase-слова или как многословное пересечение
 * по фрагментам. Полное совпадение имени — максимальный ранг, совпадения по подсловам — ранг ниже.
 * <p>
 * «Грязный» fuzzy-хвост (непрерывная подстрока внутри слова и разбросанная подпоследовательность)
 * вынесен в ОТДЕЛЬНЫЙ метод {@link #searchFuzzyTail(String, Collection, CancelChecker)}. Это медленный
 * линейный путь по снимку уникальных записей, предназначенный для потоковой дослыки
 * нижнеранжированных результатов через partial result progress; в синхронный ответ {@code search}
 * он НЕ входит.
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
   * Минимальное число CamelCase-фрагментов запроса, при котором включается многословный
   * (camel-hump) путь поиска через пересечение множеств записей по фрагментам.
   */
  private static final int MIN_MULTI_WORD_FRAGMENTS = 2;

  /**
   * Скор точного совпадения имени с запросом (наиболее релевантно).
   */
  private static final int SCORE_EXACT = 0;

  /**
   * Скор совпадения полного имени по префиксу запроса.
   */
  private static final int SCORE_PREFIX = 1;

  /**
   * Скор многословного camel-hump совпадения, где каждый фрагмент запроса — префикс отдельного
   * CamelCase-слова имени, и фрагменты назначаются словам строго по возрастанию индексов (порядок
   * фрагментов совпадает с порядком слов). Например, запрос {@code ПрДок} для {@code ПровестиДокумент}.
   * Ранжируется выше префикса начала одиночного слова из середины имени
   * ({@link #SCORE_WORD_PREFIX}).
   */
  private static final int SCORE_MULTI_WORD_IN_ORDER = 2;

  /**
   * Скор совпадения по префиксу начала CamelCase-слова имени (запрос — префикс одного из слов,
   * но не префикс полного имени). Например, запрос {@code Док} для имени {@code ПровестиДокумент}.
   * Ранжируется ниже многословного in-order совпадения ({@link #SCORE_MULTI_WORD_IN_ORDER}).
   */
  private static final int SCORE_WORD_PREFIX = 3;

  /**
   * Скор многословного camel-hump совпадения, где каждый фрагмент запроса — префикс отдельного
   * CamelCase-слова имени, но строго возрастающего по порядку назначения добиться нельзя (фрагменты
   * совпадают со словами не по порядку). Например, запрос {@code ПрДок} для {@code ДокументПровести}.
   */
  private static final int SCORE_MULTI_WORD_UNORDERED = 4;

  /**
   * Скор совпадения запроса как непрерывной подстроки имени (но не префикса слова). Относится к
   * fuzzy-хвосту ({@link #searchFuzzyTail(String, Collection, CancelChecker)}), а не к древесному
   * {@link #search(String, CancelChecker)}.
   */
  private static final int SCORE_SUBSTRING = 5;

  /**
   * Базовый скор совпадения запроса как подпоследовательности имени; к нему прибавляется
   * позиция первого совпавшего символа (более ранняя позиция — релевантнее). Относится к
   * fuzzy-хвосту ({@link #searchFuzzyTail(String, Collection, CancelChecker)}), а не к древесному
   * {@link #search(String, CancelChecker)}.
   */
  private static final int SCORE_SUBSEQUENCE = 6;

  private static final Set<VariableKind> SUPPORTED_VARIABLE_KINDS = EnumSet.of(
    VariableKind.MODULE,
    VariableKind.GLOBAL
  );

  /**
   * Дерево записей по ключам = lowercase-суффиксы имени от начала каждого CamelCase-слова.
   * <p>
   * Одна запись присутствует под несколькими ключами (полное имя и начало каждого слова), поэтому
   * при поиске нужен дедуп, а при сбросе — удаление по всем ключам записи. Несколько символов с
   * одинаковым ключом хранятся одним списком. Не потокобезопасен — любой доступ только под
   * {@link #lock}.
   */
  private final PatriciaTrie<List<Entry>> trie = new PatriciaTrie<>();

  /**
   * Уникальные записи, добавленные в индекс каждым URI.
   * <p>
   * Поиск идёт только по дереву и эту карту не сканирует. Служит двум целям: точечный сброс по URI
   * на событии жизненного цикла и полная выдача на пустой запрос (в дереве запись лежит под
   * несколькими ключами, и обход дерева вернул бы дубли и стоил бы кратно их числу). Меняется и
   * читается под {@link #lock} согласованно с {@link #trie}.
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
   * Каждая запись лежит под несколькими ключами (полное имя и начало каждого CamelCase-слова),
   * поэтому удаляется из ВСЕХ своих ключей: их множество детерминированно пересчитывается из имени
   * записи методом {@link #keysOf(String)} (обратная карта не нужна). И {@link #indexedByUri}, и
   * {@link #trie} меняются под одним write-lock, чтобы search (read-lock), читающий обе структуры,
   * не увидел запись удалённой из одной раньше другой; ключи, оставшиеся без записей, удаляются из
   * дерева, чтобы не копить пустые узлы.
   *
   * @param uri URI документа
   */
  @Override
  public void clear(URI uri) {
    lock.writeLock().lock();
    try {
      var removed = indexedByUri.remove(uri);
      if (removed == null || removed.isEmpty()) {
        return;
      }
      var removedSet = Collections.<Entry>newSetFromMap(new IdentityHashMap<>());
      removedSet.addAll(removed);
      for (var entry : removed) {
        for (var key : keysOf(entry.name())) {
          removeEntryFromKey(key, removedSet);
        }
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Найти символы рабочей области по запросу с ранжированием по релевантности.
   * <p>
   * Сопоставление регистронезависимое. Скоринг (меньше — релевантнее): точное совпадение,
   * затем префикс полного имени, затем многословное camel-hump совпадение по порядку, затем префикс
   * начала CamelCase-слова (запрос — начало слова из середины имени), затем многословное совпадение
   * не по порядку.
   * При равном скоре раньше идёт более короткое имя, затем — более ранняя позиция в документе.
   * Несовпавшие записи отбрасываются. Выдача возвращается целиком, без усечения: пустой запрос отдаёт
   * все записи индекса, непустой — все совпадения, отсортированные по релевантности.
   * <p>
   * Поиск идёт ИСКЛЮЧИТЕЛЬНО по дереву через {@link PatriciaTrie#prefixMap(Object)}, без линейного
   * скана записей: сублинейно обслуживаются и префикс полного имени, и префикс начала любого слова
   * (записи проиндексированы под суффиксами от начала каждого слова), а многословный (≥2
   * CamelCase-фрагмента) запрос — сублинейным пересечением множеств записей по фрагментам. Это быстрый
   * путь. Произвольная подстрока внутри слова и подпоследовательность вразброс сюда НЕ входят: они
   * доступны отдельно через {@link #searchFuzzyTail(String, Collection, CancelChecker)} для потоковой выдачи
   * через partial result progress и в синхронный ответ {@code search} не попадают.
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
      return searchMatching(query, lowerQuery, cancelChecker);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Собрать совпадения непустого запроса с ранжированием — исключительно по дереву, без линейного
   * скана.
   * <p>
   * Всегда выполняется сублинейный префиксный путь через {@link PatriciaTrie#prefixMap(Object)}: он
   * покрывает и префикс полного имени, и префикс начала любого CamelCase-слова (записи
   * проиндексированы под суффиксами от начала каждого слова), поэтому однословный запрос полностью
   * обслуживается им одним. Если запрос режется на ≥2 CamelCase-фрагмента, дополнительно выполняется
   * сублинейное пересечение множеств записей по фрагментам. Произвольная подстрока внутри слова и
   * подпоследовательность вразброс намеренно НЕ поддерживаются.
   * <p>
   * Одна запись присутствует под несколькими ключами и может совпасть по нескольким из них, поэтому
   * результат дедуплицируется по идентичности записи с сохранением лучшего (наименьшего) скора.
   *
   * @param query         исходный запрос пользователя (с регистром, для CamelCase-разбиения)
   * @param lowerQuery    lowercase-запрос (непустой)
   * @param cancelChecker проверяющий отмену запроса
   * @return ранжированный список совпадений без дублей
   */
  private List<Entry> searchMatching(String query, String lowerQuery, CancelChecker cancelChecker) {
    Map<Entry, Integer> bestScore = new IdentityHashMap<>();
    var progress = new ScanProgress(cancelChecker);

    var fragments = queryFragments(query);

    collectPrefixMatches(lowerQuery, bestScore, progress);
    if (fragments.size() >= MIN_MULTI_WORD_FRAGMENTS) {
      collectMultiWordMatches(fragments, bestScore, progress);
    }

    var matches = new ArrayList<Scored>(bestScore.size());
    for (var scored : bestScore.entrySet()) {
      matches.add(new Scored(scored.getValue(), scored.getKey()));
    }
    matches.sort(Comparator.naturalOrder());
    return matches.stream().map(Scored::entry).toList();
  }

  /**
   * Найти «грязный» fuzzy-хвост выдачи: совпадения по непрерывной подстроке ВНУТРИ слова и по
   * разбросанной подпоследовательности, которых нет в древесном {@link #search(String, CancelChecker)}.
   * <p>
   * Метод намеренно отделён от {@code search}: это медленный линейный путь, предназначенный для
   * потоковой дослыки нижнеранжированных результатов через partial result progress, а не для
   * синхронного ответа. Возвращаются только записи, которых НЕТ в {@code exclude} (сравнение по
   * идентичности), отсортированные тем же {@link Scored}-порядком: подстрочные
   * ({@link #SCORE_SUBSTRING}) выше подпоследовательностных ({@link #SCORE_SUBSEQUENCE}{@code  + позиция}),
   * при равном скоре раньше более короткое имя, затем более ранняя позиция. Пустой запрос даёт пустой
   * список (полная выдача — это путь пустого запроса {@code search}, fuzzy там не нужен).
   * <p>
   * Потокобезопасность: под read-lock снимается СНИМОК уникальных записей ({@link #indexedByUri}),
   * затем блокировка отпускается, и скоринг/фильтрация идут вне блокировки (записи {@link Entry}
   * неизменяемы). Отмена проверяется периодически в ходе скана; при отмене бросается
   * {@link java.util.concurrent.CancellationException}.
   *
   * @param query         строка запроса пользователя; пустая строка даёт пустой результат
   * @param exclude       записи, уже отданные быстрым путём, исключаемые из хвоста (по идентичности)
   * @param cancelChecker проверяющий отмену запроса
   * @return ранжированный список fuzzy-совпадений, не пересекающийся с {@code exclude}
   */
  public List<Entry> searchFuzzyTail(String query, Collection<Entry> exclude, CancelChecker cancelChecker) {
    cancelChecker.checkCanceled();

    var lowerQuery = query.toLowerCase(Locale.ENGLISH);
    if (lowerQuery.isEmpty()) {
      return List.of();
    }

    var snapshot = snapshotEntries();

    var progress = new ScanProgress(cancelChecker);
    var matches = new ArrayList<Scored>();
    for (var entry : snapshot) {
      progress.advance();
      var fuzzyScore = fuzzyTailScore(entry, lowerQuery, exclude);
      if (fuzzyScore >= 0) {
        matches.add(new Scored(fuzzyScore, entry));
      }
    }
    matches.sort(Comparator.naturalOrder());
    return matches.stream().map(Scored::entry).toList();
  }

  /**
   * Вычислить скор записи для fuzzy-хвоста, учитывая исключение уже отданных быстрым путём записей.
   * <p>
   * Запись, присутствующая в {@code exclude} (сравнение по идентичности), отбрасывается ({@code -1}),
   * иначе возвращается её fuzzy-скор {@link #fuzzyScore(String, String)} (тоже {@code -1}, если
   * совпадения нет). Выделено из цикла
   * {@link #searchFuzzyTail(String, Collection, CancelChecker)}, чтобы условия отбора не приходилось
   * выражать множественными {@code continue}.
   *
   * @param entry      запись-кандидат
   * @param lowerQuery lowercase-запрос (непустой)
   * @param exclude    записи, уже отданные быстрым путём (сравнение по идентичности)
   * @return скор {@code >= SCORE_SUBSTRING}, либо {@code -1}, если запись исключена или не совпала
   */
  private static int fuzzyTailScore(Entry entry, String lowerQuery, Collection<Entry> exclude) {
    if (exclude.contains(entry)) {
      return -1;
    }
    return fuzzyScore(entry.lowerName(), lowerQuery);
  }

  /**
   * Снять под read-lock снимок всех уникальных записей индекса, чтобы дальнейший скан шёл вне
   * блокировки.
   * <p>
   * Записи {@link Entry} неизменяемы, поэтому скоринг по снимку после отпускания блокировки
   * безопасен и не держит read-lock на всё время медленного линейного прохода.
   *
   * @return новый список всех уникальных записей индекса на момент вызова
   */
  private List<Entry> snapshotEntries() {
    lock.readLock().lock();
    try {
      var snapshot = new ArrayList<Entry>();
      for (var entries : indexedByUri.values()) {
        snapshot.addAll(entries);
      }
      return snapshot;
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Вычислить скор «не-префиксного» fuzzy-совпадения lowercase-имени с lowercase-запросом.
   * <p>
   * Меньшее значение — релевантнее: {@link #SCORE_SUBSTRING} — непрерывная подстрока,
   * {@link #SCORE_SUBSEQUENCE}{@code  + позиция первого совпавшего символа} — подпоследовательность.
   *
   * @param lowerName  lowercase-имя символа
   * @param lowerQuery lowercase-запрос (непустой)
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
   * @param lowerQuery lowercase-запрос (непустой)
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

  /**
   * Разбить запрос на CamelCase-фрагменты для многословного быстрого пути.
   * <p>
   * Берётся ИСХОДНЫЙ запрос (с регистром, иначе CamelCase-границы потеряются) и режется
   * {@link StringUtils#splitByCharacterTypeCamelCase(String)}; оставляются только фрагменты,
   * начинающиеся с буквы или цифры (отбрасываются разделители вроде пробелов и знаков препинания),
   * каждый приводится к нижнему регистру. Например, {@code ПрДок} → [{@code пр}, {@code док}];
   * {@code получитьСсылку} → [{@code получить}, {@code ссылку}]; {@code документ} (одно слово,
   * нижний регистр) → [{@code документ}].
   *
   * @param query исходный запрос пользователя (непустой, с регистром)
   * @return список lowercase-фрагментов в порядке следования в запросе
   */
  private static List<String> queryFragments(String query) {
    var fragments = new ArrayList<String>();
    for (var fragment : StringUtils.splitByCharacterTypeCamelCase(query)) {
      if (!fragment.isEmpty() && Character.isLetterOrDigit(fragment.charAt(0))) {
        fragments.add(fragment.toLowerCase(Locale.ENGLISH));
      }
    }
    return fragments;
  }

  /**
   * Быстрый многословный путь: пересечение множеств записей по каждому фрагменту через
   * {@link PatriciaTrie#prefixMap(Object)}. Запись попадает в результат, только если у неё есть слово,
   * начинающееся с КАЖДОГО фрагмента (запись присутствует в дереве под суффиксом от начала каждого
   * своего слова). Пересечение начинается с самого мелкого множества фрагментов, чтобы оставаться
   * дешёвым. Каждой выжившей записи назначается скор по порядку фрагментов
   * ({@link #SCORE_MULTI_WORD_IN_ORDER} либо {@link #SCORE_MULTI_WORD_UNORDERED}) и сливается в
   * накопитель через {@link Integer#min}, чтобы не понизить запись, уже найденную с лучшим скором
   * (точное имя или префикс полного имени). Линейный скан в этом пути не выполняется.
   *
   * @param fragments lowercase-фрагменты запроса (≥2)
   * @param bestScore накопитель лучшего скора на запись (по идентичности)
   * @param progress  счётчик прогресса для периодической проверки отмены
   */
  private void collectMultiWordMatches(
    List<String> fragments,
    Map<Entry, Integer> bestScore,
    ScanProgress progress
  ) {
    var perFragment = new ArrayList<Set<Entry>>(fragments.size());
    for (var fragment : fragments) {
      var entries = entriesByPrefix(fragment, progress);
      if (entries.isEmpty()) {
        // нет слова под этот фрагмент — пересечение заведомо пусто
        return;
      }
      perFragment.add(entries);
    }

    var intersection = intersect(perFragment, progress);
    for (var entry : intersection) {
      progress.advance();
      var score = inOrder(entry, fragments) ? SCORE_MULTI_WORD_IN_ORDER : SCORE_MULTI_WORD_UNORDERED;
      bestScore.merge(entry, score, Integer::min);
    }
  }

  /**
   * Собрать множество записей, у которых есть CamelCase-слово, начинающееся с данного фрагмента.
   * <p>
   * Берутся все бакеты дерева по {@link PatriciaTrie#prefixMap(Object)} (ключи — суффиксы имени от
   * начала каждого слова), их записи объединяются по идентичности.
   *
   * @param fragment lowercase-фрагмент запроса
   * @param progress счётчик прогресса для периодической проверки отмены
   * @return множество записей (по идентичности), у которых слово начинается с фрагмента
   */
  private Set<Entry> entriesByPrefix(String fragment, ScanProgress progress) {
    Set<Entry> entries = Collections.newSetFromMap(new IdentityHashMap<>());
    for (var bucket : trie.prefixMap(fragment).values()) {
      for (var entry : bucket) {
        progress.advance();
        entries.add(entry);
      }
    }
    return entries;
  }

  /**
   * Пересечь множества записей по фрагментам, начиная с самого мелкого, чтобы перебор был дешёвым.
   *
   * @param perFragment непустой список непустых множеств записей (по идентичности), по одному на фрагмент
   * @param progress    счётчик прогресса для периодической проверки отмены
   * @return множество записей, присутствующих во всех множествах (по идентичности)
   */
  private static Set<Entry> intersect(List<Set<Entry>> perFragment, ScanProgress progress) {
    var smallest = perFragment.get(0);
    for (var candidate : perFragment) {
      if (candidate.size() < smallest.size()) {
        smallest = candidate;
      }
    }
    Set<Entry> result = Collections.newSetFromMap(new IdentityHashMap<>());
    for (var entry : smallest) {
      progress.advance();
      var inAll = true;
      for (var set : perFragment) {
        if (!set.contains(entry)) {
          inAll = false;
          break;
        }
      }
      if (inAll) {
        result.add(entry);
      }
    }
    return result;
  }

  /**
   * Проверить, что фрагменты запроса назначаются словам имени строго по возрастанию индексов
   * (порядок фрагментов совпадает с порядком слов).
   * <p>
   * Имя режется на lowercase-слова тем же сплиттером. Фрагменты идут слева направо; для каждого
   * жадно берётся слово с наименьшим индексом строго больше предыдущего назначенного, начинающееся
   * с фрагмента. Если всем фрагментам нашлось строго возрастающее назначение — порядок соблюдён.
   *
   * @param entry     запись-кандидат (уже прошла пересечение, каждый фрагмент совпадает с каким-то словом)
   * @param fragments lowercase-фрагменты запроса (≥2)
   * @return {@code true}, если фрагменты назначаются словам строго по возрастанию; иначе {@code false}
   */
  private static boolean inOrder(Entry entry, List<String> fragments) {
    var rawWords = StringUtils.splitByCharacterTypeCamelCase(entry.name());
    var words = new String[rawWords.length];
    for (var i = 0; i < rawWords.length; i++) {
      words[i] = rawWords[i].toLowerCase(Locale.ENGLISH);
    }
    var previousWordIndex = -1;
    for (var fragment : fragments) {
      var assigned = -1;
      for (var wordIndex = previousWordIndex + 1; wordIndex < words.length; wordIndex++) {
        if (words[wordIndex].startsWith(fragment)) {
          assigned = wordIndex;
          break;
        }
      }
      if (assigned < 0) {
        return false;
      }
      previousWordIndex = assigned;
    }
    return true;
  }

  /**
   * Быстрый путь: префиксные совпадения сублинейно через {@link PatriciaTrie#prefixMap(Object)}.
   * Поскольку записи проиндексированы под суффиксами от начала каждого CamelCase-слова, префиксный
   * путь покрывает и совпадения по началу слова из середины имени. Скор записи определяется по
   * совпавшему ключу: запрос == полное имя — {@link #SCORE_EXACT}; запрос — префикс полного имени —
   * {@link #SCORE_PREFIX}; запрос — префикс ключа начала слова (не полного имени) —
   * {@link #SCORE_WORD_PREFIX}. У записи берётся лучший (наименьший) скор по всем совпавшим ключам.
   *
   * @param lowerQuery lowercase-запрос (непустой)
   * @param bestScore  накопитель лучшего скора на запись (по идентичности)
   * @param progress   счётчик прогресса для периодической проверки отмены
   */
  private void collectPrefixMatches(
    String lowerQuery,
    Map<Entry, Integer> bestScore,
    ScanProgress progress
  ) {
    for (var bucket : trie.prefixMap(lowerQuery).entrySet()) {
      var key = bucket.getKey();
      for (var entry : bucket.getValue()) {
        progress.advance();
        var score = prefixScore(key, lowerQuery, entry);
        bestScore.merge(entry, score, Integer::min);
      }
    }
  }

  /**
   * Скор префиксного совпадения записи по конкретному ключу дерева.
   *
   * @param key        ключ дерева, начавшийся с запроса
   * @param lowerQuery lowercase-запрос (непустой, префикс ключа)
   * @param entry      запись под этим ключом
   * @return {@link #SCORE_EXACT}, {@link #SCORE_PREFIX} или {@link #SCORE_WORD_PREFIX}
   */
  private static int prefixScore(String key, String lowerQuery, Entry entry) {
    var isFullName = key.equals(entry.lowerName());
    if (isFullName) {
      return key.equals(lowerQuery) ? SCORE_EXACT : SCORE_PREFIX;
    }
    return SCORE_WORD_PREFIX;
  }

  /**
   * Собрать все записи индекса (для пустого запроса).
   * <p>
   * Проход идёт по УНИКАЛЬНЫМ записям ({@link #indexedByUri}), а не по ключам дерева: запись лежит
   * под несколькими ключами, и обход дерева вернул бы дубли и стоил бы кратно числу ключей.
   *
   * @param cancelChecker проверяющий отмену запроса
   * @return все записи индекса
   */
  private List<Entry> collectAll(CancelChecker cancelChecker) {
    var result = new ArrayList<Entry>();
    var progress = new ScanProgress(cancelChecker);
    for (var entries : indexedByUri.values()) {
      for (var entry : entries) {
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

    var snapshot = List.copyOf(collected);

    lock.writeLock().lock();
    try {
      // indexedByUri и trie держатся согласованными под одним write-lock: search (read-lock)
      // читает обе структуры (trie — весь поиск, indexedByUri — полная выдача на пустой запрос)
      // и не должен увидеть запись в одной из них раньше другой.
      indexedByUri.put(uri, snapshot);
      for (var entry : snapshot) {
        for (var key : keysOf(entry.name())) {
          trie.merge(key, List.of(entry), WorkspaceSymbolIndex::concat);
        }
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Вычислить ключи дерева для имени символа: lowercase-суффиксы имени, начинающиеся с каждого
   * CamelCase-слова.
   * <p>
   * Имя режется на слова {@link StringUtils#splitByCharacterTypeCamelCase(String)} (кириллица
   * режется корректно), затем для каждой границы слова берётся суффикс ИСХОДНОГО имени от этой
   * границы до конца и приводится к нижнему регистру. Первый ключ всегда равен полному lowercase-имени
   * (граница первого слова — позиция 0). Берётся именно суффикс-от-начала-слова, а не изолированное
   * слово: так префиксный поиск ловит и хвост из нескольких слов (запрос {@code ДокОтбора} для
   * {@code ПровестиДокументОтбора} находится по ключу {@code документотбора}). Ключи дедуплицируются
   * (имя из одного слова даёт единственный ключ).
   *
   * @param name исходное имя символа (непустое)
   * @return упорядоченное множество ключей дерева без дублей (как минимум полное lowercase-имя)
   */
  private static Set<String> keysOf(String name) {
    var keys = new LinkedHashSet<String>();
    var lowerName = name.toLowerCase(Locale.ENGLISH);
    keys.add(lowerName);
    var offset = 0;
    for (var word : StringUtils.splitByCharacterTypeCamelCase(name)) {
      if (offset > 0) {
        keys.add(lowerName.substring(offset));
      }
      offset += word.length();
    }
    return keys;
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
      // Function — методы модулей без состояния (общие модули BSL, модули OneScript)
      case Method, Function, Constructor -> true;
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
