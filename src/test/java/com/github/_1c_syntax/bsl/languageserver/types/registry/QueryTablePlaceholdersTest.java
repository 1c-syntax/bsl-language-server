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
package com.github._1c_syntax.bsl.languageserver.types.registry;

import com.github._1c_syntax.bsl.mdclasses.CF;
import com.github._1c_syntax.bsl.mdo.AccountingRegister;
import com.github._1c_syntax.bsl.mdo.ChartOfAccounts;
import com.github._1c_syntax.bsl.types.MDOType;
import com.github._1c_syntax.bsl.types.MdoReference;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Номера субконто в именах полей виртуальных таблиц регистра бухгалтерии
 * ({@code Субконто<Номер субконто>}) — их задаёт план счетов, на котором стоит
 * регистр.
 */
class QueryTablePlaceholdersTest {

  private static final MdoReference CHART_REF =
    MdoReference.create(MDOType.CHART_OF_ACCOUNTS, "Хозрасчетный");

  @Test
  void extDimensionNumbersComeFromTheChartOfAccounts() {
    // given
    var request = request(chartOfAccounts(3));

    // when
    var values = QueryTablePlaceholders.expansionValues(
      QueryTablePlaceholders.EXT_DIMENSION_NUMBER, request);

    // then
    assertThat(values).containsExactly("1", "2", "3");
  }

  @Test
  void chartWithoutExtDimensionsGivesNoNumbers() {
    // given
    var request = request(chartOfAccounts(0));

    // when
    var values = QueryTablePlaceholders.expansionValues(
      QueryTablePlaceholders.EXT_DIMENSION_NUMBER, request);

    // then
    assertThat(values).isEmpty();
  }

  @Test
  void registerWithoutAKnownChartGivesNoNumbers() {
    // given
    var configuration = mock(CF.class);
    when(configuration.findChild(any(MdoReference.class))).thenReturn(Optional.empty());
    var request = new QueryTableRequest("AccountingRegister.Хозрасчетный",
      accountingRegister(), configuration, null, Map.of(), null);

    // when
    var values = QueryTablePlaceholders.expansionValues(
      QueryTablePlaceholders.EXT_DIMENSION_NUMBER, request);

    // then
    assertThat(values).isEmpty();
  }

  private static QueryTableRequest request(ChartOfAccounts chart) {
    var configuration = mock(CF.class);
    when(configuration.findChild(CHART_REF)).thenReturn(Optional.of(chart));
    return new QueryTableRequest("AccountingRegister.Хозрасчетный",
      accountingRegister(), configuration, null, Map.of(), null);
  }

  private static AccountingRegister accountingRegister() {
    return AccountingRegister.builder()
      .name("Хозрасчетный")
      .mdoReference(MdoReference.create(MDOType.ACCOUNTING_REGISTER, "Хозрасчетный"))
      .chartOfAccounts(CHART_REF)
      .build();
  }

  private static ChartOfAccounts chartOfAccounts(int maxExtDimensionCount) {
    return ChartOfAccounts.builder()
      .name("Хозрасчетный")
      .mdoReference(CHART_REF)
      .maxExtDimensionCount(maxExtDimensionCount)
      .build();
  }
}
