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

import com.github._1c_syntax.bsl.languageserver.client.WorkDoneProgressHelper;
import com.github._1c_syntax.bsl.languageserver.configuration.GlobalLanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.Resources;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.DocumentState;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

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
@Slf4j
public class MethodReturnTypeIndexer extends AbstractDocumentLifecycleClearableIndex {

  /** Предохранитель на случай незамеченной немонотонности пересчёта. */
  private static final int MAX_PASSES = 10;

  /** Сколько документов допустимо держать разобранными разом ради обхода цикла. */
  private static final int MAX_DOCUMENTS_IN_MEMORY = 8;

  private final ExpressionTypeInferencer inferencer;
  private final SymbolTypeIndex symbolTypeIndex;

  private final WorkDoneProgressHelper workDoneProgressHelper;
  private final GlobalLanguageServerConfiguration globalConfiguration;

  /**
   * Пул, на котором считаются компоненты одного яруса. Тот же, на котором наполняется
   * рабочая область: его воркеры выставляют себе её URI, без чего workspace-скоуп из
   * чужого потока не резолвится. К началу прохода наполнение закончено, и пул свободен.
   */
  @Qualifier("populateContextExecutor")
  private final ExecutorService resolveReturnTypesExecutor;

  /**
   * Документы, чьи значения построены на этом. Связи держатся по документам, а не по
   * методам: правка пересчитывает документ целиком, поэтому связь метода с соседом по
   * тому же файлу никогда не спрашивается, а хранение по методам стоит на порядок
   * дороже — на реальной конфигурации это сотни тысяч наборов вместо десятка тысяч.
   */
  private final Map<URI, Set<URI>> dependentsByUri = new ConcurrentHashMap<>();

  /** Документы, на значениях которых построен этот. */
  private final Map<URI, Set<URI>> dependenciesByUri = new ConcurrentHashMap<>();

  private final Map<URI, List<MethodSymbol>> methodsByUri = new ConcurrentHashMap<>();

  /** Идёт общий проход по рабочей области: разбор документа в нём — не правка, а догрузка. */
  private volatile boolean maintenance;

  /** Методы, изменившиеся в ходе общего прохода и ждущие разноса по потребителям. */
  private final Set<MethodSymbol> pending = ConcurrentHashMap.newKeySet();

  /** Методы, тела которых уже разбирались: пустой ответ у них значит «ничего не возвращает». */
  private final Set<MethodSymbol> indexed = ConcurrentHashMap.newKeySet();

  /** Сколько раз пришлось перечитать документ за общий проход — для отладочного счёта. */
  private final AtomicInteger rebuilt = new AtomicInteger();

  /** Какие документы перечитывались: волн несколько, и один документ может попасть в разбор не раз. */
  private final Set<URI> rebuiltUris = ConcurrentHashMap.newKeySet();

  /**
   * Считает типы возврата метода по телу, если этого ещё не делалось, и складывает
   * результат в {@link SymbolTypeIndex}.
   * <p>
   * Для неэкспортных методов заранее ничего не считается: они видны только внутри своего
   * документа, а там дерево разбора под рукой, и расчёт по запросу дешевле, чем расчёт
   * всех функций конфигурации при её разборе.
   *
   * @param method      метод.
   * @param computation расчёт в контексте вызывающего: у него общая с ним защита от
   *                    циклов и общая глубина, без которых цепочка вызовов внутри модуля
   *                    уходит в рекурсию до переполнения стека.
   */
  public void computeIfAbsent(MethodSymbol method, Supplier<ComputedReturnTypes> computation) {
    if (indexed.contains(method) || !method.isFunction() || !isReadable(method)) {
      return;
    }
    store(method, computation.get());
    rememberMethodOfUri(method);
  }

  /**
   * Разбиралось ли уже тело метода.
   * <p>
   * Пока рабочая область наполняется, до части модулей очередь ещё не дошла, и пустой
   * ответ у них означает «неизвестно», а не «ничего не возвращает».
   *
   * @param method метод.
   * @return {@code true}, если типы возврата метода уже выводились.
   */
  public boolean isIndexed(MethodSymbol method) {
    return indexed.contains(method);
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
    if (!event.isContentChanged()) {
      // Тот же самый текст перечитан заново — посчитанные по нему значения остаются в силе.
      // Снести и посчитать их заново означало бы, во-первых, оставить окно, в котором чужой
      // поток не находит значений уже посчитанного метода, а во-вторых, потерять точность:
      // расчёт с чистого листа рвёт цепочку взаимных вызовов в другом месте.
      return;
    }
    var documentContext = event.getSource();
    clear(documentContext.getUri());
    var changed = recomputeDocument(documentContext);
    if (maintenance) {
      // Идёт общий проход, и разбор здесь — его же догрузка. Складывать «изменившиеся» в
      // очередь нельзя: записи документа только что стёрты, поэтому изменившимся выглядит
      // каждый его метод, и проход крутил бы одно и то же до предохранителя. Что надо
      // пересчитать, решает сам проход — по методам, чей расчёт видел непосчитанное.
      return;
    }
    propagate(changed);
  }

  /**
   * Доразрешает то, что не разрешилось при наполнении рабочей области.
   * <p>
   * Документы разбираются параллельно и в произвольном порядке, поэтому вызов в модуль,
   * ещё не зарегистрированный на тот момент, никуда не вёл, а значение вызывающего метода
   * оставалось неполным. Пересчитываются не все подряд, а только те, чьи значения
   * зависели от изменившихся: остальным доразрешать нечего.
   *
   * @param event событие наполнения рабочей области.
   */
  @EventListener
  public void handleServerContextPopulated(ServerContextPopulatedEvent event) {
    var serverContext = event.getSource();
    maintenance = true;
    var deferred = pending.size();
    var startedAt = System.nanoTime();
    rebuilt.set(0);
    rebuiltUris.clear();
    var progressReporter = workDoneProgressHelper.createProgress(
      pending.size(),
      getMessage("resolveMethodsPostfix")
    );
    progressReporter.beginProgress(getMessage("resolveReturnTypes"));
    try {
      drainPending(serverContext, progressReporter);
    } finally {
      maintenance = false;
      pending.clear();
      progressReporter.endProgress(getMessage("resolveReturnTypesDone"));
    }
    LOGGER.debug("Доразрешение типов возврата: отложено методов {}, разборов документов {},"
        + " из них различных документов {}, заняло {} мс",
      deferred, rebuilt.get(), rebuiltUris.size(), (System.nanoTime() - startedAt) / 1_000_000);
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
   * Снимает связи документа с теми, на ком он построен, и пометки о разборе его методов.
   * Сами значения живут в {@link SymbolTypeIndex} и стираются им же по тому же событию.
   * <p>
   * Записи о потребителях сохраняются: содержимое документа изменилось, но построены они
   * по-прежнему на нём, и именно им предстоит получить новое значение.
   *
   * @param uri URI документа.
   */
  @Override
  public void clear(URI uri) {
    unlinkDependencies(uri);
    var methods = methodsByUri.remove(uri);
    if (methods != null) {
      methods.forEach(indexed::remove);
    }
  }

  /**
   * Пересчитывает все функции документа.
   * <p>
   * Вызывается там, где дерево разбора заведомо на месте: сразу после разбора документа
   * либо под блокировкой, снятой с проверенного состояния. Само состояние здесь не
   * спрашивается — при разборе оно выставляется уже после публикации события.
   *
   * @param documentContext документ.
   * @return методы, у которых набор типов изменился.
   */
  private List<MethodSymbol> recomputeDocument(DocumentContext documentContext) {
    var changed = new ArrayList<MethodSymbol>();
    var methods = new ArrayList<MethodSymbol>();
    for (var method : documentContext.getSymbolTree().getMethods()) {
      // Заранее считаются только экспортные функции: остальные видны лишь внутри своего
      // документа и считаются по запросу (см. getOrCompute).
      if (!method.isFunction() || !method.isExport()) {
        continue;
      }
      methods.add(method);
      if (recompute(method)) {
        changed.add(method);
      }
    }
    if (!methods.isEmpty()) {
      methodsByUri.computeIfAbsent(documentContext.getUri(), k -> new CopyOnWriteArrayList<>())
        .addAll(methods);
    }
    return changed;
  }

  /**
   * Запоминает метод в списке методов его документа, чтобы запись ушла вместе с ним.
   *
   * @param method метод.
   */
  private void rememberMethodOfUri(MethodSymbol method) {
    methodsByUri.computeIfAbsent(method.getOwner().getUri(), k -> new CopyOnWriteArrayList<>())
      .add(method);
  }

  /**
   * Пересчитывает один метод и обновляет его связи.
   *
   * @param method метод.
   * @return {@code true}, если набор типов изменился.
   */
  private boolean recompute(MethodSymbol method) {
    return store(method, inferencer.computeReturnTypes(method));
  }

  /**
   * Запоминает результат расчёта и обновляет связи метода.
   *
   * @param method   метод.
   * @param computed результат расчёта.
   * @return {@code true}, если набор типов изменился.
   */
  private boolean store(MethodSymbol method, ComputedReturnTypes computed) {
    link(method, computed.consulted());
    if (!maintenance && computed.types().isEmpty()) {
      // Пустое значение функции, посчитанное при наполнении области, значит «неизвестно»
      // чаще, чем «ничего не возвращает»: вызов в модуль, до которого очередь ещё не
      // дошла, не разрешается ни во что, поэтому и о пропущенном сообщить некому — расчёт
      // сам себя неполным не считает. Такие пересчитываются проходом, когда область
      // наполнена и все имена разрешаются одинаково. Повторно сюда они не попадают: в
      // самом проходе пустое значение уже окончательно.
      pending.add(method);
    }
    if (computed.incomplete()) {
      // Расчёт видел метод, значение которого ещё не посчитано: так бывает, пока рабочая
      // область наполняется. Такой метод пересчитывается проходом после наполнения.
      pending.add(method);
      if (maintenance && LOGGER.isDebugEnabled()) {
        LOGGER.debug("Отложен повторно: {} из {}; не посчитаны: {}",
          method.getName(), method.getOwner().getUri(),
          computed.consulted().stream()
            .filter(dependency -> !indexed.contains(dependency))
            .map(dependency -> dependency.getName() + "@" + dependency.getOwner().getMdoRef())
            .toList());
      }
    }
    var previous = symbolTypeIndex.getReturnTypes(method);
    symbolTypeIndex.putReturnTypes(method, computed.types());
    indexed.add(method);
    return !symbolTypeIndex.getReturnTypes(method).equals(previous);
  }

  /**
   * Разносит изменение значений по потребителям до неподвижной точки.
   * <p>
   * Единица разноса — документ: связи держатся по документам, и пересчитать его методы
   * целиком дешевле, чем хранить, какой из них на чём построен.
   * <p>
   * Пересчитываются только те, чьи документы разобраны прямо сейчас: догрузка ради
   * отдельной правки в редакторе слишком дорога. Остальные подтянутся общим проходом
   * либо при следующем разборе своего документа.
   *
   * @param seeds методы, значения которых изменились.
   */
  private void propagate(Collection<MethodSymbol> seeds) {
    var changedUris = seeds.stream().map(method -> method.getOwner().getUri()).toList();
    var queue = new ArrayDeque<>(dependentsOf(changedUris));
    for (var pass = 0; pass < MAX_PASSES && !queue.isEmpty(); pass++) {
      var wave = List.copyOf(queue);
      queue.clear();
      var changed = new ArrayList<URI>();
      for (var uri : wave) {
        if (recomputeLoaded(uri)) {
          changed.add(uri);
        }
      }
      queue.addAll(dependentsOf(changed));
    }
  }

  /**
   * Пересчитывает методы документа, если его дерево разбора под рукой.
   * <p>
   * Выгруженный документ пропускается: догружать его ради разноса одной волны слишком
   * дорого. Значение подтянется при следующем разборе документа, а если расчёт шёл по
   * незаполненному значению — методом из очереди отложенных.
   *
   * @param uri URI документа.
   * @return {@code true}, если хоть у одного метода набор типов изменился.
   */
  private boolean recomputeLoaded(URI uri) {
    var methods = methodsByUri.get(uri);
    if (methods == null || methods.isEmpty() || !isReadable(methods.get(0))) {
      return false;
    }
    return recomputeEach(methods);
  }

  /**
   * Разносит накопленные общим проходом изменения, догружая документы потребителей.
   *
   * @param serverContext    рабочая область.
   * @param progressReporter индикатор хода работы.
   */
  private void drainPending(
    ServerContext serverContext,
    WorkDoneProgressHelper.WorkDoneProgressReporter progressReporter
  ) {
    var planned = 0;
    for (var pass = 0; pass < MAX_PASSES && !pending.isEmpty(); pass++) {
      var roots = List.copyOf(pending);
      pending.clear();
      // Проход вскрывает новые зависимости, поэтому общее число методов заранее не
      // известно: наращиваем его по мере того, как работа находится. Считается ровно то,
      // по чему идёт отсчёт, — методы, иначе числитель убегает за знаменатель.
      planned += roots.size();
      progressReporter.setSize(planned);
      resolveAll(serverContext, roots, progressReporter);
      LOGGER.debug("Волна {}: пересчитано методов {}, снова отложено {}",
        pass + 1, roots.size(), pending.size());
    }
  }

  /**
   * Пересчитывает отложенные методы в порядке их зависимостей.
   * <p>
   * Единица работы — документ: он загружается один раз, на нём пересчитываются все его
   * отложенные методы, и он отпускается. Порядок задаёт граф зависимостей между
   * документами, свёрнутый по компонентам сильной связности: к моменту разбора документа
   * всё, от чего он зависит, уже посчитано окончательно, поэтому второй заход не нужен.
   * Без этого порядка документ перечитывался волнами по восемь-десять раз.
   * <p>
   * Внутри компоненты сходимости за один заход нет — там цикл, — поэтому её документы
   * держатся загруженными и крутятся до неподвижной точки. Компонента крупнее
   * {@link #MAX_DOCUMENTS_IN_MEMORY} обрабатывается по-старому, с загрузкой и
   * освобождением на каждом обороте: память дороже лишних разборов.
   * <p>
   * Спуск по зависимостям вглубь по методам пробовался и оказался хуже: грузить всё равно
   * приходится документ целиком, и один документ загружался заново под каждый свой
   * метод — на ssl_3_1 это дало 4252 разбора против 215.
   * <p>
   * Компоненты одного яруса считаются разом: зависимостей между ними нет по построению,
   * документы у них разные, и каждая берёт замки только своих. Ярусов на реальной
   * конфигурации единицы, а компонент в первом — больше половины, поэтому ярусный обход
   * распараллеливается почти целиком. Пул нужен именно тот, чьи воркеры несут рабочую
   * область: без неё бины workspace-скоупа из чужого потока не достаются.
   *
   * @param serverContext    рабочая область.
   * @param roots            отложенные методы.
   * @param progressReporter индикатор хода работы.
   */
  private void resolveAll(
    ServerContext serverContext,
    List<MethodSymbol> roots,
    WorkDoneProgressHelper.WorkDoneProgressReporter progressReporter
  ) {
    var byDocument = new LinkedHashMap<URI, List<MethodSymbol>>();
    for (var root : roots) {
      byDocument.computeIfAbsent(root.getOwner().getUri(), k -> new ArrayList<>()).add(root);
    }
    var order = DocumentDependencies.of(byDocument.keySet(),
      uri -> dependenciesByUri.getOrDefault(uri, Set.of()));
    for (var tier : tiersOf(order.components(), byDocument.keySet())) {
      if (tier.size() == 1) {
        resolveComponent(serverContext, tier.get(0), byDocument, progressReporter);
        continue;
      }
      var tasks = tier.stream()
        .map(component -> CompletableFuture.runAsync(
          () -> resolveComponent(serverContext, component, byDocument, progressReporter),
          resolveReturnTypesExecutor))
        .toArray(CompletableFuture[]::new);
      CompletableFuture.allOf(tasks).join();
    }
  }

  /**
   * Разбивает компоненты на ярусы: компонента попадает на ярус следующий за наибольшим
   * ярусом тех, от кого она зависит.
   * <p>
   * Компоненты приходят в обратном топологическом порядке, поэтому ярус каждой известен к
   * моменту, когда до неё дошла очередь. Внутри яруса зависимостей нет, и считать его
   * компоненты можно в любом порядке и одновременно.
   *
   * @param components компоненты в обратном топологическом порядке.
   * @param inWork     документы, которые предстоит пересчитать: зависимости на прочие
   *                   порядка не задают — те уже посчитаны окончательно.
   * @return ярусы в порядке возрастания.
   */
  private List<List<List<URI>>> tiersOf(List<List<URI>> components, Set<URI> inWork) {
    var tierOfUri = new HashMap<URI, Integer>();
    var tiers = new ArrayList<List<List<URI>>>();
    for (var component : components) {
      var tier = component.stream()
        .flatMap(uri -> dependenciesByUri.getOrDefault(uri, Set.<URI>of()).stream())
        .filter(inWork::contains)
        .map(dependency -> tierOfUri.getOrDefault(dependency, 0))
        .max(Integer::compare)
        .orElse(0);
      component.forEach(uri -> tierOfUri.put(uri, tier + 1));
      while (tiers.size() <= tier) {
        tiers.add(new ArrayList<>());
      }
      tiers.get(tier).add(component);
    }
    return tiers;
  }

  /**
   * Доводит до неподвижной точки компоненту сильной связности.
   * <p>
   * Компонента из одного документа считается за один заход: всё, от чего она зависит, уже
   * посчитано. В цикле заходов несколько, поэтому его документы держатся загруженными —
   * так повторные обороты не стоят ни одного лишнего разбора.
   *
   * @param serverContext    рабочая область.
   * @param component        документы компоненты.
   * @param byDocument       отложенные методы по документам.
   * @param progressReporter индикатор хода работы.
   */
  private void resolveComponent(
    ServerContext serverContext,
    List<URI> component,
    Map<URI, List<MethodSymbol>> byDocument,
    WorkDoneProgressHelper.WorkDoneProgressReporter progressReporter
  ) {
    var methods = component.stream().flatMap(uri -> byDocument.get(uri).stream()).toList();
    methods.forEach(method -> progressReporter.tick());
    if (component.size() == 1) {
      recomputeLoading(serverContext, byDocument.get(component.get(0)));
      return;
    }
    if (component.size() > MAX_DOCUMENTS_IN_MEMORY) {
      // Держать столько документов разом нельзя, поэтому крутим с загрузкой и
      // освобождением: лишние разборы дешевле переполнения памяти.
      for (var pass = 0; pass < MAX_PASSES; pass++) {
        var changed = false;
        for (var uri : component) {
          changed |= recomputeLoading(serverContext, byDocument.get(uri));
        }
        if (!changed) {
          return;
        }
      }
      return;
    }
    withDocumentsLoaded(serverContext, component, () -> {
      for (var pass = 0; pass < MAX_PASSES; pass++) {
        var changed = false;
        for (var method : methods) {
          changed |= recompute(method);
        }
        if (!changed) {
          return;
        }
      }
    });
  }

  /**
   * Выполняет работу, держа документы компоненты разобранными, и возвращает их в прежнее
   * состояние после неё.
   *
   * @param serverContext рабочая область.
   * @param component     документы компоненты.
   * @param work          работа над ними.
   */
  private void withDocumentsLoaded(ServerContext serverContext, List<URI> component, Runnable work) {
    var locked = new ArrayList<URI>(component.size());
    var loaded = new ArrayList<DocumentContext>(component.size());
    // Замки берутся в порядке имён: здесь их держится сразу несколько, и без общего порядка
    // два потока на пересекающихся компонентах встали бы друг против друга.
    var ordered = component.stream().sorted(Comparator.comparing(URI::toString)).toList();
    try {
      for (var uri : ordered) {
        var documentContext = serverContext.getDocuments().get(uri);
        if (documentContext == null) {
          continue;
        }
        serverContext.getDocumentLock(uri).writeLock().lock();
        locked.add(uri);
        if (!isReadable(documentContext)) {
          rebuilt.incrementAndGet();
          rebuiltUris.add(uri);
          serverContext.rebuildDocument(documentContext);
          loaded.add(documentContext);
        }
      }
      work.run();
    } finally {
      loaded.forEach(serverContext::tryClearDocument);
      for (var index = locked.size() - 1; index >= 0; index--) {
        serverContext.getDocumentLock(locked.get(index)).writeLock().unlock();
      }
    }
  }

  /**
   * Пересчитывает методы документа, догрузив его, если вторичные данные освобождены, и
   * вернув в прежнее состояние после расчёта.
   * <p>
   * Разбор документа сам публикует событие изменения содержимого, поэтому пересчёт
   * выполняет обработчик этого события — здесь остаётся только довести документ до
   * состояния, в котором его тела читаются, и вернуть обратно.
   * <p>
   * Догрузка и освобождение идут под блокировкой документа на запись: без неё другой
   * поток, работающий с тем же документом, остался бы без вторичных данных посреди
   * своего расчёта. Одновременно загруженных документов не больше, чем потоков в пуле,
   * поэтому память не зависит от размера рабочей области.
   *
   * @param serverContext рабочая область.
   * @param methods       отложенные методы одного документа.
   * @return {@code true}, если хоть у одного метода набор типов изменился.
   */
  private boolean recomputeLoading(ServerContext serverContext, List<MethodSymbol> methods) {
    var documentContext = methods.get(0).getOwner();
    var lock = serverContext.getDocumentLock(documentContext.getUri());
    lock.readLock().lock();
    try {
      if (isReadable(documentContext)) {
        return recomputeEach(methods);
      }
    } finally {
      lock.readLock().unlock();
    }
    lock.writeLock().lock();
    try {
      // Пока ждали блокировку, документ мог догрузить другой поток: тогда разбирать его
      // заново не надо, а освобождать — тем более, он сейчас кому-то нужен.
      if (isReadable(documentContext)) {
        return recomputeEach(methods);
      }
      rebuilt.incrementAndGet();
      rebuiltUris.add(documentContext.getUri());
      // Разбор сам пересчитает экспортные методы документа по событию, а неэкспортные,
      // до которых дошла очередь, пересчитываются явно.
      serverContext.rebuildDocument(documentContext);
      var changed = recomputeEach(methods);
      serverContext.tryClearDocument(documentContext);
      return changed;
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Пересчитывает перечисленные методы одного, уже разобранного документа.
   *
   * @param methods методы.
   * @return {@code true}, если хоть у одного набор типов изменился.
   */
  private boolean recomputeEach(List<MethodSymbol> methods) {
    var changed = false;
    for (var method : methods) {
      // Пометка «посчитан» не снимается: пересчёт и так идёт напрямую, а без пометки
      // соседний метод, читающий этот прямо сейчас, счёл бы его непосчитанным и снова
      // ушёл бы в отложенные — очередь не сходилась бы.
      changed |= recompute(method);
    }
    return changed;
  }

  /**
   * Запоминает, на каких чужих документах построено значение метода.
   * <p>
   * Связи внутри документа не хранятся: его правка пересчитывает все его методы разом,
   * поэтому такая связь никогда не спрашивается.
   *
   * @param method    метод.
   * @param consulted методы, значения которых участвовали в расчёте.
   */
  private void link(MethodSymbol method, Set<MethodSymbol> consulted) {
    var uri = method.getOwner().getUri();
    for (var dependency : consulted) {
      var dependencyUri = dependency.getOwner().getUri();
      if (dependencyUri.equals(uri)) {
        continue;
      }
      dependenciesByUri.computeIfAbsent(uri, k -> ConcurrentHashMap.newKeySet()).add(dependencyUri);
      dependentsByUri.computeIfAbsent(dependencyUri, k -> ConcurrentHashMap.newKeySet()).add(uri);
    }
  }

  /**
   * Потребители перечисленных документов.
   *
   * @param uris URI документов.
   * @return URI документов, значения которых на них построены.
   */
  private Set<URI> dependentsOf(Collection<URI> uris) {
    var result = new LinkedHashSet<URI>();
    for (var uri : uris) {
      var dependents = dependentsByUri.get(uri);
      if (dependents != null) {
        result.addAll(dependents);
      }
    }
    return result;
  }

  /** Снимает связи документа с теми, на ком он был построен. */
  private void unlinkDependencies(URI uri) {
    var dependencies = dependenciesByUri.remove(uri);
    if (dependencies == null) {
      return;
    }
    for (var dependency : dependencies) {
      var dependents = dependentsByUri.get(dependency);
      if (dependents != null) {
        dependents.remove(uri);
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
    return documentContext.getServerContext().getDocumentState(documentContext)
      == DocumentState.WITH_CONTENT;
  }

  /**
   * Сообщение для индикатора хода работы на языке сервера.
   *
   * @param key ключ сообщения.
   * @return текст сообщения.
   */
  private String getMessage(String key) {
    return Resources.getResourceString(globalConfiguration.getLanguage(), getClass(), key);
  }

  /**
   * Результат расчёта типов возврата метода.
   *
   * @param types      рассчитанные типы.
   * @param consulted  методы, значения которых участвовали в расчёте.
   * @param incomplete расчёт обращался к методу, значение которого ещё не посчитано, —
   *                   значит его надо повторить, когда рабочая область будет наполнена.
   */
  public record ComputedReturnTypes(TypeSet types, Set<MethodSymbol> consulted, boolean incomplete) {
  }
}
