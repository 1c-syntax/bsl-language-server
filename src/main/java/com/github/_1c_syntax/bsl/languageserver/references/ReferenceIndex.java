/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Exportable;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.references.model.Location;
import com.github._1c_syntax.bsl.languageserver.references.model.LocationRepository;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.references.model.Symbol;
import com.github._1c_syntax.bsl.languageserver.references.model.SymbolOccurrence;
import com.github._1c_syntax.bsl.languageserver.references.model.SymbolOccurrenceRepository;
import com.github._1c_syntax.bsl.languageserver.utils.MdoRefBuilder;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.utils.StringInterner;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ReferenceIndex {

  private final ServerContext serverContext;
  private final StringInterner stringInterner;

  private final LocationRepository locationRepository;
  private final SymbolOccurrenceRepository symbolOccurrenceRepository;

  /**
   * Получить ссылки на символ.
   *
   * @param symbol Символ, для которого необходимо осуществить поиск ссылок.
   * @return Список ссылок на символ.
   */
  public List<Reference> getReferencesTo(SourceDefinedSymbol symbol) {
    var mdoRef = MdoRefBuilder.getMdoRef(symbol.getOwner());
    var moduleType = symbol.getOwner().getModuleType();
    var symbolName = symbol.getName().toLowerCase(Locale.ENGLISH);
    String scopeName = "";

    if (symbol.getSymbolKind() == SymbolKind.Variable) {
      scopeName = symbol.getRootParent(SymbolKind.Method)
        .map(SourceDefinedSymbol::getName)
        .map(name -> name.toLowerCase(Locale.ENGLISH))
        .orElse("");
    }

    var symbolDto = Symbol.builder()
      .mdoRef(mdoRef)
      .moduleType(moduleType)
      .scopeName(scopeName)
      .symbolKind(symbol.getSymbolKind())
      .symbolName(symbolName)
      .build();

    return symbolOccurrenceRepository.getAllBySymbol(symbolDto)
      .stream()
      .map(this::buildReference)
      .flatMap(Optional::stream)
      .collect(Collectors.toList());
  }

  /**
   * Поиск символа по позиции курсора.
   *
   * @param uri      URI документа, в котором необходимо осуществить поиск.
   * @param position позиция курсора.
   * @return данные ссылки.
   */
  public Optional<Reference> getReference(URI uri, Position position) {
    return locationRepository.getSymbolOccurrencesByLocationUri(uri)
      .filter(symbolOccurrence -> Ranges.containsPosition(symbolOccurrence.getLocation().getRange(), position))
      .findAny()
      .flatMap(this::buildReference);
  }

  /**
   * Поиск ссылок на символы в документе.
   *
   * @param uri URI документа, в котором нужно найти ссылки на другие символы.
   * @return Список ссылок на символы.
   */
  public List<Reference> getReferencesFrom(URI uri) {

    return locationRepository.getSymbolOccurrencesByLocationUri(uri)
      .map(this::buildReference)
      .flatMap(Optional::stream)
      .collect(Collectors.toList());
  }

  /**
   * Поиск ссылок на символы в документе.
   *
   * @param uri URI документа, в котором нужно найти ссылки на другие символы.
   * @return Список ссылок на символы.
   */
  public List<Reference> getReferencesFrom(URI uri, SymbolKind kind) {

    return locationRepository.getSymbolOccurrencesByLocationUri(uri)
      .filter(s -> s.getSymbol().getSymbolKind() == kind)
      .map(this::buildReference)
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
  public void clearReferences(URI uri) {
    var symbolOccurrences = locationRepository.getSymbolOccurrencesByLocationUri(uri);
    symbolOccurrenceRepository.deleteAll(symbolOccurrences.collect(Collectors.toSet()));
    locationRepository.delete(uri);
  }

  /**
   * Добавить вызов метода в индекс.
   *
   * @param uri        URI документа, откуда произошел вызов.
   * @param mdoRef     Ссылка на объект-метаданных, к которому происходит обращение (например, CommonModule.ОбщийМодуль1).
   * @param moduleType Тип модуля, к которому происходит обращение (например, {@link ModuleType#CommonModule}).
   * @param symbolName Имя символа, к которому происходит обращение.
   * @param range      Диапазон, в котором происходит обращение к символу.
   */
  public void addMethodCall(URI uri, String mdoRef, ModuleType moduleType, String symbolName, Range range) {
    String symbolNameCanonical = stringInterner.intern(symbolName.toLowerCase(Locale.ENGLISH));

    var symbol = Symbol.builder()
      .mdoRef(mdoRef)
      .moduleType(moduleType)
      .scopeName("")
      .symbolKind(SymbolKind.Method)
      .symbolName(symbolNameCanonical)
      .build()
      .intern();

    var location = new Location(uri, range);
    var symbolOccurrence = SymbolOccurrence.builder()
      .occurrenceType(OccurrenceType.REFERENCE)
      .symbol(symbol)
      .location(location)
      .build();

    symbolOccurrenceRepository.save(symbolOccurrence);
    locationRepository.updateLocation(symbolOccurrence);
  }

  /**
   * Добавить обращение к переменной в индекс.
   *
   * @param uri          URI документа, откуда произошел вызов.
   * @param mdoRef       Ссылка на объект-метаданных, к которому происходит обращение (например, CommonModule.ОбщийМодуль1).
   * @param moduleType   Тип модуля, к которому происходит обращение (например, {@link ModuleType#CommonModule}).
   * @param methodName   Имя метода, к которому относиться перменная. Пустой если переменная относиться к модулю.
   * @param variableName Имя переменной, к которой происходит обращение.
   * @param range        Диапазон, в котором происходит обращение к символу.
   * @param definition     Признак обновления значения переменной.
   */
  public void addVariableUsage(URI uri,
                               String mdoRef,
                               ModuleType moduleType,
                               String methodName,
                               String variableName,
                               Range range,
                               boolean definition) {
    String methodNameCanonical = stringInterner.intern(methodName.toLowerCase(Locale.ENGLISH));
    String variableNameCanonical = stringInterner.intern(variableName.toLowerCase(Locale.ENGLISH));

    var symbol = Symbol.builder()
      .mdoRef(mdoRef)
      .moduleType(moduleType)
      .scopeName(methodNameCanonical)
      .symbolKind(SymbolKind.Variable)
      .symbolName(variableNameCanonical)
      .build()
      .intern();

    var location = new Location(uri, range);

    var symbolOccurrence = SymbolOccurrence.builder()
      .occurrenceType(definition ? OccurrenceType.DEFINITION : OccurrenceType.REFERENCE)
      .symbol(symbol)
      .location(location)
      .build();

    symbolOccurrenceRepository.save(symbolOccurrence);
    locationRepository.updateLocation(symbolOccurrence);
  }

  private Optional<Reference> buildReference(
    SymbolOccurrence symbolOccurrence
  ) {

    var uri = symbolOccurrence.getLocation().getUri();
    var range = symbolOccurrence.getLocation().getRange();
    var occurrenceType = symbolOccurrence.getOccurrenceType();

    return getSourceDefinedSymbol(symbolOccurrence.getSymbol())
      .map((SourceDefinedSymbol symbol) -> {
        SourceDefinedSymbol from = getFromSymbol(symbolOccurrence);
        return new Reference(from, symbol, uri, range, occurrenceType);
      })
      .filter(ReferenceIndex::isReferenceAccessible);
  }

  private Optional<SourceDefinedSymbol> getSourceDefinedSymbol(Symbol symbolEntity) {
    String mdoRef = symbolEntity.getMdoRef();
    ModuleType moduleType = symbolEntity.getModuleType();
    String symbolName = symbolEntity.getSymbolName();

    if (symbolEntity.getSymbolKind() == SymbolKind.Variable) {
      return serverContext.getDocument(mdoRef, moduleType)
        .map(DocumentContext::getSymbolTree)
        .flatMap(symbolTree -> symbolTree.getMethodSymbol(symbolEntity.getScopeName())
        .flatMap(method -> symbolTree.getVariableSymbol(symbolName, method))
        .or(() -> symbolTree.getVariableSymbol(symbolName, symbolTree.getModule())));
    }

    return serverContext.getDocument(mdoRef, moduleType)
      .map(DocumentContext::getSymbolTree)
      .flatMap(symbolTree -> symbolTree.getMethodSymbol(symbolName));
  }

  private SourceDefinedSymbol getFromSymbol(SymbolOccurrence symbolOccurrence) {

    var uri = symbolOccurrence.getLocation().getUri();
    var position = symbolOccurrence.getLocation().getRange().getStart();

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
