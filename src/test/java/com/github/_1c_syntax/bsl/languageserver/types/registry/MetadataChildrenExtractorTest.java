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
import com.github._1c_syntax.bsl.mdo.BusinessProcess;
import com.github._1c_syntax.bsl.mdo.CalculationRegister;
import com.github._1c_syntax.bsl.mdo.Catalog;
import com.github._1c_syntax.bsl.mdo.ChartOfAccounts;
import com.github._1c_syntax.bsl.mdo.ChartOfCalculationTypes;
import com.github._1c_syntax.bsl.mdo.ChartOfCharacteristicTypes;
import com.github._1c_syntax.bsl.mdo.Constant;
import com.github._1c_syntax.bsl.mdo.Document;
import com.github._1c_syntax.bsl.mdo.DocumentJournal;
import com.github._1c_syntax.bsl.mdo.ExchangePlan;
import com.github._1c_syntax.bsl.mdo.InformationRegister;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.Sequence;
import com.github._1c_syntax.bsl.mdo.Task;
import com.github._1c_syntax.bsl.mdo.storage.AdditionalIndex;
import com.github._1c_syntax.bsl.types.MdoReference;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static com.github._1c_syntax.bsl.languageserver.types.registry.MetadataCollectionSpecializer.ChildName;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Negative-cases для {@link MetadataChildrenExtractor}: MD-объекты, не
 * реализующие соответствующие интерфейсы, должны давать пустой результат.
 */
class MetadataChildrenExtractorTest {

  private static final MD NON_OWNER = Constant.builder().name("Х").build();

  @Test
  void attributesFor_nonAttributeOwner_returnsEmpty() {
    assertThat(MetadataChildrenExtractor.attributesFor(NON_OWNER)).isEmpty();
  }

  @Test
  void formsFor_nonFormOwner_returnsEmpty() {
    assertThat(MetadataChildrenExtractor.formsFor(NON_OWNER)).isEmpty();
  }

  @Test
  void templatesFor_nonTemplateOwner_returnsEmpty() {
    assertThat(MetadataChildrenExtractor.templatesFor(NON_OWNER)).isEmpty();
  }

  @Test
  void commandsFor_nonCommandOwner_returnsEmpty() {
    assertThat(MetadataChildrenExtractor.commandsFor(NON_OWNER)).isEmpty();
  }

  @Test
  void tabularSectionsFor_nonOwner_returnsEmpty() {
    assertThat(MetadataChildrenExtractor.tabularSectionsFor(NON_OWNER)).isEmpty();
  }

  @Test
  void recalculationsFor_nonCalcRegister_returnsEmpty() {
    assertThat(MetadataChildrenExtractor.recalculationsFor(NON_OWNER)).isEmpty();
  }

  @Test
  void journalColumnsFor_nonJournal_returnsEmpty() {
    assertThat(MetadataChildrenExtractor.journalColumnsFor(NON_OWNER)).isEmpty();
  }

  @Test
  void enumValuesFor_nonEnum_returnsEmpty() {
    assertThat(MetadataChildrenExtractor.enumValuesFor(NON_OWNER)).isEmpty();
  }

  @Test
  void accountingFlagsFor_nonChartOfAccounts_returnsEmpty() {
    assertThat(MetadataChildrenExtractor.accountingFlagsFor(NON_OWNER)).isEmpty();
  }

  @Test
  void extDimensionAccountingFlagsFor_nonChartOfAccounts_returnsEmpty() {
    assertThat(MetadataChildrenExtractor.extDimensionAccountingFlagsFor(NON_OWNER)).isEmpty();
  }

  @Test
  void addressingAttributesFor_nonTask_returnsEmpty() {
    assertThat(MetadataChildrenExtractor.addressingAttributesFor(NON_OWNER)).isEmpty();
  }

  @Test
  void registerRecordsFor_nonDocument_returnsEmpty() {
    assertThat(MetadataChildrenExtractor.registerRecordsFor(NON_OWNER)).isEmpty();
  }

  // === свойства, у которых в mdclasses нет интерфейса-владельца (mdclasses#677) ===

  private static final MdoReference DOCUMENT_REF = MdoReference.create("Document.Документ1");
  private static final MdoReference FIELD_REF = MdoReference.create("Catalog.Справочник1.Attribute.Реквизит1");

  /**
   * Виды объектов перечисляются в коде поимённо, потому что интерфейса-владельца у
   * свойства нет. Такое перечисление легко разъезжается с моделью — поэтому проверяется
   * каждая ветка, а не одна показательная.
   */
  @Test
  void basedOnIsReadFromEveryKindThatHasIt() {
    assertThat(extractedNames(MetadataChildrenExtractor::basedOnFor, List.of(
      Catalog.builder().name("С").addBasedOn(DOCUMENT_REF).build(),
      Document.builder().name("Д").addBasedOn(DOCUMENT_REF).build(),
      BusinessProcess.builder().name("БП").addBasedOn(DOCUMENT_REF).build(),
      Task.builder().name("З").addBasedOn(DOCUMENT_REF).build(),
      ChartOfAccounts.builder().name("ПС").addBasedOn(DOCUMENT_REF).build(),
      ChartOfCharacteristicTypes.builder().name("ПВХ").addBasedOn(DOCUMENT_REF).build(),
      ChartOfCalculationTypes.builder().name("ПВР").addBasedOn(DOCUMENT_REF).build(),
      ExchangePlan.builder().name("ПО").addBasedOn(DOCUMENT_REF).build())))
      .containsOnly("Документ1");
    assertThat(MetadataChildrenExtractor.basedOnFor(NON_OWNER)).isEmpty();
  }

  @Test
  void inputByStringIsReadFromEveryKindThatHasIt() {
    assertThat(extractedNames(MetadataChildrenExtractor::inputByStringFor, List.of(
      Catalog.builder().name("С").addInputByString(FIELD_REF).build(),
      Document.builder().name("Д").addInputByString(FIELD_REF).build(),
      BusinessProcess.builder().name("БП").addInputByString(FIELD_REF).build(),
      Task.builder().name("З").addInputByString(FIELD_REF).build(),
      ChartOfAccounts.builder().name("ПС").addInputByString(FIELD_REF).build(),
      ChartOfCharacteristicTypes.builder().name("ПВХ").addInputByString(FIELD_REF).build(),
      ChartOfCalculationTypes.builder().name("ПВР").addInputByString(FIELD_REF).build(),
      ExchangePlan.builder().name("ПО").addInputByString(FIELD_REF).build())))
      .containsOnly("Реквизит1");
    assertThat(MetadataChildrenExtractor.inputByStringFor(NON_OWNER)).isEmpty();
  }

  @Test
  void dataLockFieldsAreReadFromEveryKindThatHasThem() {
    assertThat(extractedNames(MetadataChildrenExtractor::dataLockFieldsFor, List.of(
      Catalog.builder().name("С").addDataLockFields(FIELD_REF).build(),
      Document.builder().name("Д").addDataLockFields(FIELD_REF).build(),
      BusinessProcess.builder().name("БП").addDataLockFields(FIELD_REF).build(),
      Task.builder().name("З").addDataLockFields(FIELD_REF).build(),
      ChartOfAccounts.builder().name("ПС").addDataLockFields(FIELD_REF).build(),
      ChartOfCharacteristicTypes.builder().name("ПВХ").addDataLockFields(FIELD_REF).build(),
      ChartOfCalculationTypes.builder().name("ПВР").addDataLockFields(FIELD_REF).build(),
      ExchangePlan.builder().name("ПО").addDataLockFields(FIELD_REF).build())))
      .containsOnly("Реквизит1");
    assertThat(MetadataChildrenExtractor.dataLockFieldsFor(NON_OWNER)).isEmpty();
  }

  @Test
  void additionalIndexesAreReadFromEveryKindThatHasThem() {
    var index = AdditionalIndex.builder().name("Индекс1").build();
    assertThat(extractedNames(MetadataChildrenExtractor::additionalIndexesFor, List.of(
      Catalog.builder().name("С").addAdditionalIndex(index).build(),
      Document.builder().name("Д").addAdditionalIndex(index).build(),
      DocumentJournal.builder().name("ЖД").addAdditionalIndex(index).build(),
      BusinessProcess.builder().name("БП").addAdditionalIndex(index).build(),
      Task.builder().name("З").addAdditionalIndex(index).build(),
      ChartOfAccounts.builder().name("ПС").addAdditionalIndex(index).build(),
      ChartOfCharacteristicTypes.builder().name("ПВХ").addAdditionalIndex(index).build(),
      ChartOfCalculationTypes.builder().name("ПВР").addAdditionalIndex(index).build(),
      ExchangePlan.builder().name("ПО").addAdditionalIndex(index).build(),
      InformationRegister.builder().name("РС").addAdditionalIndex(index).build(),
      AccumulationRegister.builder().name("РН").addAdditionalIndex(index).build(),
      AccountingRegister.builder().name("РБ").addAdditionalIndex(index).build(),
      CalculationRegister.builder().name("РР").addAdditionalIndex(index).build(),
      Sequence.builder().name("П").addAdditionalIndex(index).build())))
      .as("у индекса собственное имя, а не имя объекта метаданных")
      .containsOnly("Индекс1");
    assertThat(MetadataChildrenExtractor.additionalIndexesFor(NON_OWNER)).isEmpty();
  }

  @Test
  void additionalIndexWithoutNameIsSkipped() {
    var catalog = Catalog.builder().name("С")
      .addAdditionalIndex(AdditionalIndex.builder().name("").build())
      .build();
    assertThat(MetadataChildrenExtractor.additionalIndexesFor(catalog)).isEmpty();
  }

  /** Имена, извлечённые из каждого объекта; ожидается ровно по одному на объект. */
  private static List<String> extractedNames(Function<MD, List<ChildName>> extractor,
                                             List<? extends MD> objects) {
    return objects.stream()
      .peek(md -> assertThat(extractor.apply(md))
        .as("вид %s", md.getClass().getSimpleName())
        .hasSize(1))
      .flatMap(md -> extractor.apply(md).stream())
      .map(child -> child.name().ru())
      .toList();
  }
}
