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

import com.github._1c_syntax.bsl.mdo.AccountingRegister;
import com.github._1c_syntax.bsl.mdo.AccumulationRegister;
import com.github._1c_syntax.bsl.mdo.Attribute;
import com.github._1c_syntax.bsl.mdo.AttributeOwner;
import com.github._1c_syntax.bsl.mdo.BusinessProcess;
import com.github._1c_syntax.bsl.mdo.CalculationRegister;
import com.github._1c_syntax.bsl.mdo.Catalog;
import com.github._1c_syntax.bsl.mdo.ChartOfAccounts;
import com.github._1c_syntax.bsl.mdo.ChartOfCalculationTypes;
import com.github._1c_syntax.bsl.mdo.ChartOfCharacteristicTypes;
import com.github._1c_syntax.bsl.mdo.CommandOwner;
import com.github._1c_syntax.bsl.mdo.Document;
import com.github._1c_syntax.bsl.mdo.DocumentJournal;
import com.github._1c_syntax.bsl.mdo.Enum;
import com.github._1c_syntax.bsl.mdo.ExchangePlan;
import com.github._1c_syntax.bsl.mdo.FormOwner;
import com.github._1c_syntax.bsl.mdo.InformationRegister;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.Sequence;
import com.github._1c_syntax.bsl.mdo.TabularSection;
import com.github._1c_syntax.bsl.mdo.TabularSectionOwner;
import com.github._1c_syntax.bsl.mdo.Task;
import com.github._1c_syntax.bsl.mdo.TemplateOwner;
import com.github._1c_syntax.bsl.mdo.storage.AdditionalIndex;
import com.github._1c_syntax.bsl.mdo.support.AttributeKind;
import com.github._1c_syntax.bsl.types.MdoReference;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.types.registry.MetadataCollectionSpecializer.ChildName;

/**
 * Извлечение имён детей конкретной коллекции из MDO-объекта. Вынесено из
 * {@link MetadataCollectionSpecializer} чтобы сократить coupling последнего
 * (S1200): все MDO-instanceof-проверки локализованы здесь.
 */
final class MetadataChildrenExtractor {

  private MetadataChildrenExtractor() {
  }

  static List<ChildName> singleLingualMdNames(Collection<? extends MD> items) {
    var result = new ArrayList<ChildName>(items.size());
    for (var item : items) {
      var entry = ChildName.of(item.getName());
      if (entry != null) {
        result.add(entry);
      }
    }
    return List.copyOf(result);
  }

  static List<ChildName> customAttributeNames(Collection<? extends Attribute> items) {
    var result = new ArrayList<ChildName>(items.size());
    for (var item : items) {
      if (item.getKind() == AttributeKind.STANDARD) {
        continue;
      }
      var entry = ChildName.of(item.getName());
      if (entry != null) {
        result.add(entry);
      }
    }
    return List.copyOf(result);
  }

  static List<ChildName> tabularSectionEntries(Collection<? extends TabularSection> sections) {
    var result = new ArrayList<ChildName>(sections.size());
    for (var ts : sections) {
      var entry = ChildName.of(ts.getName(), ts);
      if (entry != null) {
        result.add(entry);
      }
    }
    return List.copyOf(result);
  }

  static List<ChildName> mdoReferenceNames(Collection<MdoReference> refs) {
    var result = new ArrayList<ChildName>(refs.size());
    for (var ref : refs) {
      var entry = mdoReferenceChildName(ref);
      if (entry != null) {
        result.add(entry);
      }
    }
    return List.copyOf(result);
  }

  private static @Nullable ChildName mdoReferenceChildName(MdoReference ref) {
    var qualifiedName = ref.getMdoRefRu();
    if (qualifiedName.isBlank()) {
      qualifiedName = ref.getMdoRef();
    }
    if (qualifiedName.isBlank()) {
      return null;
    }
    var dot = qualifiedName.lastIndexOf('.');
    var bare = dot < 0 ? qualifiedName : qualifiedName.substring(dot + 1);
    var mdoTypeRu = ref.getType().fullName().getRu();
    if (!mdoTypeRu.isBlank()) {
      return ChildName.withReturnType(bare, "ОбъектМетаданных: " + mdoTypeRu + "." + bare);
    }
    return ChildName.of(bare);
  }

  static List<ChildName> attributesFor(MD md) {
    return md instanceof AttributeOwner ao
      ? customAttributeNames(ao.getAllAttributes())
      : List.of();
  }

  static List<ChildName> tabularSectionsFor(MD md) {
    return md instanceof TabularSectionOwner ts ? tabularSectionEntries(ts.getTabularSections()) : List.of();
  }

  static List<ChildName> formsFor(MD md) {
    return md instanceof FormOwner fo ? singleLingualMdNames(fo.getForms()) : List.of();
  }

  static List<ChildName> templatesFor(MD md) {
    return md instanceof TemplateOwner to ? singleLingualMdNames(to.getTemplates()) : List.of();
  }

  static List<ChildName> commandsFor(MD md) {
    return md instanceof CommandOwner co ? singleLingualMdNames(co.getCommands()) : List.of();
  }

  static List<ChildName> recalculationsFor(MD md) {
    return md instanceof CalculationRegister cr ? singleLingualMdNames(cr.getRecalculations()) : List.of();
  }

  static List<ChildName> journalColumnsFor(MD md) {
    return md instanceof DocumentJournal dj ? singleLingualMdNames(dj.getColumns()) : List.of();
  }

  static List<ChildName> enumValuesFor(MD md) {
    return md instanceof Enum e ? singleLingualMdNames(e.getEnumValues()) : List.of();
  }

  static List<ChildName> accountingFlagsFor(MD md) {
    return md instanceof ChartOfAccounts coa ? singleLingualMdNames(coa.getAccountingFlags()) : List.of();
  }

  static List<ChildName> extDimensionAccountingFlagsFor(MD md) {
    return md instanceof ChartOfAccounts coa
      ? singleLingualMdNames(coa.getExtDimensionAccountingFlags()) : List.of();
  }

  static List<ChildName> addressingAttributesFor(MD md) {
    return md instanceof Task t ? singleLingualMdNames(t.getAddressingAttributes()) : List.of();
  }

  static List<ChildName> registerRecordsFor(MD md) {
    return md instanceof Document doc ? mdoReferenceNames(doc.getRegisterRecords()) : List.of();
  }

  /**
   * Типы, на основании которых вводится объект ({@code ВводитсяНаОсновании}).
   * <p>
   * Интерфейса-владельца у свойства нет, поэтому виды объектов перечислены здесь
   * (mdclasses#677). Проверять приходится каждый: свойство есть у восьми классов.
   */
  static List<ChildName> basedOnFor(MD md) {
    return mdoReferenceNames(switch (md) {
      case Catalog o -> o.getBasedOn();
      case Document o -> o.getBasedOn();
      case BusinessProcess o -> o.getBasedOn();
      case Task o -> o.getBasedOn();
      case ChartOfAccounts o -> o.getBasedOn();
      case ChartOfCharacteristicTypes o -> o.getBasedOn();
      case ChartOfCalculationTypes o -> o.getBasedOn();
      case ExchangePlan o -> o.getBasedOn();
      default -> List.of();
    });
  }

  /** Поля, по которым доступен ввод по строке ({@code ВводПоСтроке}). */
  static List<ChildName> inputByStringFor(MD md) {
    return mdoReferenceNames(switch (md) {
      case Catalog o -> o.getInputByString();
      case Document o -> o.getInputByString();
      case BusinessProcess o -> o.getInputByString();
      case Task o -> o.getInputByString();
      case ChartOfAccounts o -> o.getInputByString();
      case ChartOfCharacteristicTypes o -> o.getInputByString();
      case ChartOfCalculationTypes o -> o.getInputByString();
      case ExchangePlan o -> o.getInputByString();
      default -> List.of();
    });
  }

  /** Поля блокировки данных ({@code ПоляБлокировкиДанных}). */
  static List<ChildName> dataLockFieldsFor(MD md) {
    return mdoReferenceNames(switch (md) {
      case Catalog o -> o.getDataLockFields();
      case Document o -> o.getDataLockFields();
      case BusinessProcess o -> o.getDataLockFields();
      case Task o -> o.getDataLockFields();
      case ChartOfAccounts o -> o.getDataLockFields();
      case ChartOfCharacteristicTypes o -> o.getDataLockFields();
      case ChartOfCalculationTypes o -> o.getDataLockFields();
      case ExchangePlan o -> o.getDataLockFields();
      default -> List.of();
    });
  }

  /**
   * Дополнительные индексы объекта ({@code ДополнительныеИндексы}) — в отличие от
   * прочих коллекций здесь у элемента собственное имя, а не имя объекта метаданных.
   */
  static List<ChildName> additionalIndexesFor(MD md) {
    return additionalIndexNames(switch (md) {
      case Catalog o -> o.getAdditionalIndexes();
      case Document o -> o.getAdditionalIndexes();
      case DocumentJournal o -> o.getAdditionalIndexes();
      case BusinessProcess o -> o.getAdditionalIndexes();
      case Task o -> o.getAdditionalIndexes();
      case ChartOfAccounts o -> o.getAdditionalIndexes();
      case ChartOfCharacteristicTypes o -> o.getAdditionalIndexes();
      case ChartOfCalculationTypes o -> o.getAdditionalIndexes();
      case ExchangePlan o -> o.getAdditionalIndexes();
      case InformationRegister o -> o.getAdditionalIndexes();
      case AccumulationRegister o -> o.getAdditionalIndexes();
      case AccountingRegister o -> o.getAdditionalIndexes();
      case CalculationRegister o -> o.getAdditionalIndexes();
      case Sequence o -> o.getAdditionalIndexes();
      default -> List.of();
    });
  }

  private static List<ChildName> additionalIndexNames(Collection<AdditionalIndex> indexes) {
    var result = new ArrayList<ChildName>(indexes.size());
    for (var index : indexes) {
      var entry = ChildName.of(index.getName());
      if (entry != null) {
        result.add(entry);
      }
    }
    return List.copyOf(result);
  }

}
