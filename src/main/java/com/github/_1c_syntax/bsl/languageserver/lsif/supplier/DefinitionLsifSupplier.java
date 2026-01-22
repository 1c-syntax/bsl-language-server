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
import com.github._1c_syntax.bsl.languageserver.lsif.LsifEmitter;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Поставщик LSIF-данных для перехода к определению (Go to Definition).
 * <p>
 * Генерирует вершины range, resultSet, definitionResult и соответствующие рёбра.
 * Для каждой ссылки из документа создаётся range и связывается с definitionResult,
 * указывающим на целевой символ (definition).
 * <p>
 * Использует {@link ReferenceIndex#getReferencesFrom} для получения всех ссылок
 * из документа. Поле {@link Reference#symbol()} уже содержит целевой символ (definition).
 *
 * @see LsifDataSupplier
 * @see ReferenceIndex
 * @see Reference
 */
@Component
@RequiredArgsConstructor
public class DefinitionLsifSupplier implements LsifDataSupplier {

  private final ReferenceIndex referenceIndex;

  /**
   * {@inheritDoc}
   * <p>
   * Для каждой ссылки из {@link ReferenceIndex#getReferencesFrom}:
   * <ul>
   *   <li>Создаётся range для местоположения ссылки ({@link Reference#selectionRange()})</li>
   *   <li>Создаётся resultSet и definitionResult</li>
   *   <li>definitionResult указывает на местоположение целевого символа ({@link Reference#symbol()})</li>
   * </ul>
   */
  @Override
  public void supply(DocumentContext documentContext, long documentId, LsifEmitter emitter) {
    List<Long> rangeIds = new ArrayList<>();

    // Get all references from this document
    var references = referenceIndex.getReferencesFrom(documentContext.getUri());

    for (Reference reference : references) {
      // Reference.symbol() is the target symbol (definition)
      var targetSymbol = reference.getSourceDefinedSymbol();
      if (targetSymbol.isEmpty()) {
        continue;
      }

      var target = targetSymbol.get();

      // Emit range for the reference location in this document
      var rangeId = emitter.emitRange(reference.selectionRange());
      rangeIds.add(rangeId);

      // Check if we already have a resultSet for this target symbol (global cache)
      var existingResultSetId = emitter.getCachedResultSet(target);

      if (existingResultSetId.isPresent()) {
        // Reuse existing resultSet
        emitter.emitNext(rangeId, existingResultSetId.get());
      } else {
        // Create new resultSet and definitionResult
        var resultSetId = emitter.emitResultSet();
        emitter.emitNext(rangeId, resultSetId);

        var definitionResultId = emitter.emitDefinitionResult();
        emitter.emitDefinitionEdge(resultSetId, definitionResultId);

        // The definition location comes from the target symbol
        var targetRangeId = emitter.emitRange(target.getSelectionRange());

        // Get the document ID for the target symbol's document
        // Use cached document ID or fall back to current document
        var targetDocumentUri = target.getOwner().getUri().toString();
        var targetDocumentId = emitter.getCachedDocumentId(targetDocumentUri).orElse(documentId);

        emitter.emitItem(definitionResultId, List.of(targetRangeId), targetDocumentId, "definitions");

        // Cache the resultSet for reuse across documents
        emitter.cacheResultSet(target, resultSetId);
      }
    }

    // Emit contains edge for document -> ranges
    if (!rangeIds.isEmpty()) {
      emitter.emitContains(documentId, rangeIds);
    }
  }
}
