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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import lombok.experimental.UtilityClass;

import java.util.function.Function;
import java.util.stream.Collectors;

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
  /**
   * Дописать в {@code sb} строку параметра целиком: имя и тип ({@link #appendNameAndType}),
   * значение по умолчанию ({@code _= Значение_}) и описание ({@code — Описание}), если они есть,
   * и перевод строки.
   *
   * @param displayName имя параметра.
   * @param typesLabel  готовая markdown-метка типов (может быть пустой).
   * @param parameter   параметр.
   * @param language    язык описания.
   */
  void appendParameter(
    StringBuilder sb,
    String displayName,
    String typesLabel,
    ParameterDescriptor parameter,
    Language language
  ) {
    appendNameAndType(sb, displayName, typesLabel, parameter.optional());
    if (!parameter.defaultValue().isBlank()) {
      sb.append(" _= ").append(parameter.defaultValue()).append('_');
    }
    var description = parameter.displayDescription(language);
    if (description != null && !description.isBlank()) {
      sb.append(" — ").append(description);
    }
    sb.append('\n');
  }

  /**
   * Метка набора типов: отображаемые имена через {@code " | "}, пустые имена пропускаются.
   *
   * @param types       набор типов.
   * @param displayName отображаемое имя типа.
   * @return метка; пустая строка для пустого набора.
   */
  String typesLabel(TypeSet types, Function<TypeRef, String> displayName) {
    return types.refs().stream()
      .map(displayName)
      .filter(name -> name != null && !name.isEmpty())
      .collect(Collectors.joining(" | "));
  }

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
