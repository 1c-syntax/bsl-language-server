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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentSymbolProviderTest {

  @Test
  void testDocumentSymbol() {

    DocumentContext documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/providers/documentSymbol.bsl");

    List<Either<SymbolInformation, DocumentSymbol>> documentSymbols = DocumentSymbolProvider.getDocumentSymbol(documentContext);

    assertThat(documentSymbols).hasSize(6);

    // global variables
    assertThat(documentSymbols)
      .filteredOn(documentSymbol -> documentSymbol.getRight().getKind().equals(SymbolKind.Variable))
      .hasSize(1)
      .extracting(Either::getRight)
      .anyMatch(documentSymbol -> documentSymbol.getRange().equals(Ranges.create(8, 6, 8, 8)))
    ;

    // methods
    assertThat(documentSymbols)
      .filteredOn(documentSymbol -> documentSymbol.getRight().getKind().equals(SymbolKind.Method))
      .hasSize(3)
      .extracting(Either::getRight)
      .anyMatch(documentSymbol -> documentSymbol.getRange().equals(Ranges.create(10, 0, 11, 14)))
      .anyMatch(documentSymbol -> documentSymbol.getRange().equals(Ranges.create(13, 0, 14, 12)))
      .anyMatch(documentSymbol -> documentSymbol.getRange().equals(Ranges.create(16, 0, 19, 14)))
      .anyMatch(documentSymbol -> documentSymbol.getChildren().size() == 3)
    ;

    // sub vars
    assertThat(documentSymbols)
      .filteredOn(documentSymbol -> documentSymbol.getRight().getKind().equals(SymbolKind.Method))
      .extracting(Either::getRight)
      .flatExtracting(DocumentSymbol::getChildren)
      .hasSize(3)
      .anyMatch(subVar -> subVar.getRange().equals(Ranges.create(17, 10, 17, 11)))
      .anyMatch(subVar -> subVar.getRange().equals(Ranges.create(18, 10, 18, 11)))
      .anyMatch(subVar -> subVar.getRange().equals(Ranges.create(18, 12, 18, 13)))
    ;

    // regions
    assertThat(documentSymbols)
      .filteredOn(documentSymbol -> documentSymbol.getRight().getKind().equals(SymbolKind.Namespace))
      .extracting(Either::getRight)
      .hasSize(2)

      .flatExtracting(DocumentSymbol::getChildren)
      .hasSize(5)
      .anyMatch(documentSymbol -> documentSymbol.getKind().equals(SymbolKind.Namespace))
      .anyMatch(documentSymbol -> documentSymbol.getKind().equals(SymbolKind.Method))

      .filteredOn(documentSymbol -> documentSymbol.getKind().equals(SymbolKind.Namespace))
      .flatExtracting(DocumentSymbol::getChildren)
      .hasSize(1)
      .anyMatch(documentSymbol -> documentSymbol.getKind().equals(SymbolKind.Method))
      .anyMatch(subVar -> subVar.getRange().equals(Ranges.create(29, 0, 31, 14)))
    ;

  }

}
