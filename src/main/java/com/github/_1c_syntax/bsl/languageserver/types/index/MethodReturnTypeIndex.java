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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentClearedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentClosedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextPopulatedEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.index.AbstractDocumentLifecycleClearableIndex;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Типы возвращаемого значения методов, рассчитанные по их телам.
 * <p>
 * Расчёт идёт в момент построения контекста документа, пока его дерево разбора под рукой:
 * читать дерево чужого документа нельзя — его вторичные данные могут быть освобождены.
 * Потребители берут из индекса готовое значение и в чужие документы не заходят.
 * <p>
 * Значение метода зависит от значений тех методов, которые вызваны в его выражениях
 * возврата. Поэтому индекс держит обратные связи: изменилось значение метода — в работу
 * уходят его потребители, и так по цепочке. Набор типов при пересчёте только растёт,
 * поэтому взаимная рекурсия сходится; на всякий случай число проходов ограничено.
 * <p>
 * Значение считается по тому, что известно на момент разбора документа. Пока рабочая
 * область наполняется, часть документов ещё не зарегистрирована, и вызовы в них не
 * резолвятся — такое значение остаётся неполным до следующего разбора своего документа
 * (открытие в редакторе, правка, пакетный анализ).
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class MethodReturnTypeIndex extends AbstractDocumentLifecycleClearableIndex {

  /** Предохранитель на случай незамеченной немонотонности пересчёта. */
  private static final int MAX_PASSES = 10;

  private final ObjectProvider<ExpressionTypeInferencer> inferencerProvider;

  private final Map<MethodSymbol, TypeSet> typesByMethod = new ConcurrentHashMap<>();
  private final Map<MethodSymbol, Set<MethodSymbol>> dependentsByMethod = new ConcurrentHashMap<>();
  private final Map<MethodSymbol, Set<MethodSymbol>> dependenciesByMethod = new ConcurrentHashMap<>();
  private final Map<URI, List<MethodSymbol>> methodsByUri = new ConcurrentHashMap<>();

  /** Идёт общий проход по рабочей области: разбор документа в нём — не правка, а догрузка. */
  private volatile boolean maintenance;

  /** Методы, изменившиеся в ходе общего прохода и ждущие разноса по потребителям. */
  private final Set<MethodSymbol> pending = ConcurrentHashMap.newKeySet();

  /**
   * Типы, которые метод возвращает по своему телу.
   *
   * @param method метод.
   * @return рассчитанные типы; {@link TypeSet#EMPTY}, если тело ещё не разбиралось либо
   *     не дало типов.
   */
  public TypeSet get(MethodSymbol method) {
    return typesByMethod.getOrDefault(method, TypeSet.EMPTY);
  }

  /**
   * Пересчитывает методы документа, содержимое которого изменилось, и разносит изменение
   * по зависимым.
   * <p>
   * Порядок после индексов символов и ссылок: расчёт опирается на них, разрешая вызовы
   * в выражениях возврата.
   *
   * @param event событие изменения содержимого документа.
   */
  @Order(300)
  @EventListener
  @Override
  public void handleContentChanged(DocumentContextContentChangedEvent event) {
    var documentContext = event.getSource();
    clear(documentContext.getUri());
    var changed = recomputeDocument(documentContext);
    if (maintenance) {
      // Идёт общий проход: он сам разнесёт изменения, когда переберёт все документы.
      pending.addAll(changed);
      return;
    }
    propagate(changed);
  }

  /**
   * Доразрешает ссылки, не разрешившиеся при наполнении рабочей области.
   * <p>
   * Документы разбираются параллельно и в произвольном порядке, поэтому вызов в модуль,
   * ещё не зарегистрированный на тот момент, никуда не вёл, а значение вызывающего метода
   * оставалось неполным. Теперь зарегистрированы все, и каждый документ пересчитывается
   * заново — с догрузкой, если его вторичные данные уже освобождены, и с возвратом в
   * прежнее состояние после расчёта.
   *
   * @param event событие наполнения рабочей области.
   */
  @EventListener
  public void handleServerContextPopulated(ServerContextPopulatedEvent event) {
    var serverContext = event.getSource();
    maintenance = true;
    try {
      for (var documentContext : List.copyOf(serverContext.getDocuments().values())) {
        recomputeLoading(serverContext, documentContext);
      }
      drainPending(serverContext);
    } finally {
      maintenance = false;
      pending.clear();
    }
  }

  /**
   * Освобождение вторичных данных записи не трогает: ради этого индекс и заведён —
   * содержимое документа не менялось, а дерево символов переживает освобождение, так что
   * посчитанные значения остаются верными и читаются без дерева разбора.
   *
   * @param event событие освобождения вторичных данных документа.
   */
  @Override
  public void handleDataCleared(ServerContextDocumentClearedEvent event) {
    // Записи остаются в силе.
  }

  /**
   * Закрытие документа в редакторе — тоже не правка: содержимое то же самое.
   *
   * @param event событие закрытия документа.
   */
  @Override
  public void handleDocumentClosed(ServerContextDocumentClosedEvent event) {
    // Записи остаются в силе.
  }

  /**
   * Удаляет записи методов документа и снимает их связи.
   *
   * @param uri URI документа.
   */
  @Override
  public void clear(URI uri) {
    var methods = methodsByUri.remove(uri);
    if (methods == null) {
      return;
    }
    for (var method : methods) {
      typesByMethod.remove(method);
      unlinkDependencies(method);
      dependentsByMethod.remove(method);
    }
  }

  /**
   * Пересчитывает все функции документа.
   *
   * @param documentContext документ.
   * @return методы, у которых набор типов изменился.
   */
  private List<MethodSymbol> recomputeDocument(DocumentContext documentContext) {
    var changed = new ArrayList<MethodSymbol>();
    if (!isReadable(documentContext)) {
      return changed;
    }
    var methods = new ArrayList<MethodSymbol>();
    for (var method : documentContext.getSymbolTree().getMethods()) {
      if (!method.isFunction()) {
        continue;
      }
      methods.add(method);
      if (recompute(method)) {
        changed.add(method);
      }
    }
    if (!methods.isEmpty()) {
      methodsByUri.put(documentContext.getUri(), methods);
    }
    return changed;
  }

  /**
   * Пересчитывает один метод и обновляет его связи.
   *
   * @param method метод.
   * @return {@code true}, если набор типов изменился.
   */
  private boolean recompute(MethodSymbol method) {
    var computed = inferencerProvider.getObject().computeReturnTypes(method);
    unlinkDependencies(method);
    for (var dependency : computed.consulted()) {
      if (dependency.equals(method)) {
        continue;
      }
      dependenciesByMethod.computeIfAbsent(method, k -> ConcurrentHashMap.newKeySet()).add(dependency);
      dependentsByMethod.computeIfAbsent(dependency, k -> ConcurrentHashMap.newKeySet()).add(method);
    }
    var previous = computed.types().isEmpty()
      ? typesByMethod.remove(method)
      : typesByMethod.put(method, computed.types());
    return !computed.types().equals(previous == null ? TypeSet.EMPTY : previous);
  }

  /**
   * Разносит изменение значений по потребителям до неподвижной точки.
   * <p>
   * Пересчитываются только те, чьи документы разобраны прямо сейчас: догрузка ради
   * отдельной правки в редакторе слишком дорога. Остальные подтянутся общим проходом
   * либо при следующем разборе своего документа.
   *
   * @param seeds методы, значения которых изменились.
   */
  private void propagate(Collection<MethodSymbol> seeds) {
    var queue = new ArrayDeque<>(dependentsOf(seeds));
    for (var pass = 0; pass < MAX_PASSES && !queue.isEmpty(); pass++) {
      var wave = new ArrayList<MethodSymbol>(queue);
      queue.clear();
      var changed = new ArrayList<MethodSymbol>();
      for (var method : wave) {
        if (isReadable(method) && recompute(method)) {
          changed.add(method);
        }
      }
      queue.addAll(dependentsOf(changed));
    }
  }

  /**
   * Разносит накопленные общим проходом изменения, догружая документы потребителей.
   *
   * @param serverContext рабочая область.
   */
  private void drainPending(ServerContext serverContext) {
    for (var pass = 0; pass < MAX_PASSES && !pending.isEmpty(); pass++) {
      var wave = dependentsOf(List.copyOf(pending));
      pending.clear();
      for (var method : wave) {
        recomputeLoading(serverContext, method.getOwner());
      }
    }
  }

  /**
   * Пересчитывает методы документа, догрузив его, если вторичные данные освобождены, и
   * вернув в прежнее состояние после расчёта.
   * <p>
   * Разбор документа сам публикует событие изменения содержимого, поэтому пересчёт
   * выполняет обработчик этого события — здесь остаётся только довести документ до
   * состояния, в котором его тела читаются.
   *
   * @param serverContext   рабочая область.
   * @param documentContext документ.
   */
  private void recomputeLoading(ServerContext serverContext, DocumentContext documentContext) {
    if (isReadable(documentContext)) {
      pending.addAll(recomputeDocument(documentContext));
      return;
    }
    serverContext.rebuildDocument(documentContext);
    serverContext.tryClearDocument(documentContext);
  }

  /**
   * Потребители перечисленных методов.
   *
   * @param methods методы.
   * @return методы, значения которых на них построены.
   */
  private Set<MethodSymbol> dependentsOf(Collection<MethodSymbol> methods) {
    var result = ConcurrentHashMap.<MethodSymbol>newKeySet();
    for (var method : methods) {
      var dependents = dependentsByMethod.get(method);
      if (dependents != null) {
        result.addAll(dependents);
      }
    }
    return result;
  }

  /** Снимает связи метода с теми, от кого он зависел. */
  private void unlinkDependencies(MethodSymbol method) {
    var dependencies = dependenciesByMethod.remove(method);
    if (dependencies == null) {
      return;
    }
    for (var dependency : dependencies) {
      var dependents = dependentsByMethod.get(dependency);
      if (dependents != null) {
        dependents.remove(method);
      }
    }
  }

  /**
   * Доступно ли дерево разбора документа-владельца.
   *
   * @param method метод.
   * @return {@code true}, если тело метода можно разобрать прямо сейчас.
   */
  private static boolean isReadable(MethodSymbol method) {
    return isReadable(method.getOwner());
  }

  /**
   * Доступно ли дерево разбора документа.
   *
   * @param documentContext документ.
   * @return {@code true}, если тела его методов можно разобрать прямо сейчас.
   */
  private static boolean isReadable(DocumentContext documentContext) {
    try {
      documentContext.getAst();
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }

  /**
   * Результат расчёта типов возврата метода.
   *
   * @param types     рассчитанные типы.
   * @param consulted методы, значения которых участвовали в расчёте.
   */
  public record ComputedReturnTypes(TypeSet types, Set<MethodSymbol> consulted) {
  }
}
