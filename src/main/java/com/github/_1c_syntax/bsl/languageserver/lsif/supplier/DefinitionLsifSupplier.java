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
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.lsif.LsifEmitter;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Поставщик LSIF-данных для перехода к определению (Go to Definition).
 * <p>
 * Генерирует вершины range, resultSet, definitionResult и соответствующие рёбра.
 * Обрабатывает как определения символов, так и ссылки на них (вызовы методов,
 * использования переменных), чтобы "Go to Definition" работал с любого места
 * использования символа.
 * <p>
 * Использует {@link ReferenceIndex#getReferencesFrom} для получения всех ссылок
 * из документа на другие символы.
 *
 * @see LsifDataSupplier
 * @see ReferenceIndex
 */
@Component
@RequiredArgsConstructor
public class DefinitionLsifSupplier implements LsifDataSupplier {

  private final ReferenceIndex referenceIndex;

  /**
   * {@inheritDoc}
   * <p>
   * Обрабатывает:
   * <ul>
   *   <li>Определения методов и переменных документа</li>
   *   <li>Ссылки на символы из документа (вызовы методов, использования переменных)</li>
   * </ul>
   * Для каждого генерируются LSIF-элементы definitionResult.
   */
  @Override
  public void supply(DocumentContext documentContext, long documentId, LsifEmitter emitter) {
    var symbolTree = documentContext.getSymbolTree();
    List<Long> rangeIds = new ArrayList<>();

    // Cache for symbol -> (resultSetId, definitionResultId) to reuse for references
    Map<SourceDefinedSymbol, DefinitionData> symbolDefinitions = new HashMap<>();

    // Process method definitions
    for (MethodSymbol method : symbolTree.getMethods()) {
      var data = processDefinition(method, documentId, emitter);
      if (data != null) {
        rangeIds.add(data.rangeId);
        symbolDefinitions.put(method, data);
      }
    }

    // Process variable definitions
    for (VariableSymbol variable : symbolTree.getVariables()) {
      var data = processDefinition(variable, documentId, emitter);
      if (data != null) {
        rangeIds.add(data.rangeId);
        symbolDefinitions.put(variable, data);
      }
    }

    // Process references from this document (method calls, variable usages)
    var references = referenceIndex.getReferencesFrom(documentContext.getUri());
    for (Reference reference : references) {
      var refRangeId = processReference(reference, documentId, symbolDefinitions, emitter);
      if (refRangeId != null) {
        rangeIds.add(refRangeId);
      }
    }

    // Emit contains edge for document -> ranges
    if (!rangeIds.isEmpty()) {
      emitter.emitContains(documentId, rangeIds);
    }
  }

  /**
   * Обрабатывает определение символа (метод или переменная).
   * Создаёт range для определения и связывает его с definitionResult.
   *
   * @param symbol     символ для обработки
   * @param documentId идентификатор документа
   * @param emitter    эмиттер LSIF
   * @return данные определения для кэширования
   */
  private DefinitionData processDefinition(SourceDefinedSymbol symbol, long documentId, LsifEmitter emitter) {
    // Emit range vertex for the symbol definition
    var rangeId = emitter.emitRange(symbol.getSelectionRange());

    // Emit resultSet vertex
    var resultSetId = emitter.emitResultSet();

    // Link range to resultSet
    emitter.emitNext(rangeId, resultSetId);

    // Emit definitionResult vertex
    var definitionResultId = emitter.emitDefinitionResult();

    // Link resultSet to definitionResult
    emitter.emitDefinitionEdge(resultSetId, definitionResultId);

    // Emit item edge linking definitionResult to the range (the definition itself)
    emitter.emitItem(definitionResultId, List.of(rangeId), documentId, "definitions");

    return new DefinitionData(rangeId, resultSetId, definitionResultId);
  }

  /**
   * Обрабатывает ссылку на символ (вызов метода, использование переменной).
   * Создаёт range для ссылки и связывает его с definitionResult целевого символа.
   *
   * @param reference         ссылка для обработки
   * @param documentId        идентификатор документа
   * @param symbolDefinitions кэш определений символов
   * @param emitter           эмиттер LSIF
   * @return идентификатор range или null, если целевой символ не найден
   */
  private Long processReference(
    Reference reference,
    long documentId,
    Map<SourceDefinedSymbol, DefinitionData> symbolDefinitions,
    LsifEmitter emitter
  ) {
    // Get the target symbol this reference points to
    var targetSymbol = reference.getSourceDefinedSymbol();
    if (targetSymbol.isEmpty()) {
      return null;
    }

    // Emit range for the reference location
    var refRangeId = emitter.emitRange(reference.selectionRange());

    // Try to get cached definition data for the target symbol
    var definitionData = symbolDefinitions.get(targetSymbol.get());

    if (definitionData != null) {
      // Link reference range to the same resultSet as the definition
      emitter.emitNext(refRangeId, definitionData.resultSetId);
    } else {
      // Target symbol is in another document - create new resultSet and definitionResult
      var resultSetId = emitter.emitResultSet();
      emitter.emitNext(refRangeId, resultSetId);

      var definitionResultId = emitter.emitDefinitionResult();
      emitter.emitDefinitionEdge(resultSetId, definitionResultId);

      // The actual definition location
      var targetDoc = targetSymbol.get().getOwner();
      var targetRangeId = emitter.emitRange(targetSymbol.get().getSelectionRange());

      // Note: The target document ID would be needed here for cross-file navigation
      // For now, we emit the range but don't include it in contains
      emitter.emitItem(definitionResultId, List.of(targetRangeId), documentId, "definitions");
    }

    return refRangeId;
  }

  /**
   * Данные определения символа для кэширования.
   */
  private record DefinitionData(long rangeId, long resultSetId, long definitionResultId) {}
}
