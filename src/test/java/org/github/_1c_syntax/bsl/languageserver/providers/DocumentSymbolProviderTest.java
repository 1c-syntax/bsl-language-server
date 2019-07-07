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

import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Java6Assertions.assertThat;

class DocumentSymbolProviderTest {

  @Test
  void testDocumentSymbol() throws IOException {

    String fileContent = FileUtils.readFileToString(
      new File("./src/test/resources/providers/documentSymbol.bsl"),
      StandardCharsets.UTF_8
    );
    DocumentContext documentContext = new DocumentContext("fake-uri.bsl", fileContent);

    List<Either<SymbolInformation, DocumentSymbol>> documentSymbols = DocumentSymbolProvider.getDocumentSymbol(documentContext);

    assertThat(documentSymbols).hasSize(6);

    // global variables
    assertThat(documentSymbols)
      .filteredOn(documentSymbol -> documentSymbol.getRight().getKind().equals(SymbolKind.Variable))
      .hasSize(3)
      .extracting(Either::getRight)
      .anyMatch(documentSymbol -> documentSymbol.getRange().equals(RangeHelper.newRange(0, 6, 0, 7)))
      .anyMatch(documentSymbol -> documentSymbol.getRange().equals(RangeHelper.newRange(2, 6, 2, 7)))
      .anyMatch(documentSymbol -> documentSymbol.getRange().equals(RangeHelper.newRange(2, 9, 2, 10)))
      ;

    // methods
    assertThat(documentSymbols)
      .filteredOn(documentSymbol -> documentSymbol.getRight().getKind().equals(SymbolKind.Method))
      .hasSize(3)
      .extracting(Either::getRight)
      .anyMatch(documentSymbol -> documentSymbol.getRange().equals(RangeHelper.newRange(4, 0, 5, 14)))
      .anyMatch(documentSymbol -> documentSymbol.getRange().equals(RangeHelper.newRange(7, 0, 8, 12)))
      .anyMatch(documentSymbol -> documentSymbol.getRange().equals(RangeHelper.newRange(10, 0, 13, 14)))
      .anyMatch(documentSymbol -> documentSymbol.getChildren().size() == 3)
    ;

    // sub vars
    assertThat(documentSymbols)
      .filteredOn(documentSymbol -> documentSymbol.getRight().getKind().equals(SymbolKind.Method))
      .extracting(Either::getRight)
      .flatExtracting(DocumentSymbol::getChildren)
      .hasSize(3)
      .anyMatch(subVar -> subVar.getRange().equals(RangeHelper.newRange(11, 10, 11, 11)))
      .anyMatch(subVar -> subVar.getRange().equals(RangeHelper.newRange(12, 10, 12, 11)))
      .anyMatch(subVar -> subVar.getRange().equals(RangeHelper.newRange(12, 12, 12, 13)))
      ;

  }
}
