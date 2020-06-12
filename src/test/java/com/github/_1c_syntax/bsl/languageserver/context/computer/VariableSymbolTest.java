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
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableDescription;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class VariableSymbolTest {

  static DocumentContext documentContext;
  static List<VariableSymbol> variableSymbols;

  static {
    documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/context/symbol/variableSymbolTest.bsl");
    variableSymbols = documentContext.getSymbolTree().getVariables();
  }

  @Test
  void testVariableSymbolDescription() {

    assertThat(variableSymbols).hasSize(8);

    assertThat(variableSymbols)
      .filteredOn(variableSymbol -> variableSymbol.getDescription().isEmpty())
      .hasSize(5)
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(12, 6, 12, 34)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(14, 6, 14, 27)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(16, 6, 16, 17)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(16, 19, 16, 30)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(19, 10, 19, 19)))
    ;

    assertThat(variableSymbols)
      .filteredOn(variableSymbol -> variableSymbol.getDescription().isPresent())
      .hasSize(3)
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(2, 6, 2, 32)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(6, 6, 6, 32)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(8, 6, 8, 33)))
    ;

  }

  @Test
  void testVariableDescriptionRange() {

    List<VariableDescription> variableDescriptions = variableSymbols.stream()
      .map(VariableSymbol::getDescription)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(Collectors.toList());

    assertThat(variableDescriptions).hasSize(3);

    assertThat(variableDescriptions)
      .filteredOn(variableDescription -> !variableDescription.getDescription().equals(""))
      .hasSize(2)
      .anyMatch(variableDescription -> variableDescription.getRange().equals(Ranges.create(1, 0, 1, 18)))
      .anyMatch(variableDescription -> variableDescription.getRange().equals(Ranges.create(4, 0, 5, 23)))
    ;

    assertThat(variableDescriptions)
      .extracting(VariableDescription::getTrailingDescription)
      .filteredOn(Optional::isPresent)
      .hasSize(1)
      .extracting(Optional::get)
      .anyMatch(trailingDescription -> trailingDescription.getRange().equals(Ranges.create(8, 35, 8, 55)))
    ;

  }

  @Test
  void testVariableNameRange() {

    assertThat(variableSymbols)
      .filteredOn(variableSymbol -> variableSymbol.getDescription().isEmpty())
      .hasSize(5)
      .anyMatch(variableName -> variableName.getVariableNameRange().equals(Ranges.create(12, 6, 12, 34)))
      .anyMatch(variableName -> variableName.getVariableNameRange().equals(Ranges.create(14, 6, 14, 27)))
      .anyMatch(variableName -> variableName.getVariableNameRange().equals(Ranges.create(16, 6, 16, 17)))
      .anyMatch(variableName -> variableName.getVariableNameRange().equals(Ranges.create(16, 19, 16, 30)))
      .anyMatch(variableName -> variableName.getVariableNameRange().equals(Ranges.create(19, 10, 19, 19)))

    ;
  }

  @Test
  void testVariableKind() {

    assertThat(variableSymbols.get(0).getKind()).isEqualTo(VariableKind.MODULE);
    assertThat(variableSymbols.get(7).getKind()).isEqualTo(VariableKind.LOCAL);

  }

}
