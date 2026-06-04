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
import com.github._1c_syntax.bsl.mdo.AttributeOwner;
import com.github._1c_syntax.bsl.mdo.CalculationRegister;
import com.github._1c_syntax.bsl.mdo.ChartOfAccounts;
import com.github._1c_syntax.bsl.mdo.CommandOwner;
import com.github._1c_syntax.bsl.mdo.Document;
import com.github._1c_syntax.bsl.mdo.DocumentJournal;
import com.github._1c_syntax.bsl.mdo.Enum;
import com.github._1c_syntax.bsl.mdo.FormOwner;
import com.github._1c_syntax.bsl.mdo.InformationRegister;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.TabularSectionOwner;
import com.github._1c_syntax.bsl.mdo.Task;
import com.github._1c_syntax.bsl.mdo.TemplateOwner;

/**
 * Предикаты {@code is<XXX>(MD)} для applies-to в {@code CollectionSpec}.
 * Вынесено из {@link MetadataChildrenExtractor} чтобы снизить его coupling.
 */
final class MetadataMdoPredicates {

  private MetadataMdoPredicates() {
  }

  static boolean isRegister(MD md) {
    return md instanceof InformationRegister
      || md instanceof AccumulationRegister
      || md instanceof AccountingRegister
      || md instanceof CalculationRegister;
  }

  static boolean isAttributeOwner(MD md) {
    return md instanceof AttributeOwner;
  }

  static boolean isTabularSectionOwner(MD md) {
    return md instanceof TabularSectionOwner;
  }

  static boolean isFormOwner(MD md) {
    return md instanceof FormOwner;
  }

  static boolean isTemplateOwner(MD md) {
    return md instanceof TemplateOwner;
  }

  static boolean isCommandOwner(MD md) {
    return md instanceof CommandOwner;
  }

  static boolean isCalculationRegister(MD md) {
    return md instanceof CalculationRegister;
  }

  static boolean isDocumentJournal(MD md) {
    return md instanceof DocumentJournal;
  }

  static boolean isEnum(MD md) {
    return md instanceof Enum;
  }

  static boolean isChartOfAccounts(MD md) {
    return md instanceof ChartOfAccounts;
  }

  static boolean isTask(MD md) {
    return md instanceof Task;
  }

  static boolean isDocument(MD md) {
    return md instanceof Document;
  }
}
