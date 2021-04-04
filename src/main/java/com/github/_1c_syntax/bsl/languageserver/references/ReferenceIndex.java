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
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.utils.MdoRefBuilder;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import lombok.RequiredArgsConstructor;
import lombok.Synchronized;
import org.apache.commons.collections4.MultiMapUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ReferenceIndex {

  private final ReferenceResolver referenceResolver;
  private final ServerContext serverContext;

  /**
   * Хранит информацию о том, какие symbolName (ключ MultiValuedMap) каких mdoRef каких moduleType (ключ MultiKey)
   * были вызваны из списка Location (URI + Range).
   */
  private final Map<MultiKey<String>, MultiValuedMap<String, Location>> referencesTo = new HashMap<>();

  /**
   * Хранит информацию о том, какие symbolName с mdoRef с moduleType (ключ MultiKey) в каких URI были вызваны
   * и в каких Range, расположены вызовы
   */
  private final Map<URI, MultiValuedMap<MultiKey<String>, Range>> referencesFrom = new HashMap<>();

  /**
   * Хранит информацию о том, в каких Range каких URI были вызваны
   * какие symbolName каких mdoRef с каким moduleType (ключ MultiKey)
   */
  private final Map<URI, Map<Range, MultiKey<String>>> referencesRanges = new HashMap<>();

  /**
   * Хранит информацию о том, в каких Range каких URI были использованы значения
   * каких symbolName каких mdoRef с каким moduleType (ключ MultiKey)
   */
  private final Map<URI, Map<Range, MultiKey<String>>> variableWriteRanges = new HashMap<>();

  /**
   * Получить ссылки на символ.
   *
   * @param symbol Символ, для которого необходимо осуществить поиск ссылок.
   * @return Список ссылок на символ.
   */
  public List<Reference> getReferencesTo(SourceDefinedSymbol symbol) {
    var mdoRef = MdoRefBuilder.getMdoRef(symbol.getOwner());
    var moduleType = symbol.getOwner().getModuleType();
    var key = getKey(mdoRef, moduleType);

    // FIXME: Не нравится проверка на тип
    if (symbol.getSymbolKind() == SymbolKind.Variable) {
      key = symbol.getRootParent(SymbolKind.Method)
        .map(sourceDefinedSymbol -> getVariableKey(mdoRef, moduleType, sourceDefinedSymbol.getName().toLowerCase(Locale.ENGLISH)))
        .orElseGet(() -> getVariableKey(mdoRef, moduleType, ""));
    }

    var symbolName = symbol.getName().toLowerCase(Locale.ENGLISH);

    return referencesTo.getOrDefault(key, MultiMapUtils.emptyMultiValuedMap()).get(symbolName)
      .stream()
      .map(location -> referenceResolver.findReference(URI.create(location.getUri()), location.getRange().getStart()))
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

    referencesRanges.getOrDefault(uri, Collections.emptyMap()).values().forEach((MultiKey<String> multikey) -> {
      Collection<Location> locations = referencesTo.get(convertRangesKeyToKey(multikey)).values();
      locations.removeIf(location -> location.getUri().equals(stringUri));
    });

    variableWriteRanges.getOrDefault(uri, Collections.emptyMap()).values().forEach((MultiKey<String> multikey) -> {
      Collection<Location> locations = referencesTo.get(convertRangesKeyToKey(multikey)).values();
      locations.removeIf(location -> location.getUri().equals(stringUri));
    });

    referencesFrom.remove(uri);
    referencesRanges.remove(uri);
  }

  /**
   * Добавить вызов метода в индекс.
   *
   * @param uri URI документа, откуда произошел вызов.
   * @param mdoRef Ссылка на объект-метаданных, к которому происходит обращение (например, CommonModule.ОбщийМодуль1).
   * @param moduleType Тип модуля, к которому происходит обращение (например, {@link ModuleType#CommonModule}).
   * @param symbolName Имя символа, к которому происходит обращение.
   * @param range Диапазон, в котором происходит обращение к символу.
   */
  @Synchronized
  public void addMethodCall(URI uri, String mdoRef, ModuleType moduleType, String symbolName, Range range) {
    String symbolNameCanonical = symbolName.toLowerCase(Locale.ENGLISH);

    Location location = new Location(uri.toString(), range);

    MultiKey<String> key = getKey(mdoRef, moduleType);
    MultiKey<String> rangesKey = getRangesKey(mdoRef, moduleType, symbolNameCanonical);
    addReferenceToStorage(uri, range, symbolNameCanonical, location, key, rangesKey);
  }

  @Synchronized
  public void addVariableUsage(URI uri,
                               String mdoRef,
                               ModuleType moduleType,
                               String scopeName,
                               String symbolName,
                               Range range,
                               boolean isWrite)
  {
    String scopeNameCanonical = scopeName.toLowerCase(Locale.ENGLISH);
    String symbolNameCanonical = symbolName.toLowerCase(Locale.ENGLISH);

    Location location = new Location(uri.toString(), range);

    MultiKey<String> key = getVariableKey(mdoRef, moduleType, scopeNameCanonical);
    MultiKey<String> rangesKey = getVariableRangesKey(mdoRef, moduleType, scopeNameCanonical, symbolNameCanonical);
    addReferenceToStorage(uri, range, symbolNameCanonical, location, key, rangesKey);

    if (isWrite) {
      variableWriteRanges.computeIfAbsent(uri, k -> new HashMap<>()).put(range, rangesKey);
    }

  }

  private Optional<Reference> buildReference(
    URI uri,
    Position position,
    MultiKey<String> multikey,
    Range selectionRange
  ) {
    return getSourceDefinedSymbol(multikey)
      .map((SourceDefinedSymbol symbol) -> {
        SourceDefinedSymbol from = getFromSymbol(uri, position);
        return Reference.builder()
          .from(from)
          .symbol(symbol)
          .uri(uri)
          .selectionRange(selectionRange)
          .isWrite(isReferenceWrite(uri, position))
          .build();
      })
      .filter(ReferenceIndex::isReferenceAccessible);
  }

  private Optional<SourceDefinedSymbol> getSourceDefinedSymbol(MultiKey<String> multikey) {
    String mdoRef = multikey.getKey(0);
    ModuleType moduleType = getModuleType(multikey.getKey(1));
    String symbolName = multikey.getKey(multikey.size() - 1);

    if (multikey.size() == 4) { // FIXME: Не нравится проверка на волшебное число, хотелось бы заменить на "интерфейс"
      return serverContext.getDocument(mdoRef, moduleType)
        .map(DocumentContext::getSymbolTree)
        .flatMap(symbolTree -> {
          var method = symbolTree.getMethodSymbol(multikey.getKey(2));

          if (method.isEmpty()) {
            return symbolTree.getVariableSymbol(symbolName, symbolTree.getModule());
          }

          return symbolTree.getVariableSymbol(symbolName, method.get());
        });
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

  private boolean isReferenceWrite(URI uri, Position position) {
    return variableWriteRanges.getOrDefault(uri, Collections.emptyMap())
      .entrySet()
      .stream()
      .anyMatch(entry -> Ranges.containsPosition(entry.getKey(), position));
  }

  private void addReferenceToStorage(
    URI uri,
    Range range,
    String symbolNameCanonical,
    Location location,
    MultiKey<String> key,
    MultiKey<String> rangesKey
  ) {
    referencesTo.computeIfAbsent(key, k -> new ArrayListValuedHashMap<>()).put(symbolNameCanonical, location);
    referencesFrom.computeIfAbsent(uri, k -> new ArrayListValuedHashMap<>()).put(rangesKey, range);
    referencesRanges.computeIfAbsent(uri, k -> new HashMap<>()).put(range, rangesKey);
  }

  private static MultiKey<String> getKey(String mdoRef, ModuleType moduleType) {
    return new MultiKey<>(mdoRef, moduleType.toString());
  }

  private static MultiKey<String> getRangesKey(String mdoRef, ModuleType moduleType, String symbolName) {
    return new MultiKey<>(mdoRef, moduleType.getFileName(), symbolName);
  }

  private static MultiKey<String> getVariableKey(String mdoRef, ModuleType moduleType, String scopeName) {
    return new MultiKey<>(mdoRef, moduleType.getFileName(), scopeName);
  }

  private static MultiKey<String> getVariableRangesKey(
    String mdoRef,
    ModuleType moduleType,
    String scopeName,
    String symbolName
  ) {
    return new MultiKey<>(mdoRef, moduleType.getFileName(), scopeName, symbolName);
  }

  private static MultiKey<String> convertRangesKeyToKey(MultiKey<String> rangesKey) {
    if (rangesKey.size() == 3) {
      return new MultiKey<>(rangesKey.getKey(0), rangesKey.getKey(1));
    }

    return new MultiKey<>(rangesKey.getKey(0), rangesKey.getKey(1), rangesKey.getKey(2));
  }

  private static ModuleType getModuleType(String filename) {
    return Arrays.stream(ModuleType.values())
      .filter(type -> type.getFileName().equals(filename))
      .findFirst()
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
