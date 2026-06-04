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

import com.github._1c_syntax.bsl.mdo.Constant;
import com.github._1c_syntax.bsl.mdo.MD;
import org.junit.jupiter.api.Test;

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
}
