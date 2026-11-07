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

import com.github._1c_syntax.bsl.languageserver.configuration.events.LanguageServerConfigurationChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.events.ConfigurationTypesRegisteredEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentClearedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextPopulatedEvent;
import com.github._1c_syntax.bsl.languageserver.index.AbstractDocumentLifecycleClearableIndex;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.antlr.v4.runtime.tree.ParseTree;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Кэш выведенных типов выражений, разрезанный по URI документа и ключуемый по
 * AST-узлу выражения.
 * <p>
 * Держит типы
 * <b>не-переменных</b> ресиверов — цепочек {@code А.Б.В}, обращений к менеджерам
 * конфигурации ({@code Справочники.X}, {@code РегистрыСведений.Y}) и общим
 * модулям, результатов вызовов. На большом модуле один и тот же ресивер
 * встречается во множестве член-доступов, а вложенные ресиверы цепочки — под
 * каждым охватывающим доступом; без этого кэша {@code ExpressionTypeInferencer}
 * переинферивает их (и триггерит повторный резолв ссылок) десятки раз за проход.
 * <p>
 * Наполняется <b>лениво</b> из {@code ExpressionTypeInferencer.inferInternal}
 * только для «чистого корня» инференса (вне рекурсии символов), когда результат
 * узла не зависит от контекста обхода. Кэшируются и пустые результаты
 * ({@link TypeSet#EMPTY}) — они экономят тот же дорогой резолв.
 * <p>
 * Ключ — сам {@link ParseTree}-узел (identity equals/hashCode ANTLR-контекста);
 * при перепарсинге документа узлы получают новые идентичности, а старый бакет
 * URI сбрасывается по событию изменения содержимого.
 * Завершённые внешние запросы для конкретного режима хранятся отдельно от общего
 * результата. Расчёт получает бакет до начала вывода: сброс входов отклоняет его
 * позднюю публикацию, даже если зависимости ещё не были зарегистрированы.
 * <p>
 * Инвалидация — per-URI
 * через {@link AbstractDocumentLifecycleClearableIndex} (изменение / очистка
 * вторичных данных / закрытие / удаление документа). Правка чужого модуля сбрасывает
 * зависящие от него результаты. Регистрация конфигурационных типов и изменение
 * настроек рабочей области полностью очищают кеш.
 */
@Component
@WorkspaceScope
public class InferredExpressionTypeIndex extends AbstractDocumentLifecycleClearableIndex {

  private final Map<URI, Map<ParseTree, TypeSet>> typesByUri = new ConcurrentHashMap<>();
  private final Map<URI, Map<ContextualKey, TypeSet>> contextualTypesByUri = new ConcurrentHashMap<>();
  private volatile long contextualRevision = 0;

  /** Документ → те, чьи записи на нём построены. Правка документа сбрасывает их вместе с ним. */
  private final Map<URI, Set<URI>> dependentsByDependency = new ConcurrentHashMap<>();

  /** Обратная сторона той же связи — нужна, чтобы снимать её при сбросе документа-потребителя. */
  private final Map<URI, Set<URI>> dependenciesByDependent = new ConcurrentHashMap<>();

  /** Версия входов; меняется при сбросе документов, зависимостей и настроек рабочей области. */
  public long inputRevision() {
    return contextualRevision;
  }

  private record ContextualKey(ParseTree node, @Nullable ExecutionContext context) { }

  /** Снимок бакета до корневого расчёта; сброс не позволяет опубликовать старый результат. */
  public static final class ContextualQuery {
    private final URI uri;
    private final ContextualKey key;
    private final Map<ContextualKey, TypeSet> bucket;
    private final long revision;

    private ContextualQuery(URI uri, ContextualKey key, Map<ContextualKey, TypeSet> bucket, long revision) {
      this.uri = uri;
      this.key = key;
      this.bucket = bucket;
      this.revision = revision;
    }

    /** Уже завершённый результат для этого AST-узла и того же режима. */
    public @Nullable TypeSet cached() {
      return bucket.get(key);
    }
  }

  /** Начать отдельный корневой запрос; результаты разных режимов не объединяются. */
  public ContextualQuery contextualQuery(URI uri, ParseTree node, @Nullable ExecutionContext context) {
    var revision = contextualRevision;
    var bucket = contextualTypesByUri.computeIfAbsent(uri, ignored -> new ConcurrentHashMap<>());
    return new ContextualQuery(uri, new ContextualKey(node, context), bucket, revision);
  }

  /** Публикация полного корневого результата с проверкой, что его входы ещё не сброшены. */
  public synchronized void put(ContextualQuery query, TypeSet types, Set<URI> dependencies) {
    if (query.revision != contextualRevision || contextualTypesByUri.get(query.uri) != query.bucket) {
      return;
    }
    rememberDependencies(query.uri, dependencies);
    query.bucket.put(query.key, types);
  }

  /**
   * Запомнить объединённый внешний ответ, если все отдельные режимы уже опубликованы.
   * Зависимости частей зарегистрированы на том же URI; их сброс удаляет и объединение.
   * Само объединение строится вызывающим кодом вне замка: тип может быть ленивым.
   *
   * @param root нейтральный запрос, начатый до определения его возможных режимов.
   * @param parts запросы для одного узла и документа.
   * @param values использованные полные результаты частей.
   * @param union их объединение.
   * @return удалось ли опубликовать ответ без изменения входов.
   */
  public synchronized boolean putNeutralFromContexts(ContextualQuery root, List<ContextualQuery> parts,
                                                      List<TypeSet> values, TypeSet union) {
    if (parts.isEmpty() || parts.size() != values.size() || root.key.context != null
      || root.revision != contextualRevision || contextualTypesByUri.get(root.uri) != root.bucket) {
      return false;
    }
    for (var i = 0; i < parts.size(); i++) {
      var query = parts.get(i);
      if (!sameRoot(root, query) || !publishedValue(query, values.get(i))) {
        return false;
      }
    }
    root.bucket.put(root.key, union);
    typesByUri.computeIfAbsent(root.uri, ignored -> new ConcurrentHashMap<>()).put(root.key.node, union);
    return true;
  }

  private static boolean sameRoot(ContextualQuery root, ContextualQuery query) {
    if (query.key.context == null || query.revision != root.revision || query.bucket != root.bucket) {
      return false;
    }
    return query.uri.equals(root.uri) && query.key.node == root.key.node;
  }

  private static boolean publishedValue(ContextualQuery query, TypeSet value) {
    return query.cached() != null && query.cached() == value;
  }

  /**
   * Кэшированный тип узла-выражения либо {@code null}, если ещё не вычислялся.
   *
   * @param uri  URI документа, которому принадлежит узел.
   * @param node AST-узел выражения.
   * @return выведенный тип или {@code null} при промахе кэша.
   */
  @Nullable
  public TypeSet get(URI uri, ParseTree node) {
    var byNode = typesByUri.get(uri);
    return byNode == null ? null : byNode.get(node);
  }

  /**
   * Запомнить выведенный тип узла-выражения.
   *
   * @param uri          URI документа, которому принадлежит узел.
   * @param node         AST-узел выражения.
   * @param types        выведенный тип.
   * @param dependencies URI документов, чьё содержимое участвовало в расчёте: их правка
   *                     делает запись недействительной.
   */
  public void put(URI uri, ParseTree node, TypeSet types, Set<URI> dependencies) {
    typesByUri.computeIfAbsent(uri, k -> new ConcurrentHashMap<>()).put(node, types);
    rememberDependencies(uri, dependencies);
  }

  /**
   * Сбрасывает бакет документа и бакеты всех, чьи типы на нём построены.
   *
   * @param uri URI документа.
   */
  @Override
  public synchronized void clear(URI uri) {
    contextualRevision++;
    clearRecursive(uri, Collections.newSetFromMap(new HashMap<>()));
  }

  /**
   * Освобождение вторичных данных — не правка: содержимое документа то же самое,
   * поэтому типы, построенные на нём в других документах, остаются в силе. Сбрасывается
   * только бакет самого документа — ключи в нём указывают на узлы выброшенного дерева
   * разбора.
   *
   * @param event событие освобождения вторичных данных документа.
   */
  @Override
  public synchronized void handleDataCleared(ServerContextDocumentClearedEvent event) {
    contextualRevision++;
    typesByUri.remove(event.getDocumentContext().getUri());
    contextualTypesByUri.remove(event.getDocumentContext().getUri());
  }

  /**
   * Запомнить, на каких документах построены типы этого.
   *
   * @param uri          URI документа-потребителя.
   * @param dependencies URI документов, чьё содержимое участвовало в расчёте.
   */
  private void rememberDependencies(URI uri, Set<URI> dependencies) {
    for (var dependency : dependencies) {
      if (dependency.equals(uri)) {
        continue;
      }
      dependentsByDependency.computeIfAbsent(dependency, k -> ConcurrentHashMap.newKeySet()).add(uri);
      dependenciesByDependent.computeIfAbsent(uri, k -> ConcurrentHashMap.newKeySet()).add(dependency);
    }
  }

  /**
   * Сбрасывает бакет документа и обходит его зависимых.
   *
   * @param uri     URI документа.
   * @param visited уже пройденные документы — обрывает круговые зависимости модулей.
   */
  private void clearRecursive(URI uri, Set<URI> visited) {
    if (!visited.add(uri)) {
      return;
    }
    typesByUri.remove(uri);
    contextualTypesByUri.remove(uri);
    var dependencies = dependenciesByDependent.remove(uri);
    if (dependencies != null) {
      for (var dependency : dependencies) {
        var dependents = dependentsByDependency.get(dependency);
        if (dependents != null) {
          dependents.remove(uri);
        }
      }
    }
    var dependents = dependentsByDependency.remove(uri);
    if (dependents != null) {
      for (var dependent : dependents) {
        clearRecursive(dependent, visited);
      }
    }
  }

  /**
   * Полный сброс после наполнения рабочей области: расчёт по телам методов идёт через
   * ссылки между документами, а до конца наполнения часть документов ещё не
   * зарегистрирована — типы, посчитанные в это время, неполны.
   *
   * @param event событие наполнения рабочей области.
   */
  @EventListener
  public void handleServerContextPopulated(ServerContextPopulatedEvent event) {
    clearAll();
  }

  /**
   * Полный сброс после регистрации конфигурационных типов: до неё член-доступы
   * на конфигурационных типах инферились в пусто (реестр ещё не заполнен), и эти
   * «пустые» результаты надо пересчитать.
   *
   * @param event событие регистрации конфигурационных типов.
   */
  @EventListener
  public void handleConfigurationTypesRegistered(ConfigurationTypesRegisteredEvent event) {
    clearAll();
  }

  /** Сбросить и типы, и связи между документами. */
  private synchronized void clearAll() {
    contextualRevision++;
    typesByUri.clear();
    contextualTypesByUri.clear();
    dependentsByDependency.clear();
    dependenciesByDependent.clear();
  }

  /** Сбросить позиционные типы до обновления подсказок и диагностик по новой конфигурации. */
  @Order(Ordered.HIGHEST_PRECEDENCE)
  @EventListener
  public void handleConfigurationChanged(LanguageServerConfigurationChangedEvent event) {
    clearAll();
  }
}
