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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TableDataKindTest {

  @Test
  void rowTypeFollowsTheDataKind() {
    // Правило синтакс-помощника (`ТаблицаФормы.ДанныеСтроки`): динамический список —
    // ДанныеФормыСтруктура, дерево значений — ДанныеФормыЭлементДерева, остальные —
    // ДанныеФормыЭлементКоллекции.
    assertThat(TableDataKind.DYNAMIC_LIST.rowTypeName()).isEqualTo("ДанныеФормыСтруктура");
    assertThat(TableDataKind.VALUE_TREE.rowTypeName()).isEqualTo("ДанныеФормыЭлементДерева");
    assertThat(TableDataKind.TABULAR_SECTION.rowTypeName()).isEqualTo("ДанныеФормыЭлементКоллекции");
    assertThat(TableDataKind.VALUE_TABLE.rowTypeName()).isEqualTo("ДанныеФормыЭлементКоллекции");
    assertThat(TableDataKind.RECORD_SET.rowTypeName()).isEqualTo("ДанныеФормыЭлементКоллекции");
  }

  @Test
  void kindsWithoutOwnRowGiveFormDataStructure() {
    // Своей строки нет у частей компоновщика настроек, отбора и диаграммы Ганта: их
    // ТекущиеДанные — ДанныеФормыСтруктура. У видов со своей строкой тип берётся с неё.
    assertThat(TableDataKind.DCS_FILTER.rowTypeName()).isNull();
    assertThat(TableDataKind.DCS_FILTER.currentDataTypeName()).isEqualTo("ДанныеФормыСтруктура");
    assertThat(TableDataKind.RECORD_SET.currentDataTypeName()).isNull();
  }

  @Test
  void recordSetIsRecognizedByItsFamily() {
    // Имя типа набора несёт вид регистра, а суффикс вида у всех регистров общий.
    assertThat(TableDataKind.of("РегистрСведенийНаборЗаписей.Курсы", false)).isEqualTo(TableDataKind.RECORD_SET);
    assertThat(TableDataKind.of("РегистрНакопленияНаборЗаписей.Остатки", false))
      .isEqualTo(TableDataKind.RECORD_SET);
    assertThat(TableDataKind.RECORD_SET.rowIdTypeName()).isEqualTo("Число");
  }

  @Test
  void suffixOfRecordSetIsNotATypeName() {
    // Как и у табличной части, суффикс набора — семейство, а не имя типа: по нему самому
    // вид не опознаётся, только по имени типа реквизита.
    assertThat(TableDataKind.byTypeName("НаборЗаписей")).isNull();
    assertThat(TableDataKind.of("СправочникСсылка.Товары", false)).isNull();
    assertThat(TableDataKind.of("СправочникСсылка.Товары", true)).isEqualTo(TableDataKind.TABULAR_SECTION);
  }
}
