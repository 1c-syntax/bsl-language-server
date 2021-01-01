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
package com.github._1c_syntax.bsl.languageserver.context.computer;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTreeVisitor;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import lombok.Getter;
import lombok.Setter;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class SymbolTreeComputer implements Computer<SymbolTree> {

  private final DocumentContext documentContext;

  public SymbolTreeComputer(DocumentContext documentContext) {
    this.documentContext = documentContext;
  }

  @Override
  public SymbolTree compute() {

    ModuleSymbol moduleSymbol = new ModuleSymbolComputer(documentContext).compute();
    List<MethodSymbol> methods = new MethodSymbolComputer(documentContext).compute();
    List<RegionSymbol> regions = new RegionSymbolComputer(documentContext).compute();
    List<VariableSymbol> variables = new VariableSymbolComputer(documentContext).compute();

    List<SourceDefinedSymbol> allOfThem = new ArrayList<>(methods);
    allOfThem.addAll(regions);
    allOfThem.addAll(variables);

    allOfThem.sort(Comparator.comparingInt(symbol -> symbol.getRange().getStart().getLine()));

    List<SourceDefinedSymbol> topLevelSymbols = new ArrayList<>();
    SourceDefinedSymbol currentParent = moduleSymbol;

    for (SourceDefinedSymbol symbol : allOfThem) {
      currentParent = placeSymbol(topLevelSymbols, currentParent, symbol);
    }

    return new SymbolTree(moduleSymbol);
  }

  private static SourceDefinedSymbol placeSymbol(
    List<SourceDefinedSymbol> topLevelSymbols,
    SourceDefinedSymbol currentParent,
    SourceDefinedSymbol symbol
  ) {

    if (Ranges.containsRange(currentParent.getRange(), symbol.getRange())) {
      currentParent.getChildren().add(symbol);
      symbol.setParent(Optional.of(currentParent));

      return symbol;
    }

    Optional<SourceDefinedSymbol> maybeParent = currentParent.getParent();
    if (maybeParent.isEmpty()) {
      topLevelSymbols.add(symbol);
      return symbol;
    }

    return placeSymbol(topLevelSymbols, maybeParent.get(), symbol);
  }

  private static SourceDefinedSymbol emptySymbol() {
    return new SourceDefinedSymbol() {
      @Getter
      private final DocumentContext owner = null;
      @Getter
      private final String name = "empty";
      @Getter
      private final SymbolKind symbolKind = SymbolKind.Null;
      @Getter
      private final Range range = Ranges.create(-1, 0, -1, 0);
      @Getter
      private final Range selectionRange = Ranges.create(-1, 0, -1, 0);
      @Getter
      @Setter
      private Optional<SourceDefinedSymbol> parent = Optional.empty();
      @Getter
      private final List<SourceDefinedSymbol> children = Collections.emptyList();

      @Override
      public void accept(SymbolTreeVisitor visitor) {
      }

    };
  }
}
