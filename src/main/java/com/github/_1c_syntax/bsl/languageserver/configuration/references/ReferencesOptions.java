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
package com.github._1c_syntax.bsl.languageserver.configuration.references;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Настройки для построения индекса ссылок.
 * <p>
 * Позволяет указать список модулей и методов, возвращающих ссылку на общий модуль
 * (например, ОбщегоНазначения.ОбщийМодуль("ИмяМодуля")).
 */
@Data
@AllArgsConstructor(onConstructor_ = {@JsonCreator(mode = JsonCreator.Mode.DISABLED)})
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReferencesOptions {

  /**
   * Список паттернов "Модуль.Метод" для методов, возвращающих ссылку на общий модуль.
   * <p>
   * Формат: "ИмяМодуля.ИмяМетода", например:
   * <ul>
   *   <li>"ОбщегоНазначения.ОбщийМодуль"</li>
   *   <li>"ОбщегоНазначенияКлиент.ОбщийМодуль"</li>
   *   <li>"CommonUse.CommonModule"</li>
   *   <li>"ОбщийМодуль" - для локального вызова без указания модуля</li>
   * </ul>
   * <p>
   * По умолчанию включает стандартные варианты из БСП.
   */
  private List<String> commonModuleAccessors = new ArrayList<>(List.of(
    // Локальный вызов
    "ОбщийМодуль",
    "CommonModule",
    // Стандартные модули БСП
    "ОбщегоНазначения.ОбщийМодуль",
    "ОбщегоНазначенияКлиент.ОбщийМодуль",
    "ОбщегоНазначенияСервер.ОбщийМодуль",
    "ОбщегоНазначенияКлиентСервер.ОбщийМодуль",
    "ОбщегоНазначенияПовтИсп.ОбщийМодуль",
    // Английские варианты
    "CommonUse.CommonModule",
    "CommonUseClient.CommonModule",
    "CommonUseServer.CommonModule",
    "CommonUseClientServer.CommonModule"
  ));
}
