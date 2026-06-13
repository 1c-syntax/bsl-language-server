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
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.SymbolTag;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Инкрементальный индекс символов рабочей области для запросов {@code workspace/symbol}.
 * <p>
 * Заменяет полный обход всех документов с последующим слепым усечением выдачи: символы
 * собираются один раз на событии {@link DocumentContextContentChangedEvent} в лёгкие
 * неизменяемые записи {@link Entry} (имя, его lowercase-форма, {@link SymbolKind}, диапазон,
 * URI, теги и готовое имя контейнера). Запрос обслуживается из индекса с ранжированием по
 * релевантности; выдача возвращается целиком, без усечения.
 * <p>
 * Для быстрого первого отсева записи разложены по корзинам по первой букве lowercase-имени
 * ({@code charBuckets}); записи с пустым именем хранятся отдельно ({@code emptyNameBucket}).
 * Это избавляет от полного линейного скана всех символов для непустых запросов: достаточно
 * пройти корзины, чьи имена могут содержать первый символ запроса.
 * <p>
 * Индекс наследует {@link AbstractDocumentLifecycleClearableIndex}: его {@code @EventListener}'ы
 * сбрасывают записи документа на изменение содержимого, освобождение данных, закрытие и удаление.
 * <p>
 * Потокобезопасность: хранилища — {@link ConcurrentHashMap}; записи неизменяемы. Поиск работает
 * с моментальными снимками корзин и не удерживает блокировок, поэтому индекс безопасно читать из
 * нескольких потоков, пока другой поток переиндексирует документ.
 */
@Component
@WorkspaceScope
public class WorkspaceSymbolIndex extends AbstractDocumentLifecycleClearableIndex {

  /**
   * Частота проверки отмены запроса (раз в N просмотренных записей).
   */
  private static final int CANCEL_CHECK_INTERVAL = 1024;

  private static final Set<VariableKind> SUPPORTED_VARIABLE_KINDS = EnumSet.of(
    VariableKind.MODULE,
    VariableKind.GLOBAL
  );

  /**
   * Записи, сгруппированные по первой букве lowercase-имени.
   * <p>
   * Значение каждой корзины — неизменяемый снимок {@link List}, заменяемый целиком при
   * переиндексации, поэтому читатели всегда видят согласованный список без блокировок.
   */
  private final ConcurrentMap<Character, List<Entry>> charBuckets = new ConcurrentHashMap<>();

  /**
   * Записи, добавленные в индекс каждым URI — для точечного сброса по событию жизненного цикла.
   */
  private final ConcurrentMap<URI, List<Entry>> indexedByUri = new ConcurrentHashMap<>();

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
   *
   * @param uri URI документа
   */
  @Override
  public void clear(URI uri) {
    var removed = indexedByUri.remove(uri);
    if (removed == null || removed.isEmpty()) {
      return;
    }
    var removedSet = Collections.newSetFromMap(new IdentityHashMap<Entry, Boolean>());
    removedSet.addAll(removed);

    for (var entry : removed) {
      var key = entry.lowerName().charAt(0);
      charBuckets.computeIfPresent(key, (ignored, bucket) -> {
        var filtered = withoutEntries(bucket, removedSet);
        return filtered.isEmpty() ? null : filtered;
      });
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
   * Отмена проверяется периодически в ходе сканирования; при отмене бросается
   * {@link java.util.concurrent.CancellationException}.
   *
   * @param query         строка запроса пользователя; {@code null} трактуется как пустая строка
   * @param cancelChecker проверяющий отмену запроса
   * @return полный ранжированный список найденных записей
   */
  public List<Entry> search(String query, CancelChecker cancelChecker) {
    cancelChecker.checkCanceled();

    var normalizedQuery = query == null ? "" : query;
    var lowerQuery = normalizedQuery.toLowerCase(Locale.ENGLISH);

    if (lowerQuery.isEmpty()) {
      return collectAll(cancelChecker);
    }

    var matches = new ArrayList<Scored>();
    var seen = 0;

    for (var bucket : charBuckets.values()) {
      for (var entry : bucket) {
        if ((++seen & (CANCEL_CHECK_INTERVAL - 1)) == 0) {
          cancelChecker.checkCanceled();
        }
        var score = score(entry.lowerName(), lowerQuery);
        if (score < 0) {
          continue;
        }
        matches.add(new Scored(score, entry));
      }
    }

    matches.sort(Comparator.naturalOrder());
    return matches.stream().map(Scored::entry).toList();
  }

  /**
   * Собрать все записи индекса в детерминированном порядке (для пустого запроса).
   *
   * @param cancelChecker проверяющий отмену запроса
   * @return все записи индекса
   */
  private List<Entry> collectAll(CancelChecker cancelChecker) {
    var result = new ArrayList<Entry>();
    var seen = 0;
    for (var bucket : charBuckets.values()) {
      for (var entry : bucket) {
        if ((++seen & (CANCEL_CHECK_INTERVAL - 1)) == 0) {
          cancelChecker.checkCanceled();
        }
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
    for (var entry : collected) {
      var key = entry.lowerName().charAt(0);
      charBuckets.merge(key, List.of(entry), WorkspaceSymbolIndex::concat);
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
   * Вычислить скор совпадения lowercase-имени с lowercase-запросом.
   * <p>
   * Меньшее значение — релевантнее: {@code 0} — точное совпадение, {@code 1} — совпадение по
   * префиксу, {@code 2} — непрерывная подстрока, {@code 3 + позиция} — подпоследовательность.
   *
   * @param lowerName  lowercase-имя символа
   * @param lowerQuery lowercase-запрос (непустой)
   * @return скор {@code >= 0}, либо {@code -1}, если совпадения нет
   */
  private static int score(String lowerName, String lowerQuery) {
    if (lowerName.equals(lowerQuery)) {
      return 0;
    }
    if (lowerName.startsWith(lowerQuery)) {
      return 1;
    }
    if (lowerName.contains(lowerQuery)) {
      return 2;
    }
    var firstMatch = subsequenceFirstIndex(lowerName, lowerQuery);
    if (firstMatch >= 0) {
      return 3 + firstMatch;
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
   * Неизменяемая запись индекса: всё необходимое для построения
   * {@link org.eclipse.lsp4j.WorkspaceSymbol} без повторного обхода дерева символов.
   *
   * @param uri           URI документа-источника
   * @param name          исходное имя символа
   * @param lowerName     lowercase-форма имени для сопоставления
   * @param kind          вид символа
   * @param range         диапазон символа в документе
   * @param tags          теги символа (например, {@link SymbolTag#Deprecated})
   * @param containerName готовое имя контейнера (представление ссылки на объект метаданных) либо
   *                      пустая строка, если документ не связан с объектом метаданных
   */
  public record Entry(
    URI uri,
    String name,
    String lowerName,
    SymbolKind kind,
    Range range,
    List<SymbolTag> tags,
    String containerName
  ) {
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
