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

import com.github._1c_syntax.bsl.mdo.Attribute;
import com.github._1c_syntax.bsl.mdo.AttributeOwner;
import com.github._1c_syntax.bsl.mdo.CalculationRegister;
import com.github._1c_syntax.bsl.mdo.ChartOfAccounts;
import com.github._1c_syntax.bsl.mdo.CommandOwner;
import com.github._1c_syntax.bsl.mdo.Document;
import com.github._1c_syntax.bsl.mdo.DocumentJournal;
import com.github._1c_syntax.bsl.mdo.Enum;
import com.github._1c_syntax.bsl.mdo.FormOwner;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.TabularSection;
import com.github._1c_syntax.bsl.mdo.TabularSectionOwner;
import com.github._1c_syntax.bsl.mdo.Task;
import com.github._1c_syntax.bsl.mdo.TemplateOwner;
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

}
