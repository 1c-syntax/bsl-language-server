/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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
package org.github._1c_syntax.bsl.languageserver.providers;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import org.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import org.github._1c_syntax.bsl.parser.BSLParser;
import org.github._1c_syntax.bsl.parser.BSLParserRuleContext;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class DocumentSymbolProvider {

  private DocumentSymbolProvider() {
    // only statics
  }

  public static List<Either<SymbolInformation, DocumentSymbol>> getDocumentSymbol(DocumentContext documentContext) {

    List<DocumentSymbol> symbols = new ArrayList<>();
    documentContext.getMethods().forEach((MethodSymbol methodSymbol) -> {
      BSLParserRuleContext context = methodSymbol.getNode();
      Range range = RangeHelper.newRange(context);

      BSLParser.SubNameContext subNameContext;
      if (methodSymbol.isFunction()) {
        subNameContext = ((BSLParser.FunctionContext) context).funcDeclaration().subName();
      } else {
        subNameContext = ((BSLParser.ProcedureContext) context).procDeclaration().subName();
      }
      Range selectionRange = RangeHelper.newRange(subNameContext);

      DocumentSymbol documentSymbol = new DocumentSymbol(
        methodSymbol.getName(),
        SymbolKind.Method,
        range,
        selectionRange
      );
      
      symbols.add(documentSymbol);
    });

    return symbols.stream()
      .map(Either::<SymbolInformation, DocumentSymbol>forRight)
      .collect(Collectors.toList());
  }
}
