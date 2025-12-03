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
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;

/**
 * Провайдер для построения иерархии вызовов методов и функций.
 * <p>
 * Обрабатывает запросы {@code textDocument/prepareCallHierarchy},
 * {@code callHierarchy/incomingCalls} и {@code callHierarchy/outgoingCalls}.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_prepareCallHierarchy">Call Hierarchy Request specification</a>
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#callHierarchy_incomingCalls">Call Hierarchy Incoming Calls specification</a>
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#callHierarchy_outgoingCalls">Call Hierarchy Outgoing Calls specification</a>
 */
@Component
@RequiredArgsConstructor
public class CallHierarchyProvider {

  private final ReferenceResolver referenceResolver;
  private final ReferenceIndex referenceIndex;

  private final Comparator<CallHierarchyItem> callHierarchyItemComparator = Comparator
    .comparing(CallHierarchyItem::getDetail)
    .thenComparing(CallHierarchyItem::getSelectionRange, Ranges::compare);

  /**
   * Подготовить элементы иерархии вызовов для указанной позиции в документе.
   *
   * @param documentContext Контекст документа
   * @param params          Параметры запроса
   * @return Список элементов иерархии вызовов
   */
  public List<CallHierarchyItem> prepareCallHierarchy(
    DocumentContext documentContext,
    CallHierarchyPrepareParams params
  ) {
    var position = params.getPosition();

    return referenceResolver.findReference(documentContext.getUri(), position)
      .flatMap(Reference::getSourceDefinedSymbol)
      .map(CallHierarchyProvider::getCallHierarchyItem)
      .map(Collections::singletonList)
      .orElse(Collections.emptyList());
  }

  /**
   * Получить входящие вызовы для элемента иерархии.
   *
   * @param documentContext Контекст документа
   * @param params          Параметры запроса
   * @return Список входящих вызовов
   */
  public List<CallHierarchyIncomingCall> incomingCalls(
    DocumentContext documentContext,
    CallHierarchyIncomingCallsParams params
  ) {

    var uri = documentContext.getUri();
    var item = params.getItem();
    var position = item.getSelectionRange().getStart();

    return referenceResolver.findReference(uri, position)
      .flatMap(Reference::getSourceDefinedSymbol)
      .stream()
      .map(referenceIndex::getReferencesTo)
      .flatMap(Collection::stream)
      .collect(groupingBy(
        Reference::getFrom,
        mapping(Reference::getSelectionRange, toCollection(ArrayList::new)))
      )
      .entrySet()
      .stream()
      .map(entry -> new CallHierarchyIncomingCall(getCallHierarchyItem(entry.getKey()), entry.getValue()))
      .sorted((o1, o2) -> callHierarchyItemComparator.compare(o1.getFrom(), o2.getFrom()))
      .toList();
  }

  /**
   * Получить исходящие вызовы для элемента иерархии.
   *
   * @param documentContext Контекст документа
   * @param params          Параметры запроса
   * @return Список исходящих вызовов
   */
  public List<CallHierarchyOutgoingCall> outgoingCalls(
    DocumentContext documentContext,
    CallHierarchyOutgoingCallsParams params
  ) {

    var uri = documentContext.getUri();
    var position = params.getItem().getSelectionRange().getStart();
    return referenceResolver.findReference(uri, position)
      .flatMap(Reference::getSourceDefinedSymbol)
      .stream()
      .map(referenceIndex::getReferencesFrom)
      .flatMap(Collection::stream)
      .filter(Reference::isSourceDefinedSymbolReference)
      .filter(reference -> isSymbolSupported(reference.getSymbol()))
      .collect(groupingBy(
        reference -> reference.getSourceDefinedSymbol().orElseThrow(),
        mapping(Reference::getSelectionRange, toCollection(ArrayList::new)))
      )
      .entrySet()
      .stream()
      .map(entry -> new CallHierarchyOutgoingCall(getCallHierarchyItem(entry.getKey()), entry.getValue()))
      .sorted((o1, o2) -> callHierarchyItemComparator.compare(o1.getTo(), o2.getTo()))
      .toList();
  }

  private static CallHierarchyItem getCallHierarchyItem(SourceDefinedSymbol sourceDefinedSymbol) {
    var detail = sourceDefinedSymbol.getOwner().getMdoRef();

    var item = new CallHierarchyItem();
    item.setName(sourceDefinedSymbol.getName());
    item.setDetail(detail);
    item.setKind(sourceDefinedSymbol.getSymbolKind());
    item.setTags(sourceDefinedSymbol.getTags());
    item.setUri(sourceDefinedSymbol.getOwner().getUri().toString());
    item.setRange(sourceDefinedSymbol.getRange());
    item.setSelectionRange(sourceDefinedSymbol.getSelectionRange());

    return item;
  }

  private static boolean isSymbolSupported(Symbol symbol) {
    return symbol.getSymbolKind() == SymbolKind.Method;
  }
}
