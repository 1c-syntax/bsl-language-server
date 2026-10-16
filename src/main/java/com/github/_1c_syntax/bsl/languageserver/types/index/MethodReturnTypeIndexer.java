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
import org.jspecify.annotations.Nullable;
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
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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

  /**
   * Сколько документов допустимо держать разобранными разом: и ради обхода цикла, и ради
   * параллельного счёта компонент.
   */
  private static final int MAX_DOCUMENTS_IN_MEMORY = 8;

  private final ExpressionTypeInferencer inferencer;
  private final SymbolTypeIndex symbolTypeIndex;

  private final WorkDoneProgressHelper workDoneProgressHelper;
  private final GlobalLanguageServerConfiguration globalConfiguration;

  /**
   * Пул, на котором считаются компоненты одного яруса. Свой, а не пул наполнения: проход
   * запускается слушателем события наполнения, то есть изнутри задачи того пула, и класть
   * туда собственные задачи означало бы занимать пул, который сейчас занят. Воркеры
   * выставляют себе URI рабочей области — без этого workspace-скоуп из чужого потока не
   * резолвится.
   */
  @Qualifier("resolveReturnTypesExecutor")
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

  /**
   * Методы, тела которых уже разбирались: пустой ответ у них значит «ничего не возвращает».
   * Символ метода — из последнего дерева его документа: перечитанный с тем же текстом документ
   * подставляет сюда символы своего нового дерева ({@link #rememberCurrentSymbols}).
   */
  private final Map<MethodKey, MethodSymbol> indexed = new ConcurrentHashMap<>();

  /** Методы, значение которых посчитано по приближениям и ждёт пересчёта (см. computeIfAbsent). */
  private final Set<MethodKey> provisional = ConcurrentHashMap.newKeySet();

  /**
   * Часы, по которым видно, что чей расчёт застал, а что появилось уже после него. Своё
   * время, а не системное: сравниваются только порядковые отметки, и брать их надо в том
   * же порядке, в каком идут сами события.
   */
  private final AtomicLong clock = new AtomicLong();

  /** Отметка часов на момент, когда начинался последний расчёт метода. */
  private final Map<MethodKey, Long> computedAt = new ConcurrentHashMap<>();

  /** Отметка часов на момент, когда в документе в последний раз менялось чьё-то значение. */
  private final Map<URI, Long> changedAt = new ConcurrentHashMap<>();

  /** Сколько раз менялось чьё-либо значение: волна, не изменившая ничего, повторилась бы дословно. */
  private final AtomicLong valueChanges = new AtomicLong();

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
   * <p>
   * Состояние документа здесь не спрашивается: расчёт идёт в контексте вызывающего, а тот
   * читает дерево этого же документа прямо сейчас. Состояние бы соврало — при первом разборе
   * оно выставляется уже после события о разборе, по которому и считаются значения, и
   * вызванная функция своего модуля осталась бы непосчитанной, а вызывающий получил бы одно
   * её описание.
   *
   * <p>
   * Значение, посчитанное по приближениям, запоминается как предварительное: им отвечают,
   * пока идёт расчёт, давший приближения, — иначе каждый вызов в нём пересчитывал бы тело
   * заново, и цепочка вызовов внутри модуля разрасталась бы лавинообразно. Первый же
   * запрос не по приближениям считает значение заново, и оно становится окончательным.
   *
   * @param method      метод.
   * @param computation расчёт в контексте вызывающего: у него общая с ним защита от
   *                    циклов и общая глубина, без которых цепочка вызовов внутри модуля
   *                    уходит в рекурсию до переполнения стека.
   * @param approximate считается ли значение по приближениям, которые после текущего
   *                    расчёта устареют.
   */
  public void computeIfAbsent(MethodSymbol method, Supplier<ComputedReturnTypes> computation, boolean approximate) {
    if (!method.isFunction()) {
      return;
    }
    var key = MethodKey.of(method);
    var known = indexed.containsKey(key);
    if (known && (approximate || !provisional.contains(key))) {
      return;
    }
    var startedAt = clock.incrementAndGet();
    store(method, computation.get(), startedAt);
    if (approximate) {
      provisional.add(key);
    } else {
      provisional.remove(key);
    }
    if (!known) {
      rememberMethodOfUri(method);
    }
  }

  /**
   * Считает типы возврата метода по телу, если этого ещё не делалось, и запоминает их как
   * окончательные — см. {@link #computeIfAbsent(MethodSymbol, Supplier, boolean)}.
   *
   * @param method      метод.
   * @param computation расчёт в контексте вызывающего.
   */
  public void computeIfAbsent(MethodSymbol method, Supplier<ComputedReturnTypes> computation) {
    computeIfAbsent(method, computation, false);
  }

  /**
   * Посчитано ли значение метода окончательно.
   * <p>
   * Пока рабочая область наполняется, до части модулей очередь ещё не дошла, и пустой
   * ответ у них означает «неизвестно», а не «ничего не возвращает». Предварительное
   * значение, посчитанное по приближениям, тоже не окончательное: расчёт, опирающийся на
   * него, придётся повторить.
   *
   * @param method метод.
   * @return {@code true}, если типы возврата метода уже выводились и не по приближениям.
   */
  public boolean isIndexed(MethodSymbol method) {
    var key = MethodKey.of(method);
    return indexed.containsKey(key) && !provisional.contains(key);
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
      // расчёт с чистого листа рвёт цепочку взаимных вызовов в другом месте. Меняются только
      // символы: прежние держали бы прежнее дерево документа целиком.
      rememberCurrentSymbols(event.getSource());
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
      documentsOf(pending.stream().map(MethodSymbol::getOwner)),
      getMessage("resolveDocumentsPostfix")
    );
    progressReporter.beginProgress(getMessage("resolveReturnTypes"));
    try {
      drainPending(serverContext, new ResolveProgress(progressReporter));
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
    changedAt.remove(uri);
    var methods = methodsByUri.remove(uri);
    if (methods != null) {
      methods.forEach(method -> {
        var key = MethodKey.of(method);
        indexed.remove(key);
        provisional.remove(key);
        computedAt.remove(key);
      });
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
      rememberMethodsOfUri(documentContext.getUri(), methods);
    }
    return changed;
  }

  /**
   * Запоминает метод в списке методов его документа, чтобы запись ушла вместе с ним.
   *
   * @param method метод.
   */
  private void rememberMethodOfUri(MethodSymbol method) {
    rememberMethodsOfUri(method.getOwner().getUri(), List.of(method));
  }

  /**
   * Запоминает методы в списке методов их документа, чтобы записи ушли вместе с ним.
   * <p>
   * Список берётся и пополняется одним неделимым действием: возьми его отдельно, и
   * очистка документа успеет выкинуть список из карты между взятием и пополнением. Метод
   * остался бы помеченным как посчитанный, но недостижимым из карты — и следующая
   * очистка пометку бы уже не сняла.
   *
   * @param uri     URI документа.
   * @param methods методы документа.
   */
  private void rememberMethodsOfUri(URI uri, List<MethodSymbol> methods) {
    methodsByUri.compute(uri, (key, remembered) -> {
      var all = remembered == null ? new CopyOnWriteArrayList<MethodSymbol>() : remembered;
      all.addAll(methods);
      return all;
    });
  }

  /**
   * Подставляет в запомненное символы нового дерева документа, перечитанного с тем же текстом.
   * <p>
   * Записи о методах остаются в силе, а символы меняются на равные им из нового дерева: прежний
   * символ держал бы прежнее дерево целиком. Каждая подстановка неделима, поэтому окна, в
   * котором метод не найден, нет. Метод, которого в новом дереве нет, остаётся как был.
   *
   * @param documentContext документ.
   */
  private void rememberCurrentSymbols(DocumentContext documentContext) {
    var uri = documentContext.getUri();
    if (!methodsByUri.containsKey(uri)) {
      return;
    }
    var current = new HashMap<MethodKey, MethodSymbol>();
    for (var method : documentContext.getSymbolTree().getMethods()) {
      current.putIfAbsent(MethodKey.of(method), method);
    }
    methodsByUri.computeIfPresent(uri, (key, remembered) -> {
      var rebound = new CopyOnWriteArrayList<MethodSymbol>();
      for (var method : remembered) {
        rebound.add(current.getOrDefault(MethodKey.of(method), method));
      }
      return rebound;
    });
    current.forEach((key, method) -> indexed.computeIfPresent(key, (k, remembered) -> method));
  }

  /**
   * Пересчитывает один метод и обновляет его связи.
   *
   * @param method метод.
   * @return {@code true}, если набор типов изменился.
   */
  private boolean recompute(MethodSymbol method) {
    // Отметка снимается до расчёта: значение, изменившееся уже во время него, расчёт
    // застать не мог, и вызывающему понадобится ещё один заход.
    var startedAt = clock.incrementAndGet();
    var changed = store(method, inferencer.computeReturnTypes(method), startedAt);
    // Расчёт со свежего контекста идёт не посреди кругов: значение окончательное.
    provisional.remove(MethodKey.of(method));
    return changed;
  }

  /**
   * Запоминает результат расчёта и обновляет связи метода.
   *
   * @param method    метод.
   * @param computed  результат расчёта.
   * @param startedAt отметка часов на момент начала расчёта.
   * @return {@code true}, если набор типов изменился.
   */
  private boolean store(MethodSymbol method, ComputedReturnTypes computed, long startedAt) {
    link(method, computed.consulted());
    if (!maintenance && computed.consulted().stream().anyMatch(pending::contains)) {
      // Расчёт опирался на значение, которое само ждёт пересчёта: вызванный метод посчитан,
      // но предварительно. Когда проход доведёт его до полного, вызывающему понадобится
      // пересчёт — иначе он навсегда останется с предварительным, а каким оно вышло, решает
      // порядок наполнения. Шаг один, без обхода потребителей по цепочке: цепочку проход
      // разматывает сам, волна за волной.
      pending.add(method);
    }
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
            .filter(dependency -> !indexed.containsKey(MethodKey.of(dependency)))
            .map(dependency -> dependency.getName() + "@" + dependency.getOwner().getMdoRef())
            .toList());
      }
    }
    var previous = symbolTypeIndex.getReturnTypes(method);
    symbolTypeIndex.putReturnTypes(method, computed.types());
    var key = MethodKey.of(method);
    indexed.putIfAbsent(key, method);
    var valueChanged = !symbolTypeIndex.getReturnTypes(method).equals(previous);
    computedAt.put(key, startedAt);
    if (valueChanged) {
      // Обе отметки берутся из одной точки — до расчёта. Строго полнее было бы отмечать
      // изменение уже после того, как значение выложено: тогда ловился бы и расчёт,
      // начавшийся раньше выкладки, но успевший прочитать прежнее значение. Пока так
      // делать нельзя — лишние пересчёты вскрывают немонотонность самого пересчёта, и
      // воспроизводимость падает вместо того, чтобы вырасти (замеры в #4480).
      changedAt.merge(method.getOwner().getUri(), startedAt, Math::max);
      valueChanges.incrementAndGet();
    }
    return valueChanged;
  }

  /**
   * Методы, чей расчёт устарел: документ, на значениях которого он построен, изменился уже
   * после того, как расчёт прошёл.
   * <p>
   * Отложить такой метод в момент расчёта было некому: значение, на котором он построился,
   * выглядело посчитанным, а обогатилось позже. Учёт по документам, а не по методам: по
   * документам держатся и сами связи, а лишний пересчёт стоит дешевле, чем хранение связей
   * по методам — их на реальной конфигурации на порядок больше.
   *
   * @return методы, которые надо посчитать заново.
   */
  private Set<MethodSymbol> outdated() {
    var outdated = new LinkedHashSet<MethodSymbol>();
    for (var entry : indexed.entrySet()) {
      var method = entry.getValue();
      var dependencies = dependenciesByUri.get(method.getOwner().getUri());
      if (dependencies == null) {
        continue;
      }
      var computed = computedAt.getOrDefault(entry.getKey(), 0L);
      for (var dependency : dependencies) {
        if (changedAt.getOrDefault(dependency, 0L) > computed) {
          outdated.add(method);
          break;
        }
      }
    }
    return outdated;
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
   * Сколько различных документов среди документов методов.
   *
   * @param owners документы методов.
   * @return число документов.
   */
  private static int documentsOf(Stream<DocumentContext> owners) {
    return (int) owners.map(DocumentContext::getUri).distinct().count();
  }

  /**
   * Разносит накопленные общим проходом изменения, догружая документы потребителей.
   *
   * @param serverContext рабочая область.
   * @param progress      ход работы.
   */
  private void drainPending(ServerContext serverContext, ResolveProgress progress) {
    for (var pass = 0; pass < MAX_PASSES && !pending.isEmpty(); pass++) {
      // Волна держит ключи, а не символы: документ, разобранный ею заново, получает новое
      // дерево, и символ из прежнего держал бы прежнее дерево целиком до конца волны.
      var roots = pending.stream().map(MethodKey::of).toList();
      pending.clear();
      progress.plan(documentsOf(roots.stream().map(MethodKey::owner)));
      var changesBefore = valueChanges.get();
      resolveAll(serverContext, roots, progress);
      // План волны строится по связям, известным на её начало, а вскрываются они её же
      // расчётом: метод, чья зависимость обогатилась после него, отложить было некому.
      var outdated = outdated();
      if (valueChanges.get() == changesBefore) {
        // Волна не изменила ни одного значения: её методы следующая посчитала бы с тем же
        // итогом, и снова отложенные среди них остаются с тем, что посчитано. Устаревшим при
        // этом может оказаться метод вне волны: зависимость поменялась после его расчёта ещё
        // при наполнении, а разнос его не пересчитал — документ был отпущен, — и до первой
        // волны устаревших никто не искал.
        var recomputed = Set.copyOf(roots);
        outdated.removeIf(method -> recomputed.contains(MethodKey.of(method)));
        pending.clear();
        if (outdated.isEmpty()) {
          LOGGER.debug("Волна {}: пересчитано методов {}, ни одно значение не изменилось — проход окончен",
            pass + 1, roots.size());
          break;
        }
      }
      pending.addAll(outdated);
      LOGGER.debug("Волна {}: пересчитано методов {}, снова отложено {}, из них устаревших {}",
        pass + 1, roots.size(), pending.size(), outdated.size());
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
   * {@link #MAX_DOCUMENTS_IN_MEMORY} обрабатывается с загрузкой и освобождением
   * документов на каждом обороте: память дороже лишних разборов. Со второго оборота
   * разбираются только документы, итог которых может измениться.
   * <p>
   * Спуск по зависимостям вглубь по методам пробовался и оказался хуже: грузить всё равно
   * приходится документ целиком, и один документ загружался заново под каждый свой
   * метод — на ssl_3_1 это дало 4252 разбора против 215.
   * <p>
   * Компонента начинает считаться, как только посчитаны все, от кого она зависит, и в
   * пределе разом разобранных документов нашлось место на все её документы: цикл занимает
   * столько мест, сколько в нём документов, цикл крупнее предела — весь предел. Ярусами,
   * как прежде, выходило медленнее: ярус ждал самую долгую компоненту прошлого, даже не
   * завися от неё, а циклы яруса считались по одному. Пул нужен именно тот, чьи воркеры
   * несут рабочую область: без неё бины workspace-скоупа из чужого потока не достаются.
   * <p>
   * Независимость одновременно считаемых компонент опирается на связи, собранные прошлым
   * расчётом, а пересчёт способен дойти до документа, которого в них не было: до
   * наполнения области вызов туда не разрешался вовсе. Такой документ может считаться в
   * это же время другой компонентой, и что прочтёт расчёт, решит момент. Из-под порядка
   * это не выбивается: он и в последовательном проходе задан набором отложенного, то есть
   * тоже не воспроизводится дословно; замер мерцания на ssl_3_1 разницы между проходами не
   * показал.
   *
   * @param serverContext    рабочая область.
   * @param roots            отложенные методы.
   * @param progress         ход работы.
   */
  private void resolveAll(
    ServerContext serverContext,
    List<MethodKey> roots,
    ResolveProgress progress
  ) {
    var byDocument = new LinkedHashMap<URI, List<MethodKey>>();
    for (var root : roots) {
      byDocument.computeIfAbsent(root.owner().getUri(), k -> new ArrayList<>()).add(root);
    }
    var order = DocumentDependencies.of(byDocument.keySet(),
      uri -> dependenciesByUri.getOrDefault(uri, Set.of()));
    try {
      new ComponentDispatch(serverContext, order.components(), byDocument, progress).start().join();
    } catch (CompletionException e) {
      // Наружу — исключение самого расчёта, а не обёртка пула: так же, как при счёте в этом потоке.
      Throwable cause = e;
      while (cause instanceof CompletionException && cause.getCause() != null) {
        cause = cause.getCause();
      }
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw e;
    }
  }

  /**
   * Доводит до неподвижной точки компоненту сильной связности, документы которой можно
   * держать разобранными разом.
   * <p>
   * Компонента из одного документа считается за один заход: всё, от чего она зависит, уже
   * посчитано. В цикле заходов несколько, поэтому его документы держатся загруженными —
   * так повторные обороты не стоят ни одного лишнего разбора.
   *
   * @param serverContext    рабочая область.
   * @param component        документы компоненты.
   * @param byDocument       отложенные методы по документам.
   * @param progress         ход работы.
   */
  private void resolveComponent(
    ServerContext serverContext,
    List<URI> component,
    Map<URI, List<MethodKey>> byDocument,
    ResolveProgress progress
  ) {
    var methods = component.stream().flatMap(uri -> byDocument.get(uri).stream()).toList();
    try {
      resolveComponent(serverContext, component, byDocument, methods);
    } finally {
      // Единица счёта — документ: он и есть единица работы прохода, а методов в нём
      // сколько угодно. Отсчёт после работы: компонента считается целиком, и до её конца
      // сказать про её документы нечего, а компоненты вдобавок считаются сразу многими потоками.
      progress.done(component.size());
    }
  }

  /**
   * Доводит компоненту до неподвижной точки.
   *
   * @param serverContext рабочая область.
   * @param component     документы компоненты.
   * @param byDocument    отложенные методы по документам.
   * @param methods       отложенные методы компоненты.
   */
  private void resolveComponent(
    ServerContext serverContext,
    List<URI> component,
    Map<URI, List<MethodKey>> byDocument,
    List<MethodKey> methods
  ) {
    if (component.size() == 1) {
      recomputeLoading(serverContext, byDocument.get(component.get(0)));
      return;
    }
    withDocumentsLoaded(serverContext, component, () -> {
      var current = currentSymbols(methods);
      for (var pass = 0; pass < MAX_PASSES; pass++) {
        var changed = false;
        for (var method : current) {
          changed |= recompute(method);
        }
        if (!changed) {
          return;
        }
      }
    });
  }

  /**
   * Доводит до неподвижной точки компоненту, документы которой разом не удержать.
   * <p>
   * Держать столько документов разом нельзя, поэтому крутим с загрузкой и освобождением:
   * лишние разборы дешевле переполнения памяти. Но не каждый документ на каждом обороте:
   * со второго оборота разбирается только тот, чей расчёт может дать другой итог, —
   * значения которого на прошлом обороте изменились, либо построенный на документе,
   * изменившемся уже после его расчёта. Первое нужно из-за циклов внутри документа: их
   * разрывает прежнее значение, и изменившийся документ на новом заходе может посчитаться
   * иначе. У прочих на входе ровно то же, что и в прошлый раз, и пересчёт ничего бы не дал.
   * <p>
   * Итог оборота тот же, что у обхода документов по порядку компоненты: документ читает
   * значения тех, кто стоит раньше него, уже пересчитанными на этом обороте, а тех, кто
   * позже, — с прошлого. Поэтому документ ждёт только тех, кто раньше него и связан с ним в
   * любую сторону: на ком он построен — их новые значения ему и нужны, а кто построен на
   * нём — тем нужно его прежнее значение. Несвязанные друг с другом считаются одновременно.
   * Решение, пересчитывать ли документ, принимается, когда закончены все, кого он ждёт:
   * устареть он может только от связанных с ним.
   * <p>
   * Тех, кого оборот пересчитает наверняка, — изменившихся на прошлом и устаревших уже к его
   * началу, — свободные места в пределе разбирают впереди расчёта. Ход по такому циклу
   * отсчитывается по документам, а не разом после последнего оборота: документы первого
   * оборота уже в плане волны, а пересчитанные на следующих добавляются в план по мере
   * пересчёта.
   * <p>
   * Обороты сцеплены без ожидания в потоках пула: оборот, начатый из задачи пула и ждущий
   * там своих же задач, на занятом пуле не дождался бы их никогда.
   *
   * @param serverContext   рабочая область.
   * @param component       документы компоненты.
   * @param byDocument      отложенные методы по документам.
   * @param changedLastPass документы, значения которых изменились на прошлом обороте; у
   *                        первого — все документы компоненты.
   * @param progress        ход работы.
   * @param pass            номер оборота, с нуля.
   * @return окончание последнего оборота.
   */
  private CompletableFuture<Void> resolveUnloaded(
    ServerContext serverContext,
    List<URI> component,
    Map<URI, List<MethodKey>> byDocument,
    Set<URI> changedLastPass,
    ResolveProgress progress,
    int pass
  ) {
    if (pass >= MAX_PASSES || changedLastPass.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }
    var dispatch = new PassDispatch(serverContext, component, byDocument, changedLastPass, progress, pass == 0);
    return dispatch.start()
      .handle((ignored, error) -> dispatch.releaseUnused().thenCompose(released -> error == null
        ? resolveUnloaded(serverContext, component, byDocument, dispatch.changed, progress, pass + 1)
        : CompletableFuture.<Void>failedFuture(error)))
      .thenCompose(Function.identity());
  }

  /**
   * Ход доразрешения в документах: сколько разборов запланировано и сколько сделано.
   * <p>
   * Общий объём работы заранее не известен: волна вскрывает новые зависимости, а оборот
   * большого цикла пересчитывает документы заново. План поэтому наращивается по мере того,
   * как работа находится, и считается ровно то, по чему идёт отсчёт, — разборы документов,
   * иначе числитель убегает за знаменатель.
   */
  private static final class ResolveProgress {

    private final WorkDoneProgressHelper.WorkDoneProgressReporter reporter;
    private final AtomicInteger planned = new AtomicInteger();

    private ResolveProgress(WorkDoneProgressHelper.WorkDoneProgressReporter reporter) {
      this.reporter = reporter;
    }

    /**
     * Добавить разборы в план.
     *
     * @param documents число разборов.
     */
    void plan(int documents) {
      reporter.setSize(planned.addAndGet(documents));
    }

    /**
     * Отсчитать сделанные разборы.
     *
     * @param documents число разборов.
     */
    void done(int documents) {
      for (var i = 0; i < documents; i++) {
        reporter.tick();
      }
    }
  }

  /**
   * Запуск работ, зависящих друг от друга, в пределе {@link #MAX_DOCUMENTS_IN_MEMORY} разом
   * разобранных документов.
   * <p>
   * Работа готова, когда закончены все, от кого она зависит. Готовые запускаются по порядку
   * номеров, пока на очередную хватает мест; следующая не обгоняет ту, на которую мест пока
   * нет, — иначе крупная ждала бы, пока не кончатся мелкие. Раздача идёт под замком самого
   * запуска, работы отдают своё окончание и в потоках пула ничего не ждут. Упавшая работа
   * останавливает раздачу, а её исключение достаётся ждущему, когда закончатся начатые.
   */
  private abstract class Dispatch {

    private static final int READY = 1;
    private static final int RUNNING = 2;
    private static final int DONE = 3;

    /** Состояние работы: 0 — ждёт зависимостей, дальше {@link #READY}, {@link #RUNNING}, {@link #DONE}. */
    private final int[] state;
    private final int[] waitingFor;
    private final int[] reserved;
    private final List<List<Integer>> dependents;
    private final PriorityQueue<Integer> ready = new PriorityQueue<>();
    private final CompletableFuture<Void> completion = new CompletableFuture<>();
    private int free = MAX_DOCUMENTS_IN_MEMORY;
    private int running;
    private int finished;
    private boolean dispatching;
    private boolean completed;
    private @Nullable Throwable failure;

    Dispatch(int size) {
      state = new int[size];
      waitingFor = new int[size];
      reserved = new int[size];
      dependents = new ArrayList<>(size);
      for (var item = 0; item < size; item++) {
        dependents.add(new ArrayList<>());
      }
    }

    /**
     * Работа {@code item} начнётся не раньше, чем закончится {@code dependency}.
     *
     * @param item       работа.
     * @param dependency работа, которую она ждёт.
     */
    final void dependsOn(int item, int dependency) {
      waitingFor[item]++;
      dependents.get(dependency).add(item);
    }

    /**
     * Нужна ли готовая работа; ненужная заканчивается, не начавшись. Спрашивается под замком.
     *
     * @param item работа.
     * @return {@code true}, если её надо выполнить.
     */
    abstract boolean isNeeded(int item);

    /**
     * Сколько мест в пределе работа занимает, пока идёт.
     *
     * @param item работа.
     * @return число мест, не больше предела.
     */
    abstract int places(int item);

    /**
     * Начинает работу; вызывается под замком и ждать в нём не должна.
     *
     * @param item работа.
     * @return окончание работы.
     */
    abstract CompletableFuture<Void> perform(int item);

    /** Раздача кончилась, а места ещё есть: их можно занять впрок, см. {@link #reserve}. */
    void spare() {
      // по умолчанию впрок ничего не занимается
    }

    /**
     * Занимает место впрок за работой, которая ещё не начата: работа, начавшись, займёт на
     * него меньше, а закончившись, вернёт и его. Вызывается под замком.
     *
     * @param item работа.
     * @return {@code true}, если место нашлось.
     */
    final boolean reserve(int item) {
      if (free == 0 || state[item] >= RUNNING) {
        return false;
      }
      free--;
      reserved[item]++;
      return true;
    }

    /**
     * Не начата ли ещё работа. Вызывается под замком.
     *
     * @param item работа.
     * @return {@code true}, если работа ждёт зависимостей или места.
     */
    final boolean isPending(int item) {
      return state[item] < RUNNING;
    }

    /**
     * Начинает раздачу.
     *
     * @return окончание всех работ.
     */
    final CompletableFuture<Void> start() {
      boolean over;
      synchronized (this) {
        // Сначала собрать все, кто ничего не ждёт: ненужная работа заканчивается сразу и
        // освобождает своих зависимых, и обход по номерам застал бы их второй раз.
        var initial = new ArrayList<Integer>();
        for (var item = 0; item < state.length; item++) {
          if (waitingFor[item] == 0) {
            initial.add(item);
          }
        }
        initial.forEach(this::becomeReady);
        over = dispatch();
      }
      if (over) {
        complete();
      }
      return completion;
    }

    private void becomeReady(int item) {
      // Цепочка ненужных работ заканчивается здесь же, без рекурсии на каждую.
      var next = new ArrayDeque<Integer>();
      next.add(item);
      while (!next.isEmpty()) {
        var current = next.poll();
        if (state[current] != 0) {
          continue;
        }
        if (isNeeded(current)) {
          state[current] = READY;
          ready.add(current);
          continue;
        }
        state[current] = DONE;
        finished++;
        for (var dependent : dependents.get(current)) {
          if (--waitingFor[dependent] == 0) {
            next.add(dependent);
          }
        }
      }
    }

    /**
     * Раздаёт готовые работы; вызывается под замком.
     *
     * @return {@code true}, если раздавать больше нечего и ничего не идёт — пора заканчивать.
     */
    private boolean dispatch() {
      if (dispatching) {
        // Работа закончилась прямо в момент запуска, в этом же потоке: раздачу продолжит
        // внешний цикл — он и так перепроверяет очередь на каждом шаге.
        return false;
      }
      dispatching = true;
      try {
        while (failure == null && !ready.isEmpty()) {
          int item = ready.peek();
          var need = places(item) - reserved[item];
          if (need > free) {
            break;
          }
          ready.poll();
          free -= need;
          launch(item);
        }
        if (failure == null) {
          spare();
        }
      } finally {
        dispatching = false;
      }
      var over = !completed && running == 0 && (failure != null || ready.isEmpty());
      completed |= over;
      return over;
    }

    private void launch(int item) {
      state[item] = RUNNING;
      running++;
      CompletableFuture<Void> work;
      try {
        work = perform(item);
      } catch (RejectedExecutionException e) {
        running--;
        free += places(item);
        failure = e;
        return;
      }
      work.whenComplete((ignored, error) -> finish(item, error));
    }

    private void finish(int item, @Nullable Throwable error) {
      boolean over;
      synchronized (this) {
        running--;
        free += places(item);
        state[item] = DONE;
        finished++;
        if (error != null) {
          if (failure == null) {
            failure = error;
          }
        } else {
          for (var dependent : dependents.get(item)) {
            if (--waitingFor[dependent] == 0) {
              becomeReady(dependent);
            }
          }
        }
        over = dispatch();
      }
      if (over) {
        complete();
      }
    }

    private void complete() {
      Throwable error;
      int done;
      synchronized (this) {
        error = failure;
        done = finished;
      }
      if (error != null) {
        completion.completeExceptionally(error);
      } else if (done < state.length) {
        completion.completeExceptionally(new IllegalStateException(
          "Resolve dispatch stalled: finished " + done + " of " + state.length));
      } else {
        completion.complete(null);
      }
    }
  }

  /**
   * Компоненты волны: компонента ждёт компоненты, на документах которых построены её
   * документы, и занимает в пределе по месту на документ.
   */
  private final class ComponentDispatch extends Dispatch {

    private final ServerContext serverContext;
    private final List<List<URI>> components;
    private final Map<URI, List<MethodKey>> byDocument;
    private final ResolveProgress progress;

    private ComponentDispatch(
      ServerContext serverContext,
      List<List<URI>> components,
      Map<URI, List<MethodKey>> byDocument,
      ResolveProgress progress
    ) {
      super(components.size());
      this.serverContext = serverContext;
      this.components = components;
      this.byDocument = byDocument;
      this.progress = progress;
      var componentOf = new HashMap<URI, Integer>();
      for (var index = 0; index < components.size(); index++) {
        for (var uri : components.get(index)) {
          componentOf.put(uri, index);
        }
      }
      for (var index = 0; index < components.size(); index++) {
        var dependencies = new HashSet<Integer>();
        for (var uri : components.get(index)) {
          for (var dependency : dependenciesByUri.getOrDefault(uri, Set.of())) {
            var other = componentOf.get(dependency);
            if (other != null && other != index && dependencies.add(other)) {
              dependsOn(index, other);
            }
          }
        }
      }
    }

    @Override
    boolean isNeeded(int item) {
      return true;
    }

    @Override
    int places(int item) {
      return Math.min(components.get(item).size(), MAX_DOCUMENTS_IN_MEMORY);
    }

    @Override
    CompletableFuture<Void> perform(int item) {
      var component = components.get(item);
      if (component.size() > MAX_DOCUMENTS_IN_MEMORY) {
        return resolveUnloaded(serverContext, component, byDocument, new HashSet<>(component), progress, 0);
      }
      return CompletableFuture.runAsync(
        () -> resolveComponent(serverContext, component, byDocument, progress),
        resolveReturnTypesExecutor);
    }
  }

  /**
   * Один оборот по компоненте, документы которой разом не удержать (см. {@code resolveUnloaded}).
   * Работа — документ компоненты, номер — его место в порядке компоненты.
   */
  private final class PassDispatch extends Dispatch {

    private final ServerContext serverContext;
    private final List<URI> component;
    private final Map<URI, List<MethodKey>> byDocument;
    private final Set<URI> changedLastPass;
    private final ResolveProgress progress;
    private final boolean planned;
    private final List<Integer> certain = new ArrayList<>();
    private final Map<Integer, CompletableFuture<Boolean>> preloads = new HashMap<>();
    private final Set<URI> changed = ConcurrentHashMap.newKeySet();
    private int nextCertain;

    private PassDispatch(
      ServerContext serverContext,
      List<URI> component,
      Map<URI, List<MethodKey>> byDocument,
      Set<URI> changedLastPass,
      ResolveProgress progress,
      boolean planned
    ) {
      super(component.size());
      this.serverContext = serverContext;
      this.component = component;
      this.byDocument = byDocument;
      this.changedLastPass = changedLastPass;
      this.progress = progress;
      this.planned = planned;
      var indexOf = new HashMap<URI, Integer>();
      for (var index = 0; index < component.size(); index++) {
        indexOf.put(component.get(index), index);
      }
      for (var index = 0; index < component.size(); index++) {
        var uri = component.get(index);
        var earlier = new HashSet<Integer>();
        for (var related : List.of(
          dependenciesByUri.getOrDefault(uri, Set.of()),
          dependentsByUri.getOrDefault(uri, Set.of()))) {
          for (var other : related) {
            var otherIndex = indexOf.get(other);
            if (otherIndex != null && otherIndex < index && earlier.add(otherIndex)) {
              dependsOn(index, otherIndex);
            }
          }
        }
        if (changedLastPass.contains(uri) || isOutdated(uri, byDocument.get(uri))) {
          certain.add(index);
        }
      }
    }

    @Override
    boolean isNeeded(int item) {
      var uri = component.get(item);
      var needed = changedLastPass.contains(uri) || isOutdated(uri, byDocument.get(uri));
      if (needed && !planned) {
        progress.plan(1);
      }
      return needed;
    }

    @Override
    int places(int item) {
      return 1;
    }

    @Override
    CompletableFuture<Void> perform(int item) {
      var uri = component.get(item);
      var methods = byDocument.get(uri);
      var preload = preloads.remove(item);
      // Разбор впереди уже идёт: расчёт ждёт его, иначе документ разобрали бы дважды.
      var loaded = preload != null ? preload : CompletableFuture.completedFuture(false);
      return loaded
        .thenAcceptAsync(ignored -> {
          if (recomputeLoading(serverContext, methods)) {
            changed.add(uri);
          }
        }, resolveReturnTypesExecutor)
        .whenComplete((ignored, error) -> {
          if (preload != null) {
            release(methods.get(0).owner(), preload.getNow(false));
          }
          progress.done(1);
        });
    }

    /**
     * Свободные места занимаются впрок разбором тех, кого оборот пересчитает наверняка, по
     * порядку компоненты. Не больше предела без одного: последнее место остаётся документу,
     * который понадобится не наверняка, — иначе места могли бы занять разборы впереди,
     * ждущие его самого.
     */
    @Override
    void spare() {
      while (preloads.size() < MAX_DOCUMENTS_IN_MEMORY - 1 && nextCertain < certain.size()) {
        int item = certain.get(nextCertain);
        if (!isPending(item)) {
          nextCertain++;
          continue;
        }
        if (!reserve(item)) {
          return;
        }
        nextCertain++;
        preloads.put(item, preload(byDocument.get(component.get(item)).get(0).owner()));
      }
    }

    /**
     * Отпускает разобранное впереди, но так и не понадобившееся: так бывает, если оборот упал.
     *
     * @return окончание освобождения.
     */
    private CompletableFuture<Void> releaseUnused() {
      List<Map.Entry<Integer, CompletableFuture<Boolean>>> unused;
      synchronized (this) {
        unused = List.copyOf(preloads.entrySet());
        preloads.clear();
      }
      return CompletableFuture.allOf(unused.stream()
        .map(entry -> entry.getValue().thenAccept(loaded ->
          release(byDocument.get(component.get(entry.getKey())).get(0).owner(), loaded)))
        .toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<Boolean> preload(DocumentContext documentContext) {
      try {
        return CompletableFuture
          .supplyAsync(() -> loadIfReleased(documentContext), resolveReturnTypesExecutor)
          .exceptionally((Throwable e) -> {
            // Не разобрался впереди — разберётся на месте, как без этого разбора.
            LOGGER.warn("Can't preload document {}", documentContext.getUri(), e);
            return false;
          });
      } catch (RejectedExecutionException e) {
        return CompletableFuture.completedFuture(false);
      }
    }

    /**
     * Разбирает документ, если его вторичные данные освобождены.
     *
     * @param documentContext документ.
     * @return {@code true}, если документ разобран здесь: тогда и отпускать его здесь.
     */
    private boolean loadIfReleased(DocumentContext documentContext) {
      var lock = serverContext.getDocumentLock(documentContext.getUri()).writeLock();
      lock.lock();
      try {
        if (isReadable(documentContext)) {
          return false;
        }
        rebuilt.incrementAndGet();
        rebuiltUris.add(documentContext.getUri());
        serverContext.rebuildDocument(documentContext);
        // Файл мог не прочитаться: тогда документ не разобран, и отпускать его нечего.
        return isReadable(documentContext);
      } finally {
        lock.unlock();
      }
    }

    /**
     * Отпускает документ, разобранный впереди.
     *
     * @param documentContext документ.
     * @param loaded          разобран ли он разбором впереди: иначе отпускать его не этому.
     */
    private void release(DocumentContext documentContext, boolean loaded) {
      if (!loaded) {
        return;
      }
      var lock = serverContext.getDocumentLock(documentContext.getUri()).writeLock();
      lock.lock();
      try {
        serverContext.tryClearDocument(documentContext);
      } finally {
        lock.unlock();
      }
    }
  }

  /**
   * Устарел ли расчёт методов документа: документ, на значениях которого он построен,
   * изменился уже после него.
   *
   * @param uri     URI документа.
   * @param methods отложенные методы документа.
   * @return {@code true}, если пересчёт может дать другой итог.
   */
  private boolean isOutdated(URI uri, List<MethodKey> methods) {
    var dependencies = dependenciesByUri.get(uri);
    if (dependencies == null) {
      return false;
    }
    var computed = methods.stream()
      .mapToLong(method -> computedAt.getOrDefault(method, 0L))
      .min()
      .orElse(0L);
    for (var dependency : dependencies) {
      if (changedAt.getOrDefault(dependency, 0L) > computed) {
        return true;
      }
    }
    return false;
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
  private boolean recomputeLoading(ServerContext serverContext, List<MethodKey> methods) {
    var documentContext = methods.get(0).owner();
    var lock = serverContext.getDocumentLock(documentContext.getUri());
    lock.readLock().lock();
    try {
      if (isReadable(documentContext)) {
        return recomputeEach(currentSymbols(methods));
      }
    } finally {
      lock.readLock().unlock();
    }
    lock.writeLock().lock();
    try {
      // Пока ждали блокировку, документ мог догрузить другой поток: тогда разбирать его
      // заново не надо, а освобождать — тем более, он сейчас кому-то нужен.
      if (isReadable(documentContext)) {
        return recomputeEach(currentSymbols(methods));
      }
      rebuilt.incrementAndGet();
      rebuiltUris.add(documentContext.getUri());
      // Разбор сам пересчитает экспортные методы документа по событию, а неэкспортные,
      // до которых дошла очередь, пересчитываются явно.
      serverContext.rebuildDocument(documentContext);
      if (!isReadable(documentContext)) {
        // Файл не прочитался: тел нет, и расчёт затёр бы посчитанное пустым. Прежние значения
        // остаются, а документ пересчитается, когда разберётся, — повтор в этом же проходе
        // файла не вернёт, только повторит ошибку чтения.
        return false;
      }
      var changed = recomputeEach(currentSymbols(methods));
      serverContext.tryClearDocument(documentContext);
      return changed;
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Символы методов из последнего дерева их документа.
   * <p>
   * Документ, разобранный заново с тем же текстом, подставляет в запомненное символы своего
   * нового дерева ({@link #rememberCurrentSymbols}), поэтому брать их надо после разбора.
   * Метода, записи о котором уже нет, — документ изменился или удалён, и его методы
   * посчитаны заново, — среди них нет.
   *
   * @param methods ключи методов.
   * @return символы методов.
   */
  private List<MethodSymbol> currentSymbols(List<MethodKey> methods) {
    var current = new ArrayList<MethodSymbol>(methods.size());
    for (var key : methods) {
      var method = indexed.get(key);
      if (method != null) {
        current.add(method);
      }
    }
    return current;
  }

  /**
   * Пересчитывает перечисленные методы одного, уже разобранного документа.
   * <p>
   * Пока до метода не дошла очередь, его значение для соседей по документу устарело.
   * Связей внутри документа индекс не хранит, и порядок пересчёта их не учитывает, поэтому
   * методы на это время помечаются предварительными: соседка, вызвавшая такой метод раньше,
   * чем до него дошла очередь, пересчитает его на месте, а не возьмёт прежнее значение
   * себе навсегда.
   *
   * @param methods методы; может быть пуст — тогда пересчитывать нечего.
   * @return {@code true}, если хоть у одного набор типов изменился.
   */
  private boolean recomputeEach(List<MethodSymbol> methods) {
    if (methods.isEmpty()) {
      return false;
    }
    var before = methods.stream().map(symbolTypeIndex::getReturnTypes).toList();
    methods.forEach(method -> provisional.add(MethodKey.of(method)));
    for (var method : methods) {
      // Пометка «посчитан» не снимается: пересчёт и так идёт напрямую, а без пометки
      // соседний метод, читающий этот прямо сейчас, счёл бы его непосчитанным и снова
      // ушёл бы в отложенные — очередь не сходилась бы.
      recompute(method);
    }
    // Изменение считается по значениям до и после: метод мог пересчитаться раньше своей
    // очереди — по запросу соседки, — и собственный пересчёт тогда уже ничего не меняет.
    return IntStream.range(0, methods.size())
      .anyMatch(index -> !symbolTypeIndex.getReturnTypes(methods.get(index)).equals(before.get(index)));
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
