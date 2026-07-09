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
package com.github._1c_syntax.bsl.languageserver.context.symbol;

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SymbolTreeTest {

  @Test
  void findSymbolByNamePositionResolvesEveryDeclarationName() {
    // given — модуль с областью, двумя переменными на одной строке и методом.
    var documentContext = TestUtils.getDocumentContext("""
      #Область МояОбласть
      Перем ПеременнаяА, ПеременнаяБ;
      Процедура Тест()
      КонецПроцедуры
      #КонецОбласти
      """);
    var symbolTree = documentContext.getSymbolTree();

    // then — на начале имени каждого символа резолвится именно он (парити с
    // прежним линейным сканом getChildrenFlat().filter(containsPosition)).
    for (var symbol : symbolTree.getChildrenFlat()) {
      var nameStart = symbol.getSelectionRange().getStart();
      assertThat(symbolTree.findSymbolByNamePosition(nameStart))
        .as("символ на начале своего имени: %s", symbol.getName())
        .contains(symbol);
    }
  }

  @Test
  void findSymbolByNamePositionDisambiguatesTwoDeclarationsOnSameLine() {
    // given — два объявления переменных на одной строке в разных колонках.
    var documentContext = TestUtils.getDocumentContext("""
      Перем ПеременнаяА, ПеременнаяБ;
      """);
    var symbolTree = documentContext.getSymbolTree();
    var varA = variable(symbolTree, "ПеременнаяА");
    var varB = variable(symbolTree, "ПеременнаяБ");

    // sanity — обе на одной строке.
    assertThat(varA.getSelectionRange().getStart().getLine())
      .isEqualTo(varB.getSelectionRange().getStart().getLine());

    // then — по колонке имени возвращается нужная переменная, а не первая по порядку.
    assertThat(symbolTree.findSymbolByNamePosition(varA.getSelectionRange().getStart())).contains(varA);
    assertThat(symbolTree.findSymbolByNamePosition(varB.getSelectionRange().getStart())).contains(varB);
  }

  @Test
  void findSymbolByNamePositionEmptyWhenNotOnDeclaration() {
    // given
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Тест()
          А = 1;
      КонецПроцедуры
      """);
    var symbolTree = documentContext.getSymbolTree();
    var method = symbolTree.getMethods().getFirst();

    // позиция на ключевом слове «Процедура» (0,0) — не на имени какого-либо
    // символа (имя метода начинается в колонке 10) → empty.
    var onKeyword = new Position(0, 0);
    assertThat(Ranges.containsPosition(method.getSelectionRange(), onKeyword)).isFalse();
    assertThat(symbolTree.findSymbolByNamePosition(onKeyword)).isEmpty();

    // позиция на несуществующей строке — empty.
    assertThat(symbolTree.findSymbolByNamePosition(new Position(999, 0))).isEmpty();
  }

  private static SourceDefinedSymbol variable(SymbolTree symbolTree, String name) {
    return symbolTree.getVariables().stream()
      .filter(v -> v.getName().equalsIgnoreCase(name))
      .findFirst()
      .orElseThrow();
  }
}
