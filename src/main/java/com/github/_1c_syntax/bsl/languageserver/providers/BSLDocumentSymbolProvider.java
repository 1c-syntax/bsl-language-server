/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.BSLDocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.ls_core.context.DocumentContext;
import com.github._1c_syntax.ls_core.providers.DocumentSymbolProvider;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Primary
public final class BSLDocumentSymbolProvider implements DocumentSymbolProvider {

  @Override
  public List<Either<SymbolInformation, DocumentSymbol>> getDocumentSymbols(DocumentContext documentContext) {
    return ((BSLDocumentContext) documentContext).getSymbolTree().getChildren().stream()
      .map(BSLDocumentSymbolProvider::toDocumentSymbol)
      .map(Either::<SymbolInformation, DocumentSymbol>forRight)
      .collect(Collectors.toList());
  }

  private static DocumentSymbol toDocumentSymbol(Symbol symbol) {
    var documentSymbol = new DocumentSymbol(
      symbol.getName(),
      symbol.getSymbolKind(),
      symbol.getRange(),
      getSelectionRange(symbol)
    );

    List<DocumentSymbol> children = symbol.getChildren().stream()
      .map(BSLDocumentSymbolProvider::toDocumentSymbol)
      .collect(Collectors.toList());

    documentSymbol.setDeprecated(symbol.isDeprecated());
    documentSymbol.setChildren(children);

    return documentSymbol;
  }

  private static Range getSelectionRange(Symbol symbol) {
    Range selectionRange;
    if (symbol instanceof MethodSymbol) {
      selectionRange = ((MethodSymbol) symbol).getSubNameRange();
    } else if (symbol instanceof RegionSymbol) {
      selectionRange = ((RegionSymbol) symbol).getRegionNameRange();
    } else if (symbol instanceof VariableSymbol) {
      selectionRange = ((VariableSymbol) symbol).getVariableNameRange();
    } else {
      selectionRange = symbol.getRange();
    }
    return selectionRange;
  }

}
