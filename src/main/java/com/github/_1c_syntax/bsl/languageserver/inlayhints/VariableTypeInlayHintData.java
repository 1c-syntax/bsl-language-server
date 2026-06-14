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
package com.github._1c_syntax.bsl.languageserver.inlayhints;

import com.github._1c_syntax.bsl.languageserver.databind.URITypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.net.URI;

/**
 * Данные хинта типа переменной для отложенного построения tooltip и ссылки
 * части метки через {@code inlayHint/resolve}. Кладётся в {@code InlayHint.data}
 * при жадном расчёте хинтов и восстанавливается JSON round-trip'ом на резолве.
 * <p>
 * Поле {@code typeName} — каноническое имя выведенного типа для восстановления
 * описания (tooltip). Поля {@code targetUri} и четвёрка
 * {@code startLine}/{@code startCharacter}/{@code endLine}/{@code endCharacter} —
 * координаты объявления типа в исходнике (общий модуль, модуль менеджера объекта,
 * класс/модуль OneScript) для ленивого построения ссылки части метки. Если у типа
 * нет объявляющего исходник-символа (платформенный/примитивный тип), {@code targetUri}
 * пуст, а координаты равны {@code -1} — ссылка не строится.
 */
@Value
@NonFinal
public class VariableTypeInlayHintData implements InlayHintData {

  /** Координата-заполнитель для хинта без ссылки на объявление типа. */
  public static final int NO_LOCATION = -1;

  @JsonAdapter(URITypeAdapter.class)
  URI uri;
  String id;
  String typeName;
  String targetUri;
  int startLine;
  int startCharacter;
  int endLine;
  int endCharacter;

  /**
   * Есть ли у хинта координаты объявления типа для построения ссылки части метки.
   *
   * @return {@code true}, если {@code targetUri} непуст — координаты заполнены.
   */
  public boolean hasLocation() {
    return !targetUri.isBlank();
  }
}
