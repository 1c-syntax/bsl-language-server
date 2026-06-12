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
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.hover.MarkupContentBuilder;
import com.github._1c_syntax.bsl.languageserver.lsif.LsifEmitter;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Поставщик LSIF-данных для hover.
 * <p>
 * Генерирует вершины range, resultSet, hoverResult и соответствующие рёбра
 * для символов документа. Использует {@link MarkupContentBuilder} для
 * формирования содержимого hover-подсказок.
 *
 * @see LsifDataSupplier
 * @see MarkupContentBuilder
 */
@Component
@RequiredArgsConstructor
public class HoverLsifSupplier implements LsifDataSupplier {

  private final Map<SymbolKind, MarkupContentBuilder<Symbol>> markupContentBuilders;

  /**
   * {@inheritDoc}
   * <p>
   * Обрабатывает все методы и модуль документа, генерируя для каждого
   * hover-информацию и связывающие LSIF-элементы.
   */
  @Override
  public void supply(DocumentContext documentContext, long documentId, LsifEmitter emitter) {
    var symbolTree = documentContext.getSymbolTree();
    List<Long> rangeIds = new ArrayList<>();

    // Process methods
    for (MethodSymbol method : symbolTree.getMethods()) {
      var rangeId = processSymbol(method, emitter);
      if (rangeId != null) {
        rangeIds.add(rangeId);
      }
    }

    // Process module symbol
    var moduleSymbol = symbolTree.getModule();
    var moduleRangeId = processSymbol(moduleSymbol, emitter);
    if (moduleRangeId != null) {
      rangeIds.add(moduleRangeId);
    }

    // Emit contains edge for document -> ranges
    if (!rangeIds.isEmpty()) {
      emitter.emitContains(documentId, rangeIds);
    }
  }

  private Long processSymbol(SourceDefinedSymbol symbol, LsifEmitter emitter) {
    var builder = markupContentBuilders.get(symbol.getSymbolKind());
    if (builder == null) {
      return null;
    }

    var content = builder.getContent(symbol);
    if (content == null) {
      return null;
    }

    // Emit range vertex
    var rangeId = emitter.emitRange(symbol.getSelectionRange());

    // Emit resultSet vertex
    var resultSetId = emitter.emitResultSet();

    // Link range to resultSet
    emitter.emitNext(rangeId, resultSetId);

    // Emit hoverResult vertex
    var hoverResultId = emitter.emitHoverResult(content.getValue());

    // Link resultSet to hoverResult
    emitter.emitHoverEdge(resultSetId, hoverResultId);

    return rangeId;
  }
}
