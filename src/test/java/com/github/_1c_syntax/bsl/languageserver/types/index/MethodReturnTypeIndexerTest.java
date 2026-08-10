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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
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

  private ExpressionTypeInferencer inferencer;
  private SymbolTypeIndex symbolTypeIndex;
  private MethodReturnTypeIndexer indexer;

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
    indexer = new MethodReturnTypeIndexer(
      inferencer,
      symbolTypeIndex,
      mock(WorkDoneProgressHelper.class, RETURNS_DEEP_STUBS),
      configuration
    );

    source = document("file:///source.bsl");
    consumer = document("file:///consumer.bsl");
    sourceMethod = method(source, true);
    consumerMethod = method(consumer, false);

    when(source.getSymbolTree().getMethods()).thenReturn(List.of(sourceMethod));
    when(consumer.getSymbolTree().getMethods()).thenReturn(List.of(consumerMethod));

    // Значения запоминаются по-настоящему: по ним индекс решает, изменился ли метод.
    var stored = new HashMap<MethodSymbol, TypeSet>();
    when(symbolTypeIndex.getReturnTypes(any()))
      .thenAnswer(invocation -> stored.getOrDefault(invocation.getArgument(0), TypeSet.EMPTY));
    // Объявленных типов у этих методов нет, поэтому выведенные и есть всё значение.
    when(symbolTypeIndex.getInferredReturnTypes(any()))
      .thenAnswer(invocation -> stored.getOrDefault(invocation.getArgument(0), TypeSet.EMPTY));
    doAnswer(invocation -> stored.put(invocation.getArgument(0), invocation.getArgument(1)))
      .when(symbolTypeIndex).putReturnTypes(any(), any());

    returns(sourceMethod, TypeSet.EMPTY);
    returns(consumerMethod, TypeSet.EMPTY, sourceMethod);
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
  void changedValueReachesConsumersDuringPostPopulatePass() {
    // given: значение потребителя построено на методе источника, а сам источник отложен.
    indexer.computeIfAbsent(consumerMethod,
      () -> new ComputedReturnTypes(TypeSet.EMPTY, Set.of(sourceMethod), false));
    indexer.computeIfAbsent(sourceMethod,
      () -> new ComputedReturnTypes(TypeSet.EMPTY, Set.of(), true));
    clearInvocations(inferencer);
    // Пересчёт источника в проходе даёт другое значение.
    returns(sourceMethod, STRING);

    // when
    indexer.handleServerContextPopulated(new ServerContextPopulatedEvent(serverContextOf(consumer)));

    // then: потребитель пересчитан. Сам он ничем не помечен — непосчитанного он не видел, —
    // поэтому попасть в работу мог только разносом от изменившегося источника.
    verify(inferencer).computeReturnTypes(consumerMethod);
  }

  @Test
  void passStopsWhenValuesStopChanging() {
    // given: метод отложен и остаётся отложенным — обращение, которое у него не
    // разрешилось, не разрешится и дальше, — но значение его не меняется.
    when(inferencer.computeReturnTypes(consumerMethod))
      .thenReturn(new ComputedReturnTypes(TypeSet.EMPTY, Set.of(sourceMethod), true));
    indexer.computeIfAbsent(consumerMethod,
      () -> new ComputedReturnTypes(TypeSet.EMPTY, Set.of(sourceMethod), true));
    clearInvocations(inferencer);

    // when
    indexer.handleServerContextPopulated(new ServerContextPopulatedEvent(serverContextOf(consumer)));

    // then: одна волна, а не десять до предохранителя.
    verify(inferencer, times(1)).computeReturnTypes(consumerMethod);
  }

  @Test
  void releasedDocumentIsLoadedForRecomputeAndReleasedBack() {
    // given: отложенный метод лежит в документе, вторичные данные которого уже освобождены.
    returns(consumerMethod, TypeSet.EMPTY, sourceMethod);
    indexer.computeIfAbsent(consumerMethod,
      () -> new ComputedReturnTypes(TypeSet.EMPTY, Set.of(sourceMethod), true));
    var serverContext = serverContextOf(consumer);
    when(serverContext.getDocumentState(consumer)).thenReturn(DocumentState.WITHOUT_CONTENT);

    // when
    indexer.handleServerContextPopulated(new ServerContextPopulatedEvent(serverContext));

    // then: документ разобран ради пересчёта и сразу отпущен — иначе на большой рабочей
    // области в памяти осело бы всё, что проход трогал.
    verify(serverContext).rebuildDocument(consumer);
    verify(serverContext).tryClearDocument(consumer);
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

  /**
   * Рабочая область, в которой живёт документ: блокировки настоящие, состояние документа
   * спрашивается у неё же.
   *
   * @param documentContext документ.
   * @return рабочая область.
   */
  private static ServerContext serverContextOf(DocumentContext documentContext) {
    var serverContext = documentContext.getServerContext();
    var documents = Map.of(documentContext.getUri(), documentContext);
    when(serverContext.getDocumentLock(any())).thenReturn(new ReentrantReadWriteLock());
    when(serverContext.getDocuments()).thenReturn(documents);
    return serverContext;
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
