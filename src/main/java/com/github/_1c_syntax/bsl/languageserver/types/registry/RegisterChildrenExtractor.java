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
import com.github._1c_syntax.bsl.mdo.CalculationRegister;
import com.github._1c_syntax.bsl.mdo.InformationRegister;
import com.github._1c_syntax.bsl.mdo.MD;

import java.util.List;

/**
 * Извлечение измерений/ресурсов из конкретных видов регистров. Вынесено из
 * {@link MetadataChildrenExtractor} чтобы снизить coupling последнего.
 */
final class RegisterChildrenExtractor {

  private RegisterChildrenExtractor() {
  }

  /** Измерения регистра (InformationRegister/AccumulationRegister/AccountingRegister/CalculationRegister). */
  static List<MD> registerDimensions(MD md) {
    if (md instanceof InformationRegister r) {
      return r.getDimensions().stream().map(MD.class::cast).toList();
    }
    if (md instanceof AccumulationRegister r) {
      return r.getDimensions().stream().map(MD.class::cast).toList();
    }
    if (md instanceof AccountingRegister r) {
      return r.getDimensions().stream().map(MD.class::cast).toList();
    }
    if (md instanceof CalculationRegister r) {
      return r.getDimensions().stream().map(MD.class::cast).toList();
    }
    return List.of();
  }

  /** Ресурсы регистра. */
  static List<MD> registerResources(MD md) {
    if (md instanceof InformationRegister r) {
      return r.getResources().stream().map(MD.class::cast).toList();
    }
    if (md instanceof AccumulationRegister r) {
      return r.getResources().stream().map(MD.class::cast).toList();
    }
    if (md instanceof AccountingRegister r) {
      return r.getResources().stream().map(MD.class::cast).toList();
    }
    if (md instanceof CalculationRegister r) {
      return r.getResources().stream().map(MD.class::cast).toList();
    }
    return List.of();
  }
}
