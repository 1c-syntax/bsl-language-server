/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Exportable;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.utils.MdoRefBuilder;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import lombok.RequiredArgsConstructor;
import lombok.Synchronized;
import org.apache.commons.collections4.MultiMapUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ReferenceIndex {

  private final ReferenceResolver referenceResolver;
  private final ServerContext serverContext;

  /**
   * Хранит информацию о том, какие ReferenceDTO (symbolName + mdoRef + moduleType + symbolKind)
   * были вызваны из списка Location (URI + Range).
   */
  private final Map<ReferenceDTO, List <Location>> referencesTo = new HashMap<>();

  /**
   * Хранит информацию о том, какие ReferenceDTO (symbolName + mdoRef + moduleType + symbolKind) в каких URI были
   *  вызваны и в каких Range, расположены вызовы
   */
  private final Map<URI, MultiValuedMap<ReferenceDTO, Range>> referencesFrom = new HashMap<>();

  /**
   * Хранит информацию о том, в каких Range каких URI были вызваны
   * какие ReferenceDTO (symbolName + mdoRef + moduleType + symbolKind)
   */
  private final Map<URI, Map<Range, ReferenceDTO>> referencesRanges = new HashMap<>();

  /**
   * Получить ссылки на символ.
   *
   * @param symbol Символ, для которого необходимо осуществить поиск ссылок.
   * @return Список ссылок на символ.
   */
  public List<Reference> getReferencesTo(SourceDefinedSymbol symbol) {
    var mdoRef = MdoRefBuilder.getMdoRef(symbol.getOwner());
    var moduleType = symbol.getOwner().getModuleType();
    var scopeName = symbol.getRootParent(SymbolKind.Method)
      .map(Symbol::getName)
      .orElse("");

    // TODO: Можно уйти от двойного ключа для referencesTo, но тогда в referencesFrom будет храниться только
    //       один вид ключа, что приведет к неправильному формированию Reference при выполнении getReferencesFrom
    List<ReferenceDTO> keys = new ArrayList<>();
    keys.add(ReferenceDTO.of(mdoRef, moduleType, scopeName, symbol.getSymbolKind(), symbol.getName(), false));

    if (symbol.getSymbolKind() == SymbolKind.Variable) {
      keys.add(ReferenceDTO.of(mdoRef, moduleType, scopeName, symbol.getSymbolKind(), symbol.getName(), true));
    }

    return keys.stream()
      .map(key -> referencesTo.getOrDefault(key, Collections.emptyList()))
      .flatMap(locations -> locations.stream()
        .map(location -> referenceResolver.findReference(URI.create(location.getUri()), location.getRange().getStart()))
      )
      .flatMap(Optional::stream)
      .collect(Collectors.toList());
  }

  /**
   * Поиск символа по позиции курсора.
   *
   * @param uri URI документа, в котором необходимо осуществить поиск.
   * @param position позиция курсора.
   * @return данные ссылки.
   */
  public Optional<Reference> getReference(URI uri, Position position) {
    return referencesRanges.getOrDefault(uri, Collections.emptyMap()).entrySet().stream()
      .filter(entry -> Ranges.containsPosition(entry.getKey(), position))
      .findAny()
      .flatMap(entry -> buildReference(uri, position, entry.getValue(), entry.getKey()));
  }

  /**
   * Поиск ссылок на символы в документе.
   *
   * @param uri URI документа, в котором нужно найти ссылки на другие символы.
   * @return Список ссылок на символы.
   */
  public List<Reference> getReferencesFrom(URI uri) {
    return referencesFrom.getOrDefault(uri, MultiMapUtils.emptyMultiValuedMap()).entries().stream()
      .map(entry -> buildReference(uri, entry.getValue().getStart(), entry.getKey(), entry.getValue()))
      .flatMap(Optional::stream)
      .collect(Collectors.toList());
  }

  /**
   * Поиск ссылок на символы в символе.
   *
   * @param symbol Символ, в котором нужно найти ссылки на другие символы.
   * @return Список ссылок на символы.
   */
  public List<Reference> getReferencesFrom(SourceDefinedSymbol symbol) {
    return getReferencesFrom(symbol.getOwner().getUri()).stream()
      .filter(reference -> reference.getFrom().equals(symbol))
      .collect(Collectors.toList());
  }

  /**
   * Очистить ссылки из/на текущий документ.
   *
   * @param uri URI документа.
   */
  @Synchronized
  public void clearReferences(URI uri) {
    String stringUri = uri.toString();

    referencesRanges.getOrDefault(uri, Collections.emptyMap()).values().forEach(
      referenceKey -> referencesTo
        .get(referenceKey)
        .removeIf(location -> location.getUri().equals(stringUri))
    );

    referencesFrom.remove(uri);
    referencesRanges.remove(uri);
  }

  @Synchronized
  public void addReference(URI uri, Range range, ReferenceDTO referenceKey) {
    Location location = new Location(uri.toString(), range);
    referencesTo.computeIfAbsent(referenceKey, k -> new ArrayList<>()).add(location);
    referencesFrom.computeIfAbsent(uri, k -> new ArrayListValuedHashMap<>()).put(referenceKey, range);
    referencesRanges.computeIfAbsent(uri, k -> new HashMap<>()).put(range, referenceKey);
  }

  private Optional<Reference> buildReference(URI uri, Position position, ReferenceDTO referenceKey, Range selectionRange) {
    return getSourceDefinedSymbol(referenceKey)
      .map((SourceDefinedSymbol symbol) -> {
        SourceDefinedSymbol from = getFromSymbol(uri, position);
        return Reference.builder()
          .from(from)
          .symbol(symbol)
          .uri(uri)
          .selectionRange(selectionRange)
          .isWrite(referenceKey.isWrite())
          .build();
      })
      .filter(ReferenceIndex::isReferenceAccessible);
  }

  private Optional<SourceDefinedSymbol> getSourceDefinedSymbol(ReferenceDTO referenceKey) {
    String mdoRef = referenceKey.getMdoRef();
    ModuleType moduleType = referenceKey.getModuleType();
    String symbolName = referenceKey.getSymbolName();

    if (referenceKey.getSymbolKind() == SymbolKind.Variable) {
      return serverContext.getDocument(mdoRef, moduleType)
        .map(DocumentContext::getSymbolTree)
        .flatMap(symbolTree -> symbolTree.getMethodSymbol(referenceKey.getScopeName())
          .flatMap(method -> symbolTree.getVariableSymbol(symbolName, method))
          .or(() -> symbolTree.getVariableSymbol(symbolName, symbolTree.getModule())));
    }

    return serverContext.getDocument(mdoRef, moduleType)
      .map(DocumentContext::getSymbolTree)
      .flatMap(symbolTree -> symbolTree.getMethodSymbol(symbolName));
  }

  private SourceDefinedSymbol getFromSymbol(URI uri, Position position) {
    Optional<SymbolTree> symbolTree = Optional.ofNullable(serverContext.getDocument(uri))
      .map(DocumentContext::getSymbolTree);
    return symbolTree
      .map(SymbolTree::getChildrenFlat)
      .stream()
      .flatMap(Collection::stream)
      .filter(sourceDefinedSymbol -> sourceDefinedSymbol.getSymbolKind() != SymbolKind.Namespace)
      .filter(symbol -> Ranges.containsPosition(symbol.getRange(), position))
      .findFirst()
      .or(() -> symbolTree.map(SymbolTree::getModule))
      .orElseThrow();
  }

  private static boolean isReferenceAccessible(Reference reference) {
    if (!reference.isSourceDefinedSymbolReference()) {
      return true;
    }

    SourceDefinedSymbol to = reference.getSourceDefinedSymbol().orElseThrow();
    SourceDefinedSymbol from = reference.getFrom();
    if (to.getOwner().equals(from.getOwner())) {
      return true;
    }

    if (to instanceof Exportable) {
      return ((Exportable) to).isExport();
    }

    return true;
  }

}
