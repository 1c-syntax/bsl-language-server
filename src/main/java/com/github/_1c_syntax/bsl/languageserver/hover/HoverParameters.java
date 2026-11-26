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
package com.github._1c_syntax.bsl.languageserver.hover;

import lombok.experimental.UtilityClass;

/**
 * Общие хелперы отрисовки параметров в hover-карточках платформенных членов
 * и конструкторов.
 */
@UtilityClass
class HoverParameters {

  /**
   * Дописать в {@code sb} markdown-пункт параметра вида
   * {@code - `Имя`: Тип} с пометкой необязательности «?».
   * Знак приклеивается к типу ({@code `Имя`: Тип?}), а при отсутствии типа —
   * к имени ({@code `Имя?`}).
   *
   * @param displayName имя параметра.
   * @param typesLabel  готовая markdown-метка типов (может быть пустой).
   * @param optional    необязательный ли параметр.
   */
  void appendNameAndType(StringBuilder sb, String displayName, String typesLabel, boolean optional) {
    var optionalMark = optional ? "?" : "";
    sb.append("- `").append(displayName);
    if (typesLabel.isEmpty()) {
      sb.append(optionalMark).append('`');
    } else {
      sb.append("`: ").append(typesLabel).append(optionalMark);
    }
  }
}
