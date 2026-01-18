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
package com.github._1c_syntax.bsl.languageserver.lsif.supplier;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.lsif.LsifEmitter;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Поставщик LSIF-данных для поиска ссылок.
 * <p>
 * Генерирует вершины range, resultSet, referenceResult и соответствующие рёбра
 * для символов документа и их ссылок. Использует {@link ReferenceIndex} для
 * получения информации о ссылках на символы.
 *
 * @see LsifDataSupplier
 * @see ReferenceIndex
 */
@Component
@RequiredArgsConstructor
public class ReferenceLsifSupplier implements LsifDataSupplier {

  private final ReferenceIndex referenceIndex;

  /**
   * {@inheritDoc}
   * <p>
   * Обрабатывает все методы документа и их ссылки, генерируя referenceResult
   * с разделением на definitions и references через ребро item.
   */
  @Override
  public void supply(DocumentContext documentContext, long documentId, LsifEmitter emitter) {
    var symbolTree = documentContext.getSymbolTree();
    List<Long> rangeIds = new ArrayList<>();

    // Process methods and their references
    for (MethodSymbol method : symbolTree.getMethods()) {
      var ids = processSymbolWithReferences(method, documentId, emitter);
      rangeIds.addAll(ids);
    }

    // Emit contains edge for document -> ranges
    if (!rangeIds.isEmpty()) {
      emitter.emitContains(documentId, rangeIds);
    }
  }

  private List<Long> processSymbolWithReferences(SourceDefinedSymbol symbol, long documentId, LsifEmitter emitter) {
    List<Long> rangeIds = new ArrayList<>();

    // Emit range for the symbol definition
    var definitionRangeId = emitter.emitRange(symbol.getSelectionRange());
    rangeIds.add(definitionRangeId);

    // Emit resultSet for references
    var resultSetId = emitter.emitResultSet();

    // Link definition range to resultSet
    emitter.emitNext(definitionRangeId, resultSetId);

    // Get references to this symbol
    var references = referenceIndex.getReferencesTo(symbol);

    if (!references.isEmpty()) {
      // Emit referenceResult
      var referenceResultId = emitter.emitReferenceResult();

      // Link resultSet to referenceResult
      emitter.emitReferencesEdge(resultSetId, referenceResultId);

      // Collect reference range IDs for this document
      List<Long> referenceRangeIds = new ArrayList<>();
      var ownerUri = symbol.getOwner().getUri();
      for (Reference reference : references) {
        // Only process references in the same document for now
        // Using normalize() to handle URI equivalence
        if (reference.uri().normalize().equals(ownerUri.normalize())) {
          var refRangeId = emitter.emitRange(reference.selectionRange());
          referenceRangeIds.add(refRangeId);
          rangeIds.add(refRangeId);

          // Link reference range to the same resultSet
          emitter.emitNext(refRangeId, resultSetId);
        }
      }

      // Emit item edge for references
      if (!referenceRangeIds.isEmpty()) {
        emitter.emitItem(referenceResultId, referenceRangeIds, documentId, "references");
      }

      // Emit item edge for definition
      emitter.emitItem(referenceResultId, List.of(definitionRangeId), documentId, "definitions");
    }

    return rangeIds;
  }
}
