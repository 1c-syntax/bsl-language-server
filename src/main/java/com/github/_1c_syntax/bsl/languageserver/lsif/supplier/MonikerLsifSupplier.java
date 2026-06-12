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
import com.github._1c_syntax.bsl.languageserver.context.symbol.Exportable;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.lsif.LsifEmitter;
import com.github._1c_syntax.bsl.languageserver.lsif.dto.LsifConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Поставщик LSIF-данных для моникеров.
 * <p>
 * Генерирует вершины moniker для экспортируемых символов,
 * что позволяет связывать символы между разными проектами
 * в Sourcegraph, GitHub Code Navigation и других системах.
 * <p>
 * Моникер формируется в формате: {@code mdoRef:moduleType:symbolName}
 *
 * @see LsifDataSupplier
 * @see LsifConstants.MonikerKind
 * @see LsifConstants.MonikerScheme
 */
@Component
@RequiredArgsConstructor
public class MonikerLsifSupplier implements LsifDataSupplier {

  /**
   * {@inheritDoc}
   * <p>
   * Обрабатывает экспортируемые методы документа, генерируя для каждого
   * моникер с уникальным идентификатором.
   */
  @Override
  public void supply(DocumentContext documentContext, long documentId, LsifEmitter emitter) {
    var symbolTree = documentContext.getSymbolTree();
    List<Long> rangeIds = new ArrayList<>();

    // Process exported methods
    for (MethodSymbol method : symbolTree.getMethods()) {
      if (isExported(method)) {
        var rangeId = processExportedSymbol(method, documentContext, emitter);
        if (rangeId != null) {
          rangeIds.add(rangeId);
        }
      }
    }

    // Emit contains edge for document -> ranges
    if (!rangeIds.isEmpty()) {
      emitter.emitContains(documentId, rangeIds);
    }
  }

  private boolean isExported(SourceDefinedSymbol symbol) {
    if (symbol instanceof Exportable exportable) {
      return exportable.isExport();
    }
    return false;
  }

  private Long processExportedSymbol(SourceDefinedSymbol symbol, DocumentContext documentContext, LsifEmitter emitter) {
    // Emit range vertex
    var rangeId = emitter.emitRange(symbol.getSelectionRange());

    // Emit resultSet vertex
    var resultSetId = emitter.emitResultSet();

    // Link range to resultSet
    emitter.emitNext(rangeId, resultSetId);

    // Create moniker identifier: mdoRef:moduleType:symbolName
    var identifier = buildMonikerIdentifier(symbol, documentContext);

    // Emit moniker vertex for exported symbol
    var monikerId = emitter.emitMoniker(
      LsifConstants.MonikerScheme.BSL,
      identifier,
      LsifConstants.MonikerKind.EXPORT,
      "scheme"
    );

    // Link resultSet to moniker
    emitter.emitMonikerEdge(resultSetId, monikerId);

    return rangeId;
  }

  private String buildMonikerIdentifier(SourceDefinedSymbol symbol, DocumentContext documentContext) {
    var mdoRef = documentContext.getMdoRef();
    var moduleType = documentContext.getModuleType().name().toLowerCase(Locale.ENGLISH);
    var symbolName = symbol.getName().toLowerCase(Locale.ENGLISH);

    return String.format("%s:%s:%s", mdoRef, moduleType, symbolName);
  }
}
