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

import com.github._1c_syntax.bsl.languageserver.configuration.GlobalLanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.DocumentState;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextPopulatedEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.client.WorkDoneProgressHelper;
import com.github._1c_syntax.bsl.languageserver.types.index.MethodReturnTypeIndexer.ComputedReturnTypes;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.utils.Absolute;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.List;
import java.util.Set;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Связи между методами: правка документа должна доводить новое значение до тех, чьи
 * значения на нём построены.
 */
class MethodReturnTypeIndexerTest {

  private static final TypeSet STRING = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Строка"));
  private static final TypeSet NUMBER = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Число"));

  /** Сколько документов большой цикл разбирает впереди расчёта: предел разом разобранных без одного. */
  private static final int AHEAD_WINDOW = 7;

  /** Предел разом разобранных документов: окно впереди и документ, который считается. */
  private static final int DOCUMENTS_IN_MEMORY = 8;

  private ExpressionTypeInferencer inferencer;
  private SymbolTypeIndex symbolTypeIndex;
  private MethodReturnTypeIndexer indexer;
  private ExecutorService resolveExecutor;

  private DocumentContext source;
  private DocumentContext consumer;
  private MethodSymbol sourceMethod;
  private MethodSymbol consumerMethod;

  @BeforeEach
  void prepare() {
    inferencer = mock(ExpressionTypeInferencer.class);
    symbolTypeIndex = mock(SymbolTypeIndex.class);
    // Язык нужен настоящий: на нём индекс берёт тексты для индикатора хода работы.
    var configuration = mock(GlobalLanguageServerConfiguration.class);
    when(configuration.getLanguage()).thenReturn(Language.RU);
    // Ярусы прохода считаются на пуле; здесь он однопоточный, чтобы проверялось поведение
    // прохода, а не работа пула.
    resolveExecutor = Executors.newSingleThreadExecutor();
    indexer = new MethodReturnTypeIndexer(
      inferencer,
      symbolTypeIndex,
      mock(WorkDoneProgressHelper.class, RETURNS_DEEP_STUBS),
      configuration,
      resolveExecutor
    );

    source = document("file:///source.bsl");
    consumer = document("file:///consumer.bsl");
    sourceMethod = method(source, true);
    consumerMethod = method(consumer, false);

    when(source.getSymbolTree().getMethods()).thenReturn(List.of(sourceMethod));
    when(consumer.getSymbolTree().getMethods()).thenReturn(List.of(consumerMethod));

    // Значения запоминаются по-настоящему: по ним индекс решает, изменился ли метод.
    var stored = new ConcurrentHashMap<MethodSymbol, TypeSet>();
    when(symbolTypeIndex.getReturnTypes(any()))
      .thenAnswer(invocation -> stored.getOrDefault(invocation.getArgument(0), TypeSet.EMPTY));
    doAnswer(invocation -> stored.put(invocation.getArgument(0), invocation.getArgument(1)))
      .when(symbolTypeIndex).putReturnTypes(any(), any());

    returns(sourceMethod, TypeSet.EMPTY);
    returns(consumerMethod, TypeSet.EMPTY, sourceMethod);
  }

  @AfterEach
  void stopExecutor() {
    resolveExecutor.shutdownNow();
  }

  @Test
  void changedDocumentReachesItsConsumers() {
    // given: документ-источник разобран, а на его методе построено значение потребителя.
    indexer.handleContentChanged(new DocumentContextContentChangedEvent(source));
    indexer.computeIfAbsent(consumerMethod,
      () -> new ComputedReturnTypes(TypeSet.EMPTY, Set.of(sourceMethod), false));
    clearInvocations(inferencer);

    // when: содержимое источника изменилось, и значение его метода стало другим.
    returns(sourceMethod, STRING);
    returns(consumerMethod, NUMBER, sourceMethod);
    indexer.handleContentChanged(new DocumentContextContentChangedEvent(source));

    // then: потребитель пересчитан — иначе он остался бы со старым значением навсегда.
    verify(inferencer).computeReturnTypes(consumerMethod);
  }

  @Test
  void callerReadsSiblingRecomputedInSamePass() {
    // given: в документе потребителя две функции, и пересчитываются они в порядке, где
    // вызывающая стоит первой. Порядок этот от связей внутри документа не зависит: на
    // доразрешении его задаёт набор отложенных методов. Здесь он задан первым разбором, где
    // вызывающая соседку ещё не спрашивала.
    var caller = method(consumer, true);
    var callee = method(consumer, true);
    when(consumer.getSymbolTree().getMethods()).thenReturn(List.of(caller, callee));
    returns(caller, STRING);
    returns(callee, STRING, sourceMethod);
    returns(sourceMethod, STRING);
    indexer.handleContentChanged(new DocumentContextContentChangedEvent(source));
    indexer.handleContentChanged(new DocumentContextContentChangedEvent(consumer));
    // Дальше вызывающая берёт значение у соседки тем же путём, что вывод типов, — через
    // computeIfAbsent, а соседка — у метода источника.
    when(inferencer.computeReturnTypes(caller)).thenAnswer(invocation -> {
      indexer.computeIfAbsent(callee, () -> inferencer.computeReturnTypes(callee));
      return new ComputedReturnTypes(symbolTypeIndex.getReturnTypes(callee), Set.of(callee), false);
    });
    when(inferencer.computeReturnTypes(callee)).thenAnswer(invocation ->
      new ComputedReturnTypes(symbolTypeIndex.getReturnTypes(sourceMethod), Set.of(sourceMethod), false));

    // when: значение метода источника изменилось, и документ потребителя пересчитывается.
    returns(sourceMethod, NUMBER);
    indexer.handleContentChanged(new DocumentContextContentChangedEvent(source));

    // then: вызывающая прочла соседку уже пересчитанной. Связей внутри документа индекс не
    // хранит, поэтому порядок пересчёта их не учитывает: без этого вызывающая, стоящая
    // первой, взяла бы прежнее значение соседки и оставила бы его себе навсегда.
    assertThat(symbolTypeIndex.getReturnTypes(callee)).isEqualTo(NUMBER);
    assertThat(symbolTypeIndex.getReturnTypes(caller)).isEqualTo(NUMBER);
  }

  @Test
  void unchangedDocumentLeavesConsumersAlone() {
    // given: документ-источник разобран, а на его методе построено значение потребителя.
    indexer.handleContentChanged(new DocumentContextContentChangedEvent(source));
    indexer.computeIfAbsent(consumerMethod,
      () -> new ComputedReturnTypes(TypeSet.EMPTY, Set.of(sourceMethod), false));
    clearInvocations(inferencer);

    // when: источник перечитан, но значение его метода прежнее.
    indexer.handleContentChanged(new DocumentContextContentChangedEvent(source));

    // then: пересчитывать потребителя незачем.
    verify(inferencer, never()).computeReturnTypes(consumerMethod);
  }

  @Test
  void rereadDocumentKeepsItsValues() {
    // given: документ разобран, значения его методов посчитаны.
    returns(sourceMethod, STRING);
    indexer.handleContentChanged(new DocumentContextContentChangedEvent(source));
    clearInvocations(inferencer);

    // when: тот же самый текст перечитан заново.
    indexer.handleContentChanged(new DocumentContextContentChangedEvent(source, false));

    // then: посчитанное осталось в силе и заново не считалось — иначе в чужом потоке
    // нашлась бы дыра на месте уже известного значения.
    assertThat(indexer.isIndexed(sourceMethod)).isTrue();
    verify(inferencer, never()).computeReturnTypes(sourceMethod);
  }

  @Test
  void provisionalValueServesApproximateRequestsAndIsReplacedByFinalOne() {
    // given: значение посчитано по приближениям — посреди кругов по ячейкам.
    indexer.computeIfAbsent(consumerMethod,
      () -> new ComputedReturnTypes(STRING, Set.of(), false), true);

    // when: ещё один запрос по приближениям, а за ним — первый окончательный.
    indexer.computeIfAbsent(consumerMethod,
      () -> new ComputedReturnTypes(TypeSet.EMPTY, Set.of(), false), true);
    var approximate = symbolTypeIndex.getReturnTypes(consumerMethod);
    var finalBefore = indexer.isIndexed(consumerMethod);
    indexer.computeIfAbsent(consumerMethod,
      () -> new ComputedReturnTypes(NUMBER, Set.of(), false));

    // then: пока идут приближения, значение одно и то же — иначе цепочка вызовов
    // пересчитывалась бы лавинообразно; окончательный запрос считает заново. До него
    // значение окончательным не считается — опирающийся на него расчёт придётся повторить.
    assertThat(approximate).isEqualTo(STRING);
    assertThat(finalBefore).isFalse();
    assertThat(symbolTypeIndex.getReturnTypes(consumerMethod)).isEqualTo(NUMBER);
    assertThat(indexer.isIndexed(consumerMethod)).isTrue();
  }

  @Test
  void recomputationOfDocumentMakesProvisionalValueFinal() {
    // given: экспортная функция посчитана по приближениям раньше, чем до неё дошёл
    // пересчёт её документа.
    indexer.computeIfAbsent(sourceMethod,
      () -> new ComputedReturnTypes(STRING, Set.of(), false), true);
    returns(sourceMethod, NUMBER);

    // when
    indexer.handleContentChanged(new DocumentContextContentChangedEvent(source));

    // then
    assertThat(symbolTypeIndex.getReturnTypes(sourceMethod)).isEqualTo(NUMBER);
    assertThat(indexer.isIndexed(sourceMethod)).isTrue();
  }

  @Test
  void deferredMethodIsRecomputedAfterWorkspaceIsPopulated() {
    // given: при разборе значение метода вышло неполным — вызванный метод ещё не посчитан.
    returns(consumerMethod, TypeSet.EMPTY, sourceMethod);
    indexer.computeIfAbsent(consumerMethod,
      () -> new ComputedReturnTypes(TypeSet.EMPTY, Set.of(sourceMethod), true));
    clearInvocations(inferencer);

    // when: рабочая область наполнена.
    indexer.handleServerContextPopulated(new ServerContextPopulatedEvent(serverContextOf(consumer)));

    // then: отложенный метод пересчитан на месте — документ разобран, догружать нечего.
    verify(inferencer).computeReturnTypes(consumerMethod);
  }

  @Test
  void passStopsOnceWaveChangesNothing() {
    // given: расчёт метода неполон каждый раз, а значение у него одно и то же — неполнота
    // не временная, и пересчёт её не снимет.
    when(inferencer.computeReturnTypes(consumerMethod))
      .thenReturn(new ComputedReturnTypes(STRING, Set.of(sourceMethod), true));
    indexer.computeIfAbsent(consumerMethod,
      () -> new ComputedReturnTypes(STRING, Set.of(sourceMethod), true));
    clearInvocations(inferencer);

    // when: рабочая область наполнена.
    indexer.handleServerContextPopulated(new ServerContextPopulatedEvent(serverContextOf(consumer)));

    // then: волна не изменила ни одного значения, и следующих нет — они посчитали бы то же.
    verify(inferencer, times(1)).computeReturnTypes(consumerMethod);
  }

  @Test
  void consumerOutdatedWhilePopulatingIsRecomputedAfterWaveThatChangesNothing() {
    // given: потребитель посчитан на источнике, и документ его отпущен.
    var other = document("file:///other.bsl");
    var otherMethod = method(other, false);
    when(other.getSymbolTree().getMethods()).thenReturn(List.of(otherMethod));
    var serverContext = serverContextOf(source, consumer, other);
    indexer.computeIfAbsent(consumerMethod,
      () -> new ComputedReturnTypes(STRING, Set.of(sourceMethod), false));
    trackLoading(serverContext, consumer);
    when(inferencer.computeReturnTypes(consumerMethod)).thenAnswer(invocation ->
      new ComputedReturnTypes(symbolTypeIndex.getReturnTypes(sourceMethod), Set.of(sourceMethod), false));

    // given: значение источника поменялось после — разнос до отпущенного потребителя не дошёл.
    indexer.computeIfAbsent(sourceMethod, () -> new ComputedReturnTypes(NUMBER, Set.of(), false));

    // given: отложен посторонний метод, и его пересчёт ничего не меняет.
    when(inferencer.computeReturnTypes(otherMethod)).thenReturn(new ComputedReturnTypes(STRING, Set.of(), true));
    indexer.computeIfAbsent(otherMethod, () -> new ComputedReturnTypes(STRING, Set.of(), true));

    // when: рабочая область наполнена.
    indexer.handleServerContextPopulated(new ServerContextPopulatedEvent(serverContext));

    // then: первая волна не изменила ни одного значения, но потребитель устарел ещё до неё и
    // пересчитан по новому значению источника.
    assertThat(symbolTypeIndex.getReturnTypes(consumerMethod)).isEqualTo(NUMBER);
  }

  @Test
  void emptyValuedFunctionIsRecomputedAfterWorkspaceIsPopulated() {
    // given: при разборе значение функции вышло пустым, и неполным расчёт себя не считает:
    // вызов вёл в модуль, ещё не зарегистрированный в рабочей области, поэтому не разрешился
    // ни во что — и о пропущенном сообщить было некому.
    returns(sourceMethod, TypeSet.EMPTY);
    indexer.handleContentChanged(new DocumentContextContentChangedEvent(source));
    clearInvocations(inferencer);

    // when: рабочая область наполнена, и теперь тот же вызов разрешается.
    returns(sourceMethod, STRING);
    indexer.handleServerContextPopulated(new ServerContextPopulatedEvent(serverContextOf(source)));

    // then: пустое значение пересчитано. Иначе функция навсегда осталась бы без типа, а
    // повезло ей или нет — решал бы порядок наполнения, разный от запуска к запуску.
    assertThat(symbolTypeIndex.getReturnTypes(sourceMethod)).isEqualTo(STRING);
  }

  @Test
  void consumerOfDeferredMethodIsRecomputedToo() {
    // given: значение источника вышло неполным и отложено, а значение потребителя посчитано
    // на нём — то есть на значении, которое само ещё ждёт пересчёта. Сам потребитель
    // неполным не считается: вызванный метод посчитан, просто посчитан предварительно.
    indexer.computeIfAbsent(sourceMethod, () -> new ComputedReturnTypes(TypeSet.EMPTY, Set.of(), true));
    indexer.computeIfAbsent(consumerMethod,
      () -> new ComputedReturnTypes(STRING, Set.of(sourceMethod), false));
    returns(sourceMethod, NUMBER);
    when(inferencer.computeReturnTypes(consumerMethod)).thenAnswer(invocation ->
      new ComputedReturnTypes(symbolTypeIndex.getReturnTypes(sourceMethod), Set.of(sourceMethod), false));

    // when: рабочая область наполнена.
    indexer.handleServerContextPopulated(new ServerContextPopulatedEvent(serverContextOf(source, consumer)));

    // then: потребитель пересчитан по доразрешённому значению источника. Иначе он навсегда
    // остался бы с предварительным, а каким оно вышло, решает порядок наполнения.
    assertThat(symbolTypeIndex.getReturnTypes(consumerMethod)).isEqualTo(NUMBER);
  }

  @Test
  void consumerIsRecomputedWhenDependencyChangesLaterInPass() {
    // given: отложены оба метода. Связь, известная проходу на старте, ведёт от источника к
    // потребителю, поэтому потребитель считается первым. Обратной связи — что сам он
    // построен на источнике — в плане прохода нет: она вскроется только его расчётом.
    indexer.computeIfAbsent(sourceMethod,
      () -> new ComputedReturnTypes(TypeSet.EMPTY, Set.of(consumerMethod), true));
    indexer.computeIfAbsent(consumerMethod,
      () -> new ComputedReturnTypes(TypeSet.EMPTY, Set.of(), true));
    when(inferencer.computeReturnTypes(consumerMethod)).thenAnswer(invocation ->
      new ComputedReturnTypes(symbolTypeIndex.getReturnTypes(sourceMethod), Set.of(sourceMethod), false));
    returns(sourceMethod, NUMBER, consumerMethod);

    // when: рабочая область наполнена.
    indexer.handleServerContextPopulated(new ServerContextPopulatedEvent(serverContextOf(source, consumer)));

    // then: источник изменился уже после того, как потребитель на нём построился, и
    // потребитель пересчитан по новому значению. Иначе он навсегда остался бы с тем, что
    // успело попасться, а что успело — решает порядок разбора, разный от запуска к запуску.
    assertThat(symbolTypeIndex.getReturnTypes(consumerMethod)).isEqualTo(NUMBER);
  }

  @Test
  void releasedDocumentIsLoadedForRecomputeAndReleasedBack() {
    // given: отложенный метод лежит в документе, вторичные данные которого уже освобождены.
    returns(consumerMethod, TypeSet.EMPTY, sourceMethod);
    indexer.computeIfAbsent(consumerMethod,
      () -> new ComputedReturnTypes(TypeSet.EMPTY, Set.of(sourceMethod), true));
    var serverContext = serverContextOf(consumer);
    trackLoading(serverContext, consumer);

    // when
    indexer.handleServerContextPopulated(new ServerContextPopulatedEvent(serverContext));

    // then: документ разобран ради пересчёта и сразу отпущен — иначе на большой рабочей
    // области в памяти осело бы всё, что проход трогал.
    verify(serverContext).rebuildDocument(consumer);
    verify(serverContext).tryClearDocument(consumer);
  }

  @Test
  void documentWhoseFileFailedToReadKeepsItsValues() {
    // given: у отложенного метода посчитанное значение, документ освобождён, а файл его при
    // догрузке не читается — документ так и остаётся без тел.
    indexer.computeIfAbsent(consumerMethod,
      () -> new ComputedReturnTypes(STRING, Set.of(sourceMethod), true));
    var serverContext = serverContextOf(consumer);
    when(serverContext.getDocumentState(consumer)).thenReturn(DocumentState.WITHOUT_CONTENT);
    clearInvocations(inferencer);

    // when
    indexer.handleServerContextPopulated(new ServerContextPopulatedEvent(serverContext));

    // then: расчёта без тел не было, и посчитанное не затёрто пустым.
    verify(serverContext).rebuildDocument(consumer);
    verify(inferencer, never()).computeReturnTypes(consumerMethod);
    assertThat(symbolTypeIndex.getReturnTypes(consumerMethod)).isEqualTo(STRING);
  }

  @Test
  void documentsOfCycleAreHeldLoadedUntilFixedPoint() {
    // given: два документа ссылаются друг на друга, и оба отложены. Вторичные данные обоих
    // освобождены, так что проходу придётся их догрузить.
    returns(sourceMethod, TypeSet.EMPTY, consumerMethod);
    returns(consumerMethod, TypeSet.EMPTY, sourceMethod);
    indexer.computeIfAbsent(sourceMethod,
      () -> new ComputedReturnTypes(TypeSet.EMPTY, Set.of(consumerMethod), true));
    indexer.computeIfAbsent(consumerMethod,
      () -> new ComputedReturnTypes(TypeSet.EMPTY, Set.of(sourceMethod), true));

    var serverContext = source.getServerContext();
    var documents = Map.of(source.getUri(), source, consumer.getUri(), consumer);
    when(serverContext.getDocumentLock(any())).thenReturn(new ReentrantReadWriteLock());
    when(serverContext.getDocuments()).thenReturn(documents);
    when(serverContext.getDocumentState(any())).thenReturn(DocumentState.WITHOUT_CONTENT);
    when(consumer.getServerContext()).thenReturn(serverContext);

    // when
    indexer.handleServerContextPopulated(new ServerContextPopulatedEvent(serverContext));

    // then: оба документа догружены и отпущены. Цикл крутится на загруженных документах,
    // поэтому повторные обороты не стоят ни одного лишнего разбора.
    verify(serverContext).rebuildDocument(source);
    verify(serverContext).rebuildDocument(consumer);
    verify(serverContext).tryClearDocument(source);
    verify(serverContext).tryClearDocument(consumer);
  }

  @Test
  void cycleTooLargeToHoldReparsesOnlyDocumentsThatMayChange() {
    // given: девять документов по кругу — больше, чем проход держит разобранными разом, —
    // и все отложены. Документ i построен на документе i + 1. Значение меняется только у
    // первого, а последний, построенный на первом, переписывает его значение себе.
    var ring = ring("cycle", 9);
    var documents = ring.documents();
    var methods = ring.methods();
    var first = methods[0];
    var last = methods[methods.length - 1];
    returns(first, STRING, methods[1]);
    when(inferencer.computeReturnTypes(last)).thenAnswer(invocation ->
      new ComputedReturnTypes(symbolTypeIndex.getReturnTypes(first), Set.of(first), false));
    var serverContext = serverContextOf(documents);
    trackLoading(serverContext, documents);

    // when
    indexer.handleServerContextPopulated(new ServerContextPopulatedEvent(serverContext));

    // then: значение первого дошло до последнего.
    assertThat(symbolTypeIndex.getReturnTypes(last)).isEqualTo(STRING);
    // Изменившийся документ разобран на втором обороте ещё раз: циклы внутри него разрывает
    // прежнее значение, и новый заход может посчитать его иначе.
    verify(serverContext, times(2)).rebuildDocument(documents[0]);
    // А тот, у кого на входе ничего не поменялось, — только на первом: повторный разбор дал
    // бы ровно то же самое, а на большой конфигурации такой цикл держит сотни документов.
    verify(serverContext, times(1)).rebuildDocument(documents[4]);
  }

  @Test
  void progressOfCycleTooLargeToHoldIsCountedByDocumentWhileItIsComputed() {
    // given: индикатор, по которому видно, когда отсчитаны документы, и большой цикл, второй
    // оборот которого пересчитывает часть документов заново.
    var progressHelper = mock(WorkDoneProgressHelper.class);
    var reporter = mock(WorkDoneProgressHelper.WorkDoneProgressReporter.class);
    when(progressHelper.createProgress(anyInt(), anyString())).thenReturn(reporter);
    var configuration = mock(GlobalLanguageServerConfiguration.class);
    when(configuration.getLanguage()).thenReturn(Language.RU);
    indexer = new MethodReturnTypeIndexer(inferencer, symbolTypeIndex, progressHelper, configuration, resolveExecutor);
    var ring = ring("cycle", 9);
    var documents = ring.documents();
    var methods = ring.methods();
    var first = methods[0];
    var last = methods[methods.length - 1];
    var firstComputations = new AtomicInteger();
    var ticksBySecondPass = new AtomicInteger(-1);
    when(inferencer.computeReturnTypes(first)).thenAnswer(invocation -> {
      if (firstComputations.incrementAndGet() == 2) {
        ticksBySecondPass.set(ticksOf(reporter));
      }
      return new ComputedReturnTypes(STRING, Set.of(methods[1]), false);
    });
    when(inferencer.computeReturnTypes(last)).thenAnswer(invocation ->
      new ComputedReturnTypes(symbolTypeIndex.getReturnTypes(first), Set.of(first), false));
    var serverContext = serverContextOf(documents);
    trackLoading(serverContext, documents);

    // when
    indexer.handleServerContextPopulated(new ServerContextPopulatedEvent(serverContext));

    // then: к пересчёту первого документа на втором обороте отсчитаны все документы первого —
    // ход виден по ходу расчёта цикла, а не разом после него. Больше их может быть, если
    // по порядку компоненты на втором обороте до него пересчитаны другие.
    assertThat(ticksBySecondPass.get()).isGreaterThanOrEqualTo(documents.length);
    // Пересчитанное на следующих оборотах добавлено в план и отсчитано: отсчёт сходится с планом.
    var sizes = ArgumentCaptor.forClass(Integer.class);
    verify(reporter, atLeastOnce()).setSize(sizes.capture());
    var planned = sizes.getValue();
    assertThat(planned).isGreaterThan(documents.length);
    assertThat(ticksOf(reporter)).isEqualTo(planned);
  }

  @Test
  void cycleTooLargeToHoldIsParsedAheadWithinLimitAndReleased() {
    // Ожидание в расчёте не должно занимать единственный поток предзагрузки.
    var executor = Executors.newFixedThreadPool(2);
    try {
      var configuration = mock(GlobalLanguageServerConfiguration.class);
      when(configuration.getLanguage()).thenReturn(Language.RU);
      indexer = new MethodReturnTypeIndexer(inferencer, symbolTypeIndex,
        mock(WorkDoneProgressHelper.class, RETURNS_DEEP_STUBS), configuration, executor);

      // Двадцать документов по кругу, все отложены и все освобождены.
      var ring = ring("ahead", 20);
      var documents = ring.documents();
      var methods = ring.methods();
      var serverContext = serverContextOf(documents);
      var loading = trackLoading(serverContext, documents);
      var computations = new AtomicInteger();
      for (var i = 0; i < methods.length; i++) {
        var next = methods[(i + 1) % methods.length];
        when(inferencer.computeReturnTypes(methods[i])).thenAnswer(invocation -> {
          // Ждёт фактически первый метод прохода: порядок Tarjan не обязан начинаться с methods[0].
          if (computations.getAndIncrement() == 0) {
            await().atMost(10, SECONDS).until(() -> loading.loaded().size() >= AHEAD_WINDOW);
          }
          return new ComputedReturnTypes(TypeSet.EMPTY, Set.of(next), false);
        });
      }

      indexer.handleServerContextPopulated(new ServerContextPopulatedEvent(serverContext));

      // Каждый документ разобран ровно раз и отпущен. Окно заполнено, лимит не превышен.
      for (var documentContext : documents) {
        verify(serverContext, times(1)).rebuildDocument(documentContext);
      }
      assertThat(loading.loaded()).isEmpty();
      assertThat(loading.peak().get()).isBetween(AHEAD_WINDOW, DOCUMENTS_IN_MEMORY);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void componentStartsAsSoonAsItsDependenciesAreComputed() throws InterruptedException {
    // given: потребитель построен на источнике, а третий документ ни от кого не зависит и
    // считается, пока не начнётся потребитель. Пул на несколько потоков: работа идёт разом.
    var executor = Executors.newFixedThreadPool(4);
    try {
      indexer = indexerOn(executor);
      var slow = document("file:///slow.bsl");
      var slowMethod = method(slow, true);
      when(slow.getSymbolTree().getMethods()).thenReturn(List.of(slowMethod));
      var consumerStarted = new CountDownLatch(1);
      when(inferencer.computeReturnTypes(consumerMethod)).thenAnswer(invocation -> {
        consumerStarted.countDown();
        return new ComputedReturnTypes(TypeSet.EMPTY, Set.of(sourceMethod), false);
      });
      when(inferencer.computeReturnTypes(slowMethod)).thenAnswer(invocation ->
        new ComputedReturnTypes(consumerStarted.await(10, SECONDS) ? STRING : NUMBER, Set.of(), false));
      indexer.computeIfAbsent(sourceMethod, () -> new ComputedReturnTypes(TypeSet.EMPTY, Set.of(), true));
      indexer.computeIfAbsent(consumerMethod,
        () -> new ComputedReturnTypes(TypeSet.EMPTY, Set.of(sourceMethod), true));
      indexer.computeIfAbsent(slowMethod, () -> new ComputedReturnTypes(TypeSet.EMPTY, Set.of(), true));
      var serverContext = serverContextOf(source, consumer, slow);

      // when
      indexer.handleServerContextPopulated(new ServerContextPopulatedEvent(serverContext));

      // then: потребитель начался, пока независимый документ ещё считался, — ждать его
      // потребителю незачем, хотя по ярусам он стоит вместе с источником.
      assertThat(symbolTypeIndex.getReturnTypes(slowMethod)).isEqualTo(STRING);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void documentsOfLargeCycleUnrelatedToEachOtherAreComputedTogether() {
    // given: большой цикл звездой — центр построен на каждом луче, а каждый луч на центре;
    // друг с другом лучи не связаны. Документов больше, чем держится разобранными разом.
    var executor = Executors.newFixedThreadPool(4);
    try {
      indexer = indexerOn(executor);
      var hub = document("file:///hub.bsl");
      var hubMethod = method(hub, true);
      var documents = new ArrayList<DocumentContext>(List.of(hub));
      var spokes = new ArrayList<MethodSymbol>();
      for (var index = 0; index < 9; index++) {
        var spoke = document("file:///spoke" + index + ".bsl");
        documents.add(spoke);
        spokes.add(method(spoke, true));
      }
      var inside = new AtomicInteger();
      var together = new CountDownLatch(2);
      var spokesMetEachOther = new AtomicBoolean(true);
      var hubAlone = new AtomicBoolean(true);
      for (var spoke : spokes) {
        when(inferencer.computeReturnTypes(spoke)).thenAnswer(invocation -> {
          inside.incrementAndGet();
          try {
            together.countDown();
            if (!together.await(10, SECONDS)) {
              spokesMetEachOther.set(false);
            }
            return new ComputedReturnTypes(TypeSet.EMPTY, Set.of(hubMethod), false);
          } finally {
            inside.decrementAndGet();
          }
        });
        indexer.computeIfAbsent(spoke, () -> new ComputedReturnTypes(TypeSet.EMPTY, Set.of(hubMethod), true));
      }
      when(inferencer.computeReturnTypes(hubMethod)).thenAnswer(invocation -> {
        if (inside.get() > 0) {
          hubAlone.set(false);
        }
        return new ComputedReturnTypes(TypeSet.EMPTY, Set.copyOf(spokes), false);
      });
      indexer.computeIfAbsent(hubMethod, () -> new ComputedReturnTypes(TypeSet.EMPTY, Set.copyOf(spokes), true));
      var serverContext = serverContextOf(documents.toArray(DocumentContext[]::new));

      // when
      indexer.handleServerContextPopulated(new ServerContextPopulatedEvent(serverContext));

      // then: лучи считались разом — порядок обхода их друг от друга не отделяет, — а центр
      // ни с одним лучом не пересёкся: каждому из них нужен его итог либо прежнее значение.
      assertThat(spokesMetEachOther).isTrue();
      assertThat(hubAlone).isTrue();
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void documentWhoseParseAheadFailedIsParsedInPlace() {
    // given: большой цикл, и разбор одного из документов впереди расчёта падает.
    var ring = ring("failedAhead", 9);
    var serverContext = serverContextOf(ring.documents());
    var loading = trackLoading(serverContext, ring.documents());
    var broken = ring.documents()[3];
    doThrow(new IllegalStateException("файл занят"))
      .doAnswer(invocation -> loading.load(broken))
      .when(serverContext).rebuildDocument(broken);

    // when
    indexer.handleServerContextPopulated(new ServerContextPopulatedEvent(serverContext));

    // then: документ разобран на месте, как без разбора впереди, посчитан и отпущен.
    verify(serverContext, times(2)).rebuildDocument(broken);
    verify(inferencer).computeReturnTypes(ring.methods()[3]);
    assertThat(loading.loaded()).isEmpty();
  }

  @Test
  void documentWhoseFileFailedToReadAheadKeepsItsValues() {
    // given: большой цикл, и файл одного из документов не читается вовсе.
    var ring = ring("unreadableAhead", 9);
    var serverContext = serverContextOf(ring.documents());
    var loading = trackLoading(serverContext, ring.documents());
    var unreadable = ring.documents()[3];
    doNothing().when(serverContext).rebuildDocument(unreadable);
    clearInvocations(inferencer);

    // when
    indexer.handleServerContextPopulated(new ServerContextPopulatedEvent(serverContext));

    // then: впереди документ не разобрался, на месте — тоже, и без тел его не считали.
    verify(serverContext, times(2)).rebuildDocument(unreadable);
    verify(inferencer, never()).computeReturnTypes(ring.methods()[3]);
    verify(serverContext, never()).tryClearDocument(unreadable);
    assertThat(loading.loaded()).isEmpty();
  }

  @Test
  void documentOpenInEditorIsNotReleasedByParseAhead() {
    // given: большой цикл, один из документов открыт в редакторе — его вторичные данные на месте.
    var ring = ring("openAhead", 9);
    var serverContext = serverContextOf(ring.documents());
    var open = ring.documents()[3];
    var released = Arrays.stream(ring.documents()).filter(document -> document != open)
      .toArray(DocumentContext[]::new);
    trackLoading(serverContext, released);

    // when
    indexer.handleServerContextPopulated(new ServerContextPopulatedEvent(serverContext));

    // then: открытый документ не разбирали и не отпускали — он нужен редактору.
    verify(serverContext, never()).rebuildDocument(open);
    verify(serverContext, never()).tryClearDocument(open);
    verify(inferencer).computeReturnTypes(ring.methods()[3]);
  }

  @Test
  void preloadedDocumentThatBecameCurrentIsReleasedBeforePassCompletes() {
    // Все документы связаны друг с другом: порядок обхода произволен, но расчёт последовательный.
    var executor = Executors.newFixedThreadPool(4);
    try {
      indexer = indexerOn(executor);
      var ring = ring("skippedAhead", 9);
      var documents = ring.documents();
      var methods = ring.methods();
      var consulted = Set.of(methods);
      var order = new CopyOnWriteArrayList<MethodSymbol>();
      var refreshed = new CountDownLatch(1);
      var released = new CountDownLatch(1);
      var releasedDuringPass = new AtomicBoolean();
      for (var method : methods) {
        var document = method.getOwner();
        when(document.getSymbolTree().getMethods()).thenReturn(List.of(method));
        indexer.clear(document.getUri());
        indexer.computeIfAbsent(method, () -> new ComputedReturnTypes(TypeSet.EMPTY, consulted, true));
        var computations = new AtomicInteger();
        when(inferencer.computeReturnTypes(method)).thenAnswer(invocation -> {
          var computation = computations.incrementAndGet();
          if (computation == 1) {
            order.add(method);
          }
          var position = order.indexOf(method);
          if (computation == 2 && position == 0) {
            assertThat(refreshed.await(10, SECONDS)).isTrue();
          }
          if (computation == 2 && position == 7) {
            releasedDuringPass.set(released.await(5, SECONDS));
          }
          // Изменение последнего документа делает середину устаревшей ко второму обороту.
          var types = position == 0 || position == 8 ? STRING : TypeSet.EMPTY;
          return new ComputedReturnTypes(types, consulted, false);
        });
      }
      var serverContext = serverContextOf(documents);
      var loading = trackLoading(serverContext, documents);
      Map<DocumentContext, AtomicInteger> parses = new ConcurrentHashMap<>();
      doAnswer(invocation -> {
        DocumentContext document = invocation.getArgument(0);
        loading.load(document);
        var parse = parses.computeIfAbsent(document, ignored -> new AtomicInteger()).incrementAndGet();
        if (parse == 2 && document == order.get(4).getOwner()) {
          // Перечитывание изменённого текста уже пересчитало методы; очередная работа не нужна.
          indexer.handleContentChanged(new DocumentContextContentChangedEvent(document));
          refreshed.countDown();
        }
        return null;
      }).when(serverContext).rebuildDocument(any(DocumentContext.class));
      doAnswer(invocation -> {
        DocumentContext document = invocation.getArgument(0);
        if (loading.loaded().remove(document)) {
          loading.unloaded().add(document);
        }
        if (refreshed.getCount() == 0 && document == order.get(4).getOwner()) {
          released.countDown();
        }
        return true;
      }).when(serverContext).tryClearDocument(any());

      indexer.handleServerContextPopulated(new ServerContextPopulatedEvent(serverContext));

      assertThat(releasedDuringPass).isTrue();
      assertThat(parses.get(order.get(4).getOwner())).hasValue(2);
      assertThat(loading.loaded()).isEmpty();
      assertThat(loading.peak().get()).isLessThanOrEqualTo(DOCUMENTS_IN_MEMORY);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void stoppingLargeCycleReleasesPreloadsAndSkipsRemainingMethods() {
    var ring = ring("stoppedPass", 9);
    var serverContext = serverContextOf(ring.documents());
    var loading = trackLoading(serverContext, ring.documents());
    var stopped = new AtomicBoolean();
    when(serverContext.isBackgroundWorkStopped()).thenAnswer(invocation -> stopped.get());
    doAnswer(invocation -> {
      assertThat(stopped).isFalse();
      return loading.load(invocation.getArgument(0));
    }).when(serverContext).rebuildDocument(any(DocumentContext.class));
    when(inferencer.computeReturnTypes(any())).thenAnswer(invocation -> {
      stopped.set(true);
      return new ComputedReturnTypes(STRING, Set.of(), false);
    });
    clearInvocations(inferencer);

    indexer.handleServerContextPopulated(new ServerContextPopulatedEvent(serverContext));

    verify(inferencer, times(1)).computeReturnTypes(any());
    assertThat(loading.loaded()).isEmpty();
    assertThat(loading.peak().get()).isLessThanOrEqualTo(DOCUMENTS_IN_MEMORY);
  }

  @Test
  void stoppedWorkspaceDoesNotLoadPendingCycle() {
    var ring = ring("alreadyStopped", 9);
    var serverContext = serverContextOf(ring.documents());
    trackLoading(serverContext, ring.documents());
    when(serverContext.isBackgroundWorkStopped()).thenReturn(true);
    clearInvocations(inferencer);

    indexer.handleServerContextPopulated(new ServerContextPopulatedEvent(serverContext));

    verify(serverContext, never()).rebuildDocument(any(DocumentContext.class));
    verify(inferencer, never()).computeReturnTypes(any());
  }

  @Test
  void parseAheadIsReleasedWhenPassFails() {
    // given: большой цикл, и расчёт первого же документа падает.
    var ring = ring("failedPass", 9);
    var serverContext = serverContextOf(ring.documents());
    var loading = trackLoading(serverContext, ring.documents());
    when(inferencer.computeReturnTypes(ring.methods()[0])).thenThrow(new IllegalStateException("сбой расчёта"));
    var event = new ServerContextPopulatedEvent(serverContext);

    // when
    assertThatThrownBy(() -> indexer.handleServerContextPopulated(event))
      .isInstanceOf(IllegalStateException.class);

    // then: разобранное впереди отпущено — память не держит то, что расчёт уже не возьмёт.
    assertThat(loading.loaded()).isEmpty();
  }

  /**
   * Документы, отложенные методы которых построены друг на друге по кругу: метод i — на методе
   * i + 1.
   *
   * @param documents документы.
   * @param methods   их отложенные методы, по одному на документ.
   */
  private record Ring(DocumentContext[] documents, MethodSymbol[] methods) {
  }

  /**
   * Круг из отложенных документов: значения пустые и от расчёта не меняются.
   *
   * @param name имя для URI документов.
   * @param size сколько документов.
   * @return круг.
   */
  private Ring ring(String name, int size) {
    var documents = new DocumentContext[size];
    var methods = new MethodSymbol[size];
    for (var index = 0; index < size; index++) {
      documents[index] = document("file:///" + name + index + ".bsl");
      methods[index] = method(documents[index], true);
    }
    for (var index = 0; index < size; index++) {
      var next = methods[(index + 1) % size];
      returns(methods[index], TypeSet.EMPTY, next);
      indexer.computeIfAbsent(methods[index],
        () -> new ComputedReturnTypes(TypeSet.EMPTY, Set.of(next), true));
    }
    return new Ring(documents, methods);
  }

  /**
   * Состояние документов: какие освобождены, какие разобраны проходом и сколько было
   * разобрано разом в худший момент.
   *
   * @param unloaded освобождённые документы.
   * @param loaded   разобранные проходом документы.
   * @param peak     наибольшее их число.
   */
  private record Loading(Set<DocumentContext> unloaded, Set<DocumentContext> loaded, AtomicInteger peak) {

    /**
     * Разбор: освобождённый документ становится читаемым.
     *
     * @param documentContext документ.
     * @return ничего — для заглушки метода без результата.
     */
    @Nullable Void load(DocumentContext documentContext) {
      if (unloaded.remove(documentContext)) {
        loaded.add(documentContext);
        peak.accumulateAndGet(loaded.size(), Math::max);
      }
      return null;
    }
  }

  /**
   * Рабочая область, в которой живут документы: блокировки настоящие, у каждого документа
   * своя, состояние документа спрашивается у неё же.
   *
   * @param documentContexts документы рабочей области.
   * @return рабочая область.
   */
  private static ServerContext serverContextOf(DocumentContext... documentContexts) {
    var serverContext = documentContexts[0].getServerContext();
    var documents = new HashMap<URI, DocumentContext>();
    for (var documentContext : documentContexts) {
      documents.put(documentContext.getUri(), documentContext);
      when(documentContext.getServerContext()).thenReturn(serverContext);
      when(serverContext.getDocumentState(documentContext)).thenReturn(DocumentState.WITH_CONTENT);
    }
    // Общая на всех блокировка сцепила бы потоки, которые в рабочей области друг друга не ждут:
    // расчёт, держащий свой документ, и разбор впереди, берущий чужой.
    Map<URI, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();
    when(serverContext.getDocumentLock(any())).thenAnswer(invocation ->
      locks.computeIfAbsent(invocation.getArgument(0), uri -> new ReentrantReadWriteLock()));
    when(serverContext.getDocuments()).thenReturn(Map.copyOf(documents));
    return serverContext;
  }

  /**
   * Состояние документов как в рабочей области: перечисленные освобождены, разбор делает
   * документ читаемым, освобождение — снова нет.
   *
   * @param serverContext рабочая область.
   * @param released      документы, вторичные данные которых освобождены.
   * @return учёт документов, разобранных проходом.
   */
  private static Loading trackLoading(ServerContext serverContext, DocumentContext... released) {
    var loading = new Loading(ConcurrentHashMap.newKeySet(), ConcurrentHashMap.newKeySet(), new AtomicInteger());
    loading.unloaded().addAll(List.of(released));
    when(serverContext.getDocumentState(any())).thenAnswer(invocation ->
      loading.unloaded().contains(invocation.<DocumentContext>getArgument(0))
        ? DocumentState.WITHOUT_CONTENT
        : DocumentState.WITH_CONTENT);
    doAnswer(invocation -> loading.load(invocation.getArgument(0)))
      .when(serverContext).rebuildDocument(any(DocumentContext.class));
    doAnswer(invocation -> {
      DocumentContext documentContext = invocation.getArgument(0);
      if (loading.loaded().remove(documentContext)) {
        loading.unloaded().add(documentContext);
      }
      return true;
    }).when(serverContext).tryClearDocument(any());
    return loading;
  }

  private static int ticksOf(WorkDoneProgressHelper.WorkDoneProgressReporter reporter) {
    return (int) mockingDetails(reporter).getInvocations().stream()
      .filter(invocation -> invocation.getMethod().getName().equals("tick"))
      .count();
  }

  /**
   * Индекс, проход которого считает на переданном пуле.
   *
   * @param executor пул прохода.
   * @return индекс.
   */
  private MethodReturnTypeIndexer indexerOn(ExecutorService executor) {
    var configuration = mock(GlobalLanguageServerConfiguration.class);
    when(configuration.getLanguage()).thenReturn(Language.RU);
    return new MethodReturnTypeIndexer(
      inferencer,
      symbolTypeIndex,
      mock(WorkDoneProgressHelper.class, RETURNS_DEEP_STUBS),
      configuration,
      executor
    );
  }

  private void returns(MethodSymbol method, TypeSet types, MethodSymbol... consulted) {
    when(inferencer.computeReturnTypes(method))
      .thenReturn(new ComputedReturnTypes(types, Set.of(consulted), false));
  }

  private static DocumentContext document(String uri) {
    var documentContext = mock(DocumentContext.class, RETURNS_DEEP_STUBS);
    var serverContext = mock(ServerContext.class);
    when(documentContext.getUri()).thenReturn(Absolute.uri(uri));
    when(documentContext.getSymbolTree()).thenReturn(mock(SymbolTree.class));
    when(documentContext.getServerContext()).thenReturn(serverContext);
    when(serverContext.getDocumentState(documentContext)).thenReturn(DocumentState.WITH_CONTENT);
    return documentContext;
  }

  private static MethodSymbol method(DocumentContext owner, boolean export) {
    var method = mock(MethodSymbol.class);
    when(method.isFunction()).thenReturn(true);
    when(method.isExport()).thenReturn(export);
    when(method.getOwner()).thenReturn(owner);
    return method;
  }
}
