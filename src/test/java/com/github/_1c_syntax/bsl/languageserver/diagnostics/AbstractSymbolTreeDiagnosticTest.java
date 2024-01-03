/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AbstractSymbolTreeDiagnosticTest {

  @Test
  void testAllSymbolsWereVisited() {
    // given
    var diagnostic = new AbstractSymbolTreeDiagnostic() {
      public final Set<SourceDefinedSymbol> visitedSymbols = new HashSet<>();

      @Override
      public void visitModule(ModuleSymbol module) {
        visitedSymbols.add(module);
        super.visitModule(module);
      }

      @Override
      public void visitRegion(RegionSymbol region) {
        visitedSymbols.add(region);
        super.visitRegion(region);
      }

      @Override
      public void visitMethod(MethodSymbol method) {
        visitedSymbols.add(method);
        super.visitMethod(method);
      }

      @Override
      public void visitVariable(VariableSymbol variable) {
        visitedSymbols.add(variable);
        super.visitVariable(variable);
      }
    };

    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/diagnostics/AbstractSymbolTreeDiagnostic.bsl");

    // when
    diagnostic.getDiagnostics(documentContext);

    // then
    var allSymbols = documentContext.getSymbolTree().getChildrenFlat();
    var module = documentContext.getSymbolTree().getModule();
    assertThat(diagnostic.visitedSymbols)
      .containsAll(allSymbols)
      .contains(module)
    ;
  }
}
