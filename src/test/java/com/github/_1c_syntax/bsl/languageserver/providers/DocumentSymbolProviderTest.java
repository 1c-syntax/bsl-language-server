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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentSymbolProviderTest {

  @Test
  void testDocumentSymbol() throws IOException {

    String fileContent = FileUtils.readFileToString(
      new File("./src/test/resources/providers/documentSymbol.bsl"),
      StandardCharsets.UTF_8
    );
    DocumentContext documentContext = new DocumentContext("fake-uri.bsl", fileContent);

    List<Either<SymbolInformation, DocumentSymbol>> documentSymbols = DocumentSymbolProvider.getDocumentSymbol(documentContext);

    assertThat(documentSymbols).hasSize(8);

    // global variables
    assertThat(documentSymbols)
      .filteredOn(documentSymbol -> documentSymbol.getRight().getKind().equals(SymbolKind.Variable))
      .hasSize(3)
      .extracting(Either::getRight)
      .anyMatch(documentSymbol -> documentSymbol.getRange().equals(Ranges.create(0, 6, 0, 7)))
      .anyMatch(documentSymbol -> documentSymbol.getRange().equals(Ranges.create(2, 6, 2, 7)))
      .anyMatch(documentSymbol -> documentSymbol.getRange().equals(Ranges.create(2, 9, 2, 10)))
      ;

    // methods
    assertThat(documentSymbols)
      .filteredOn(documentSymbol -> documentSymbol.getRight().getKind().equals(SymbolKind.Method))
      .hasSize(4)
      .extracting(Either::getRight)
      .anyMatch(documentSymbol -> documentSymbol.getRange().equals(Ranges.create(4, 0, 5, 14)))
      .anyMatch(documentSymbol -> documentSymbol.getRange().equals(Ranges.create(7, 0, 8, 12)))
      .anyMatch(documentSymbol -> documentSymbol.getRange().equals(Ranges.create(10, 0, 13, 14)))
      .anyMatch(documentSymbol -> documentSymbol.getRange().equals(Ranges.create(30, 0, 34, 35)))
      .anyMatch(documentSymbol -> documentSymbol.getChildren().size() == 3)
    ;

    // sub vars
    assertThat(documentSymbols)
      .filteredOn(documentSymbol -> documentSymbol.getRight().getKind().equals(SymbolKind.Method))
      .extracting(Either::getRight)
      .flatExtracting(DocumentSymbol::getChildren)
      .hasSize(3)
      .anyMatch(subVar -> subVar.getRange().equals(Ranges.create(11, 10, 11, 11)))
      .anyMatch(subVar -> subVar.getRange().equals(Ranges.create(12, 10, 12, 11)))
      .anyMatch(subVar -> subVar.getRange().equals(Ranges.create(12, 12, 12, 13)))
      ;

    // regions
    assertThat(documentSymbols)
      .filteredOn(documentSymbol -> documentSymbol.getRight().getKind().equals(SymbolKind.Namespace))
      .extracting(Either::getRight)
      .hasSize(1)

      .flatExtracting(DocumentSymbol::getChildren)
      .hasSize(2)
      .anyMatch(documentSymbol -> documentSymbol.getKind().equals(SymbolKind.Namespace))
      .anyMatch(documentSymbol -> documentSymbol.getKind().equals(SymbolKind.Method))

      .filteredOn(documentSymbol -> documentSymbol.getKind().equals(SymbolKind.Namespace))
      .flatExtracting(DocumentSymbol::getChildren)
      .hasSize(1)
      .anyMatch(documentSymbol -> documentSymbol.getKind().equals(SymbolKind.Method))
      .anyMatch(subVar -> subVar.getRange().equals(Ranges.create(23, 0, 25, 14)))
      ;

  }

}
