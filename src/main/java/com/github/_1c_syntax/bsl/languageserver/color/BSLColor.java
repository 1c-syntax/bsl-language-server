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
package com.github._1c_syntax.bsl.languageserver.color;

import lombok.experimental.UtilityClass;

/**
 * Служебный класс для констант по работе с объектом {@code Цвет}.
 */
@UtilityClass
public class BSLColor {
  /**
   * Альфа-канал цвета по умолчанию как значение из диапазона [0...1].
   */
  public static final double DEFAULT_ALPHA_CHANNEL = 1.0;

  /**
   * Максимальное значение компонента цвета (диапазон [0...255]).
   */
  public static final int MAX_COLOR_COMPONENT_VALUE = 255;
}
