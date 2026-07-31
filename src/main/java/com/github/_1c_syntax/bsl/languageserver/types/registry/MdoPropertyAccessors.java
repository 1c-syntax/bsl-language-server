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
import com.github._1c_syntax.bsl.mdo.Document;
import com.github._1c_syntax.bsl.mdo.DocumentJournal;
import com.github._1c_syntax.bsl.mdo.ExchangePlan;
import com.github._1c_syntax.bsl.mdo.InformationRegister;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.Sequence;
import com.github._1c_syntax.bsl.mdo.Task;
import com.github._1c_syntax.bsl.mdo.storage.AdditionalIndex;
import com.github._1c_syntax.bsl.types.MdoReference;

import java.util.List;

/**
 * Свойства объектов метаданных, у которых в mdclasses нет интерфейса-владельца.
 * <p>
 * `ВводитсяНаОсновании`, `ВводПоСтроке`, `ПоляБлокировкиДанных` объявлены полем в восьми
 * классах, `ДополнительныеИндексы` — в четырнадцати, и общего интерфейса вроде
 * {@code AttributeOwner} у них нет (mdclasses#677). Поэтому виды объектов приходится
 * перечислять — и это перечисление собрано здесь, чтобы не тащить полтора десятка
 * MD-классов в тех, кто просто хочет прочитать свойство.
 * <p>
 * Как только интерфейсы появятся, весь класс схлопывается в четыре
 * {@code instanceof}-проверки.
 */
final class MdoPropertyAccessors {

  private MdoPropertyAccessors() {
    // утилитный класс
  }

  /** Типы, на основании которых вводится объект; пусто — свойства у вида нет. */
  static List<MdoReference> basedOn(MD md) {
    return switch (md) {
      case Catalog o -> o.getBasedOn();
      case Document o -> o.getBasedOn();
      case BusinessProcess o -> o.getBasedOn();
      case Task o -> o.getBasedOn();
      case ChartOfAccounts o -> o.getBasedOn();
      case ChartOfCharacteristicTypes o -> o.getBasedOn();
      case ChartOfCalculationTypes o -> o.getBasedOn();
      case ExchangePlan o -> o.getBasedOn();
      default -> List.of();
    };
  }

  /** Поля, по которым доступен ввод по строке; пусто — свойства у вида нет. */
  static List<MdoReference> inputByString(MD md) {
    return switch (md) {
      case Catalog o -> o.getInputByString();
      case Document o -> o.getInputByString();
      case BusinessProcess o -> o.getInputByString();
      case Task o -> o.getInputByString();
      case ChartOfAccounts o -> o.getInputByString();
      case ChartOfCharacteristicTypes o -> o.getInputByString();
      case ChartOfCalculationTypes o -> o.getInputByString();
      case ExchangePlan o -> o.getInputByString();
      default -> List.of();
    };
  }

  /** Поля блокировки данных; пусто — свойства у вида нет. */
  static List<MdoReference> dataLockFields(MD md) {
    return switch (md) {
      case Catalog o -> o.getDataLockFields();
      case Document o -> o.getDataLockFields();
      case BusinessProcess o -> o.getDataLockFields();
      case Task o -> o.getDataLockFields();
      case ChartOfAccounts o -> o.getDataLockFields();
      case ChartOfCharacteristicTypes o -> o.getDataLockFields();
      case ChartOfCalculationTypes o -> o.getDataLockFields();
      case ExchangePlan o -> o.getDataLockFields();
      default -> List.of();
    };
  }

  /**
   * Дополнительные индексы; пусто — свойства у вида нет.
   * <p>
   * Видов четырнадцать, поэтому перебор разделён на две половины: одним switch'ем
   * он перевалил бы порог цикломатической сложности, а дробить его по-другому нечем —
   * ветки различаются только типом.
   */
  static List<AdditionalIndex> additionalIndexes(MD md) {
    var ofObject = additionalIndexesOfObject(md);
    return ofObject.isEmpty() ? additionalIndexesOfRegister(md) : ofObject;
  }

  private static List<AdditionalIndex> additionalIndexesOfObject(MD md) {
    return switch (md) {
      case Catalog o -> o.getAdditionalIndexes();
      case Document o -> o.getAdditionalIndexes();
      case DocumentJournal o -> o.getAdditionalIndexes();
      case BusinessProcess o -> o.getAdditionalIndexes();
      case Task o -> o.getAdditionalIndexes();
      case ChartOfAccounts o -> o.getAdditionalIndexes();
      case ChartOfCharacteristicTypes o -> o.getAdditionalIndexes();
      case ChartOfCalculationTypes o -> o.getAdditionalIndexes();
      case ExchangePlan o -> o.getAdditionalIndexes();
      default -> List.of();
    };
  }

  private static List<AdditionalIndex> additionalIndexesOfRegister(MD md) {
    return switch (md) {
      case InformationRegister o -> o.getAdditionalIndexes();
      case AccumulationRegister o -> o.getAdditionalIndexes();
      case AccountingRegister o -> o.getAdditionalIndexes();
      case CalculationRegister o -> o.getAdditionalIndexes();
      case Sequence o -> o.getAdditionalIndexes();
      default -> List.of();
    };
  }
}
