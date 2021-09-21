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
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Position;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Optional;

/**
 * Реализация поискового движка на основе попадания искомой позиции в строку объявления метода.
 */
@Component
@RequiredArgsConstructor
public class SourceDefinedSymbolDeclarationReferenceFinder implements ReferenceFinder {

  private final ServerContext serverContext;

  @Override
  public Optional<Reference> findReference(URI uri, Position position) {
    DocumentContext document = serverContext.getDocument(uri);
    if (document == null) {
      return Optional.empty();
    }

    SymbolTree symbolTree = document.getSymbolTree();
    return symbolTree.getChildrenFlat()
      .stream()
      .filter(sourceDefinedSymbol -> Ranges.containsPosition(sourceDefinedSymbol.getSelectionRange(), position))
      .map(sourceDefinedSymbol -> new Reference(
        symbolTree.getModule(),
        sourceDefinedSymbol,
        uri,
        sourceDefinedSymbol.getSelectionRange(),
        OccurrenceType.DEFINITION)
      )
      .findFirst();
  }
}
