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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Поставщик LSIF-данных для перехода к определению.
 * <p>
 * Генерирует вершины range, resultSet, definitionResult и соответствующие рёбра
 * для определений символов документа.
 */
@Component
@RequiredArgsConstructor
public class DefinitionLsifSupplier implements LsifDataSupplier {

  @Override
  public void supply(DocumentContext documentContext, long documentId, LsifEmitter emitter) {
    var symbolTree = documentContext.getSymbolTree();
    List<Long> rangeIds = new ArrayList<>();

    // Process methods
    for (MethodSymbol method : symbolTree.getMethods()) {
      var rangeId = processSymbol(method, documentId, emitter);
      if (rangeId != null) {
        rangeIds.add(rangeId);
      }
    }

    // Process variables
    for (VariableSymbol variable : symbolTree.getVariables()) {
      var rangeId = processSymbol(variable, documentId, emitter);
      if (rangeId != null) {
        rangeIds.add(rangeId);
      }
    }

    // Emit contains edge for document -> ranges
    if (!rangeIds.isEmpty()) {
      emitter.emitContains(documentId, rangeIds);
    }
  }

  private Long processSymbol(SourceDefinedSymbol symbol, long documentId, LsifEmitter emitter) {
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

    return rangeId;
  }
}
