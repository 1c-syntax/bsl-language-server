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
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.utils.MdoRefBuilder;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.Position;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;

@Component
@RequiredArgsConstructor
public class CallHierarchyProvider {

  private final ReferenceResolver referenceResolver;
  private final ReferenceIndex referenceIndex;

  public List<CallHierarchyItem> prepareCallHierarchy(
    DocumentContext documentContext,
    CallHierarchyPrepareParams params
  ) {
    Position position = params.getPosition();

    return referenceResolver.findReference(documentContext.getUri(), position)
      .flatMap(Reference::getSourceDefinedSymbol)
      .map(CallHierarchyProvider::getCallHierarchyItem)
      .map(Collections::singletonList)
      .orElse(Collections.emptyList())
      ;
  }

  public List<CallHierarchyIncomingCall> incomingCalls(
    DocumentContext documentContext,
    CallHierarchyIncomingCallsParams params
  ) {

    URI uri = documentContext.getUri();
    CallHierarchyItem item = params.getItem();
    Position position = item.getSelectionRange().getStart();

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
      .collect(Collectors.toList());

  }

  public List<CallHierarchyOutgoingCall> outgoingCalls(
    DocumentContext documentContext,
    CallHierarchyOutgoingCallsParams params
  ) {

    URI uri = documentContext.getUri();
    Position position = params.getItem().getSelectionRange().getStart();

    return referenceResolver.findReference(uri, position)
      .flatMap(Reference::getSourceDefinedSymbol)
      .stream()
      .map(referenceIndex::getReferencesFrom)
      .flatMap(Collection::stream)
      .filter(Reference::isSourceDefinedSymbolReference)
      .collect(groupingBy(
        reference -> reference.getSourceDefinedSymbol().orElseThrow(),
        mapping(Reference::getSelectionRange, toCollection(ArrayList::new)))
      )
      .entrySet()
      .stream()
      .map(entry -> new CallHierarchyOutgoingCall(getCallHierarchyItem(entry.getKey()), entry.getValue()))
      .collect(Collectors.toList());

  }

  private static CallHierarchyItem getCallHierarchyItem(SourceDefinedSymbol sourceDefinedSymbol) {
    String detail = MdoRefBuilder.getMdoRef(sourceDefinedSymbol.getOwner());

    CallHierarchyItem item = new CallHierarchyItem();
    item.setName(sourceDefinedSymbol.getName());
    item.setDetail(detail);
    item.setKind(sourceDefinedSymbol.getSymbolKind());
    item.setTags(sourceDefinedSymbol.getTags());
    item.setUri(sourceDefinedSymbol.getOwner().getUri().toString());
    item.setRange(sourceDefinedSymbol.getRange());
    item.setSelectionRange(sourceDefinedSymbol.getSelectionRange());

    return item;
  }
}
