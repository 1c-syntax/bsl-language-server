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

import com.github._1c_syntax.bsl.mdo.Catalog;
import com.github._1c_syntax.bsl.mdo.ChartOfCharacteristicTypes;
import com.github._1c_syntax.bsl.mdo.Document;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.support.HierarchyType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Набор стандартных реквизитов зависит не только от вида объекта: два реквизита
 * иерархии есть не у каждого справочника. Проверяется без Spring — это чистая функция
 * над объектом mdclasses и таблицей из bsl-context.
 */
class StandardAttributesResolverTest {

  @Test
  void flatCatalogHasNeitherParentNorIsFolder() {
    assertThat(namesOf(Catalog.builder().name("С").hierarchical(false).build()))
      .contains("Ссылка", "Код", "Наименование")
      .doesNotContain("Родитель", "ЭтоГруппа");
  }

  @Test
  void hierarchyOfFoldersAndItemsHasBoth() {
    assertThat(namesOf(Catalog.builder().name("С")
      .hierarchical(true)
      .hierarchyType(HierarchyType.HIERARCHY_FOLDERS_AND_ITEMS)
      .build()))
      .contains("Родитель", "ЭтоГруппа");
  }

  @Test
  void hierarchyOfItemsHasParentButNoIsFolder() {
    // При иерархии элементов групп не бывает, значит и признака группы нет.
    assertThat(namesOf(Catalog.builder().name("С")
      .hierarchical(true)
      .hierarchyType(HierarchyType.HIERARCHY_OF_ITEMS)
      .build()))
      .contains("Родитель")
      .doesNotContain("ЭтоГруппа");
  }

  @Test
  void chartOfCharacteristicTypesFollowsItsOwnHierarchyFlag() {
    assertThat(namesOf(ChartOfCharacteristicTypes.builder().name("ПВХ").hierarchical(false).build()))
      .doesNotContain("Родитель", "ЭтоГруппа");
    assertThat(namesOf(ChartOfCharacteristicTypes.builder().name("ПВХ").hierarchical(true).build()))
      .as("у плана видов характеристик иерархия всегда групп и элементов")
      .contains("Родитель", "ЭтоГруппа");
  }

  @Test
  void kindWithoutHierarchyIsUntouched() {
    // У документа реквизитов иерархии нет вовсе — фильтр не должен ничего отрезать.
    assertThat(namesOf(Document.builder().name("Д").build()))
      .contains("Ссылка", "Дата", "Номер");
  }

  private static List<String> namesOf(MD md) {
    return StandardAttributesResolver.standardAttributesFor(md).stream()
      .map(child -> child.name().ru())
      .toList();
  }
}
