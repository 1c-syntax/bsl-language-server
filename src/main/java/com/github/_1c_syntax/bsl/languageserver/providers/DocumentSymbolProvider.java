/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public final class DocumentSymbolProvider {

  /**
   * Идентификатор источника символов документа.
   */
  public static final String LABEL = "BSL Language Server";

  private static final Set<VariableKind> supportedVariableKinds = EnumSet.of(
    VariableKind.MODULE,
    VariableKind.LOCAL,
    VariableKind.GLOBAL
  );

  public List<DocumentSymbol> getDocumentSymbols(DocumentContext documentContext) {
    return documentContext.getSymbolTree().getChildren().stream()
      .filter(DocumentSymbolProvider::isSupported)
      .map(DocumentSymbolProvider::toDocumentSymbol)
      .collect(Collectors.toList());
  }

  private static DocumentSymbol toDocumentSymbol(SourceDefinedSymbol symbol) {
    var documentSymbol = new DocumentSymbol(
      symbol.getName(),
      symbol.getSymbolKind(),
      symbol.getRange(),
      symbol.getSelectionRange()
    );

    List<DocumentSymbol> children = symbol.getChildren().stream()
      .filter(DocumentSymbolProvider::isSupported)
      .map(DocumentSymbolProvider::toDocumentSymbol)
      .collect(Collectors.toList());

    documentSymbol.setTags(symbol.getTags());
    documentSymbol.setChildren(children);

    return documentSymbol;
  }

  public static boolean isSupported(Symbol symbol) {
    var symbolKind = symbol.getSymbolKind();
    if (symbolKind == SymbolKind.Variable) {
      return supportedVariableKinds.contains(((VariableSymbol) symbol).getKind());
    }
    return true;
  }
}
