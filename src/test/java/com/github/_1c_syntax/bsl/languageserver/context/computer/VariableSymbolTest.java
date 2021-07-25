/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class VariableSymbolTest {

  static DocumentContext documentContext;
  static List<VariableSymbol> variableSymbols;

  @BeforeEach
  void setup() {
    documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/context/symbol/variableSymbolTest.bsl");
    variableSymbols = documentContext.getSymbolTree().getVariables();
  }

  @Test
  void testVariableSymbolDescription() {

    assertThat(variableSymbols).hasSize(13);

    assertThat(variableSymbols)
      .filteredOn(variableSymbol -> variableSymbol.getDescription().isEmpty())
      .hasSize(5)
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(12, 6, 34)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(14, 6, 27)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(16, 6, 17)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(16, 19, 30)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(27, 10, 19)))
    ;

    assertThat(variableSymbols)
      .filteredOn(variableSymbol -> variableSymbol.getDescription().isPresent())
      .hasSize(8)
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(2, 6, 32)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(6, 6, 32)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(8, 6, 33)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(19, 6, 18)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(24, 6, 18)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(29, 10, 20)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(33, 10, 20)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(40, 10, 21)))
    ;

  }

  @Test
  void testVariableDescriptionRange() {

    List<VariableDescription> variableDescriptions = variableSymbols.stream()
      .map(VariableSymbol::getDescription)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(Collectors.toList());

    assertThat(variableDescriptions)
      .hasSize(8)
      .filteredOn(variableDescription -> !variableDescription.getDescription().equals(""))
      .hasSize(5)
      .anyMatch(variableDescription -> variableDescription.getRange().equals(Ranges.create(1, 0, 18)))
      .anyMatch(variableDescription -> variableDescription.getRange().equals(Ranges.create(4, 0, 5, 23)))
      .anyMatch(variableDescription -> variableDescription.getRange().equals(Ranges.create(21, 0, 23, 29)))
      .anyMatch(variableDescription -> variableDescription.getRange().equals(Ranges.create(31, 4, 25)))
      .anyMatch(variableDescription -> variableDescription.getRange().equals(Ranges.create(35, 4, 39, 27)))
    ;

    assertThat(variableDescriptions)
      .extracting(VariableDescription::getTrailingDescription)
      .filteredOn(Optional::isPresent)
      .hasSize(5)
      .extracting(Optional::get)
      .anyMatch(trailingDescription -> trailingDescription.getRange().equals(Ranges.create(8, 35, 55)))
      .anyMatch(variableDescription -> variableDescription.getRange().equals(Ranges.create(19, 20, 42)))
      .anyMatch(variableDescription -> variableDescription.getRange().equals(Ranges.create(24, 20, 42)))
      .anyMatch(variableDescription -> variableDescription.getRange().equals(Ranges.create(29, 21, 43)))
      .anyMatch(variableDescription -> variableDescription.getRange().equals(Ranges.create(33, 21, 43)))
    ;

  }

  @Test
  void testVariableNameRange() {

    assertThat(variableSymbols)
      .filteredOn(variableSymbol -> variableSymbol.getDescription().isEmpty())
      .hasSize(5)
      .anyMatch(variableName -> variableName.getVariableNameRange().equals(Ranges.create(12, 6, 34)))
      .anyMatch(variableName -> variableName.getVariableNameRange().equals(Ranges.create(14, 6, 27)))
      .anyMatch(variableName -> variableName.getVariableNameRange().equals(Ranges.create(16, 6, 17)))
      .anyMatch(variableName -> variableName.getVariableNameRange().equals(Ranges.create(16, 19, 30)))
      .anyMatch(variableName -> variableName.getVariableNameRange().equals(Ranges.create(27, 10, 19)))
    ;
  }

  @Test
  void testVariableKind() {

    assertThat(variableSymbols.get(0).getKind()).isEqualTo(VariableKind.MODULE);
    assertThat(variableSymbols.get(variableSymbols.size() - 1).getKind()).isEqualTo(VariableKind.LOCAL);

  }

}
