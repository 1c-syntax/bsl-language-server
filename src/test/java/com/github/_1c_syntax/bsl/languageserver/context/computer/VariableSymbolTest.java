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

    assertThat(variableSymbols).hasSize(23);

    assertThat(variableSymbols)
      .filteredOn(variableSymbol -> variableSymbol.getDescription().isEmpty())
      .hasSize(11)
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(12, 6, 34)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(14, 6, 27)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(16, 6, 17)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(16, 19, 30)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(32, 12, 29)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(32, 31, 59)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(33, 10, 19)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(55, 0, 35)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(56, 0, 47)))
    ;

    assertThat(variableSymbols)
      .filteredOn(variableSymbol -> variableSymbol.getDescription().isPresent())
      .hasSize(12)
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(2, 6, 32)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(6, 6, 33)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(8, 6, 33)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(19, 6, 18)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(24, 6, 18)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(35, 10, 20)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(39, 10, 20)))
      .anyMatch(variableSymbol -> variableSymbol.getRange().equals(Ranges.create(46, 10, 21)))
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
      .hasSize(12)
      .filteredOn(variableDescription -> !variableDescription.getDescription().isEmpty())
      .hasSize(5)
      .anyMatch(variableDescription -> variableDescription.getRange().equals(Ranges.create(1, 0, 18)))
      .anyMatch(variableDescription -> variableDescription.getRange().equals(Ranges.create(4, 0, 5, 23)))
      .anyMatch(variableDescription -> variableDescription.getRange().equals(Ranges.create(21, 0, 23, 29)))
      .anyMatch(variableDescription -> variableDescription.getRange().equals(Ranges.create(37, 4, 25)))
      .anyMatch(variableDescription -> variableDescription.getRange().equals(Ranges.create(41, 4, 45, 27)))
    ;

    assertThat(variableDescriptions)
      .extracting(VariableDescription::getTrailingDescription)
      .filteredOn(Optional::isPresent)
      .hasSize(9)
      .extracting(Optional::get)
      .anyMatch(trailingDescription -> trailingDescription.getRange().equals(Ranges.create(8, 35, 55)))
      .anyMatch(variableDescription -> variableDescription.getRange().equals(Ranges.create(19, 20, 42)))
      .anyMatch(variableDescription -> variableDescription.getRange().equals(Ranges.create(24, 20, 42)))
      .anyMatch(variableDescription -> variableDescription.getRange().equals(Ranges.create(35, 21, 43)))
      .anyMatch(variableDescription -> variableDescription.getRange().equals(Ranges.create(39, 21, 43)))
      .anyMatch(variableDescription -> variableDescription.getRange().equals(Ranges.create(63, 45, 77)))
      .anyMatch(variableDescription -> variableDescription.getRange().equals(Ranges.create(71, 38, 70)))
    ;

  }

  @Test
  void testVariableNameRange() {

    assertThat(variableSymbols)
      .filteredOn(variableSymbol -> variableSymbol.getDescription().isEmpty())
      .hasSize(11)
      .anyMatch(variableName -> variableName.getVariableNameRange().equals(Ranges.create(12, 6, 34)))
      .anyMatch(variableName -> variableName.getVariableNameRange().equals(Ranges.create(14, 6, 27)))
      .anyMatch(variableName -> variableName.getVariableNameRange().equals(Ranges.create(16, 6, 17)))
      .anyMatch(variableName -> variableName.getVariableNameRange().equals(Ranges.create(16, 19, 30)))
      .anyMatch(variableName -> variableName.getVariableNameRange().equals(Ranges.create(32, 12, 29)))
      .anyMatch(variableName -> variableName.getVariableNameRange().equals(Ranges.create(32, 36, 57)))
      .anyMatch(variableName -> variableName.getVariableNameRange().equals(Ranges.create(33, 10, 19)))
      .anyMatch(variableName -> variableName.getVariableNameRange().equals(Ranges.create(55, 0, 35)))
      .anyMatch(variableName -> variableName.getVariableNameRange().equals(Ranges.create(56, 0, 47)))
      .anyMatch(variableName -> variableName.getVariableNameRange().equals(Ranges.create(59, 12, 30)))
      .anyMatch(variableName -> variableName.getVariableNameRange().equals(Ranges.create(67, 4, 22)))
    ;
  }

  @Test
  void testVariableKind() {

    assertThat(variableSymbols.get(0).getKind()).isEqualTo(VariableKind.MODULE);
    assertThat(variableSymbols.get(9).getKind()).isEqualTo(VariableKind.PARAMETER);
    assertThat(variableSymbols.get(14).getKind()).isEqualTo(VariableKind.LOCAL);
    assertThat(variableSymbols.get(18).getKind()).isEqualTo(VariableKind.DYNAMIC);

  }

}
