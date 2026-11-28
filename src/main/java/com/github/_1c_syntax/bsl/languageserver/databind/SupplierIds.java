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
package com.github._1c_syntax.bsl.languageserver.databind;

import lombok.experimental.UtilityClass;

import java.beans.Introspector;

/**
 * Идентификаторы поставщиков (code lens, inlay hints, команд).
 * <p>
 * Идентификатор — ключ поставщика в разделе {@code parameters} конфигурации и значение поля
 * {@code id} в данных линзы, подсказки или аргументах команды, по которому эти данные
 * разбираются в свой класс.
 */
@UtilityClass
public class SupplierIds {

  /**
   * Идентификатор поставщика по имени его класса: имя без суффикса роли, с маленькой буквы
   * ({@code CognitiveComplexityCodeLensSupplier} → {@code cognitiveComplexity}). Класс без
   * суффикса роли получает своё простое имя как есть.
   *
   * @param supplierClass Класс поставщика
   * @param roleSuffix    Суффикс роли в имени класса, например {@code CodeLensSupplier}
   * @return Идентификатор поставщика
   */
  public static String of(Class<?> supplierClass, String roleSuffix) {
    var simpleName = supplierClass.getSimpleName();
    if (!simpleName.endsWith(roleSuffix)) {
      return simpleName;
    }
    return Introspector.decapitalize(simpleName.substring(0, simpleName.length() - roleSuffix.length()));
  }
}
