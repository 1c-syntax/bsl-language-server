/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
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
import com.github._1c_syntax.bsl.languageserver.references.model.Location;
import com.github._1c_syntax.bsl.languageserver.references.model.Symbol;
import com.github._1c_syntax.bsl.languageserver.references.model.SymbolOccurrence;
import com.github._1c_syntax.bsl.languageserver.references.model.SymbolOccurrenceRepository;
import com.github._1c_syntax.bsl.languageserver.references.model.SymbolRepository;
import com.github._1c_syntax.bsl.languageserver.utils.MdoRefBuilder;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.mdclasses.mdo.support.ModuleType;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

  private final SymbolRepository symbolRepository;
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

    return symbolRepository.findByMdoRefAndModuleTypeAndSymbolName(mdoRef, moduleType, symbolName)
      .map(Symbol::getOccurrences)
      .stream()
      .flatMap(Collection::stream)
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
    return symbolOccurrenceRepository.getAllByLocationUri(uri)
      .filter(symbolOccurrence -> Ranges.containsPosition(symbolOccurrence.getLocation().getRange(), position))
      .get()
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

    return symbolOccurrenceRepository.getAllByLocationUri(uri).stream()
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
  @Transactional
  public void clearReferences(URI uri) {
    symbolOccurrenceRepository.deleteAllByLocationUri(uri);
    // todo: clear removed symbols from symbolRepository?
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
    String symbolNameCanonical = symbolName.toLowerCase(Locale.ENGLISH);

    // todo: race condition?
    var symbol = symbolRepository.findByMdoRefAndModuleTypeAndSymbolName(mdoRef, moduleType, symbolNameCanonical)
      .orElseGet(() -> {
        var newSymbol = new Symbol();

        newSymbol.setMdoRef(mdoRef);
        newSymbol.setModuleType(moduleType);
        newSymbol.setSymbolName(symbolNameCanonical);

        return symbolRepository.save(newSymbol);
      });

    var location = new Location();
    location.setUri(uri);
    location.setRange(range);

    var symbolOccurrence = new SymbolOccurrence();
    symbolOccurrence.setSymbol(symbol);
    symbolOccurrence.setLocation(location);

    symbolOccurrenceRepository.save(symbolOccurrence);
  }

  private Optional<Reference> buildReference(
    SymbolOccurrence symbolOccurrence
  ) {

    var uri = symbolOccurrence.getLocation().getUri();
    var range = symbolOccurrence.getLocation().getRange();

    return getSourceDefinedSymbol(symbolOccurrence.getSymbol())
      .map((SourceDefinedSymbol symbol) -> {
        SourceDefinedSymbol from = getFromSymbol(symbolOccurrence);
        return new Reference(from, symbol, uri, range);
      })
      .filter(ReferenceIndex::isReferenceAccessible);
  }

  private Optional<SourceDefinedSymbol> getSourceDefinedSymbol(Symbol symbolEntity) {
    String mdoRef = symbolEntity.getMdoRef();
    ModuleType moduleType = symbolEntity.getModuleType();
    String symbolName = symbolEntity.getSymbolName();

    return serverContext.getDocument(mdoRef, moduleType)
      .map(DocumentContext::getSymbolTree)
      // TODO: SymbolTree#getSymbol(Position)?
      //  Для поиска не только методов, но и переменных, которые могут иметь одинаковые имена
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
