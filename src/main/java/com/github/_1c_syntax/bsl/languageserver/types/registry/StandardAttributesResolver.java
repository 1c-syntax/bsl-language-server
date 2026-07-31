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

import com.github._1c_syntax.bsl.context.api.KnownStandardAttributes;
import com.github._1c_syntax.bsl.mdo.Catalog;
import com.github._1c_syntax.bsl.mdo.ChartOfCharacteristicTypes;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.TabularSection;
import com.github._1c_syntax.bsl.mdo.support.HierarchyType;
import com.github._1c_syntax.bsl.types.MDOType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.types.registry.MetadataCollectionSpecializer.ChildName;

/**
 * Резолв стандартных реквизитов MD-объекта через {@link KnownStandardAttributes}
 * из bsl-context. Вынесено из {@link MetadataChildrenExtractor} чтобы снизить
 * coupling последнего.
 */
final class StandardAttributesResolver {

  private static final Map<MDOType, String> OWNER_TYPE_BY_MDO_TYPE = Map.ofEntries(
    Map.entry(MDOType.CATALOG, "ОбъектМетаданных: Справочник"),
    Map.entry(MDOType.DOCUMENT, "ОбъектМетаданных: Документ"),
    Map.entry(MDOType.CHART_OF_ACCOUNTS, "ОбъектМетаданных: ПланСчетов"),
    Map.entry(MDOType.CHART_OF_CALCULATION_TYPES, "ОбъектМетаданных: ПланВидовРасчета"),
    Map.entry(MDOType.CHART_OF_CHARACTERISTIC_TYPES, "ОбъектМетаданных: ПланВидовХарактеристик"),
    Map.entry(MDOType.EXCHANGE_PLAN, "ОбъектМетаданных: ПланОбмена"),
    Map.entry(MDOType.TASK, "ОбъектМетаданных: Задача"),
    Map.entry(MDOType.BUSINESS_PROCESS, "ОбъектМетаданных: БизнесПроцесс"),
    Map.entry(MDOType.ENUM, "ОбъектМетаданных: Перечисление"),
    Map.entry(MDOType.INFORMATION_REGISTER, "ОбъектМетаданных: РегистрСведений"),
    Map.entry(MDOType.ACCUMULATION_REGISTER, "ОбъектМетаданных: РегистрНакопления"),
    Map.entry(MDOType.ACCOUNTING_REGISTER, "ОбъектМетаданных: РегистрБухгалтерии"),
    Map.entry(MDOType.CALCULATION_REGISTER, "ОбъектМетаданных: РегистрРасчета"),
    Map.entry(MDOType.TABULAR_SECTION, "ОбъектМетаданных: ТабличнаяЧасть")
  );

  /** Стандартный реквизит иерархического объекта — ссылка на родителя. */
  private static final String PARENT = "Родитель";

  /** Признак группы — есть только при иерархии групп и элементов. */
  private static final String IS_FOLDER = "ЭтоГруппа";

  private StandardAttributesResolver() {
  }

  static List<ChildName> standardAttributesFor(MD md) {
    var ownerType = ownerTypeFor(md);
    if (ownerType == null) {
      return List.of();
    }
    var names = KnownStandardAttributes.forOwner(ownerType);
    if (names.isEmpty()) {
      return List.of();
    }
    var result = new ArrayList<ChildName>(names.size());
    for (var n : names) {
      if (!appliesTo(md, n.getName())) {
        continue;
      }
      var entry = ChildName.bilingual(n.getName(), n.getAlias());
      if (entry != null) {
        result.add(entry);
      }
    }
    return List.copyOf(result);
  }

  /**
   * Есть ли стандартный реквизит у конкретного объекта. Список от
   * {@code KnownStandardAttributes} зависит только от вида объекта, а два реквизита
   * зависят ещё и от его настроек:
   * <ul>
   *   <li>{@code Родитель} есть только у иерархического справочника или плана видов
   *       характеристик;</li>
   *   <li>{@code ЭтоГруппа} — только при иерархии групп и элементов: при иерархии
   *       элементов групп не бывает.</li>
   * </ul>
   */
  private static boolean appliesTo(MD md, String attributeName) {
    if (PARENT.equalsIgnoreCase(attributeName)) {
      return isHierarchical(md);
    }
    if (IS_FOLDER.equalsIgnoreCase(attributeName)) {
      return isHierarchical(md) && hasFolders(md);
    }
    return true;
  }

  private static boolean isHierarchical(MD md) {
    return switch (md) {
      case Catalog catalog -> catalog.isHierarchical();
      case ChartOfCharacteristicTypes chart -> chart.isHierarchical();
      // Прочие виды объектов иерархичны по своей природе (план счетов) либо
      // реквизита не имеют вовсе — тогда сюда и не попадём.
      default -> true;
    };
  }

  /**
   * Бывают ли у иерархии этого объекта группы. Выбор вида иерархии есть только у
   * справочника; у плана видов характеристик иерархия всегда групп и элементов.
   */
  private static boolean hasFolders(MD md) {
    if (md instanceof Catalog catalog) {
      return catalog.getHierarchyType() == HierarchyType.HIERARCHY_FOLDERS_AND_ITEMS;
    }
    return true;
  }

  static @Nullable String ownerTypeFor(MD md) {
    var byMdoType = OWNER_TYPE_BY_MDO_TYPE.get(md.getMdoType());
    if (byMdoType != null) {
      return byMdoType;
    }
    if (md instanceof TabularSection) {
      return OWNER_TYPE_BY_MDO_TYPE.get(MDOType.TABULAR_SECTION);
    }
    return null;
  }

  static boolean hasStandardAttributes(MD md) {
    return ownerTypeFor(md) != null;
  }
}
