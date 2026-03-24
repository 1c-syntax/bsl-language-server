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
package com.github._1c_syntax.bsl.languageserver.semantictokens;

import lombok.experimental.UtilityClass;

/**
 * Пользовательские типы семантических токенов, не входящие в стандарт LSP.
 * <p>
 * Эти типы расширяют стандартный набор {@link org.eclipse.lsp4j.SemanticTokenTypes}
 * для решения специфических задач подсветки синтаксиса.
 */
@UtilityClass
public class CustomSemanticTokenTypes {

  /**
   * Нейтральный тип токена для переопределения TextMate-скоупа строки.
   * <p>
   * Используется в телах лямбда-выражений для промежутков между семантическими
   * токенами (идентификаторы, пробелы, точки и пр.), где TextMate по умолчанию
   * применяет {@code string.quoted.double.bsl}, окрашивая код в цвет строки.
   * Поскольку этот тип не распознаётся редактором, токены отображаются
   * стандартным цветом текста.
   */
  public final String SOURCE = "source";

  /**
   * Тип токена для экранированных двойных кавычек {@code ""} внутри строковых литералов.
   * <p>
   * В BSL двойная кавычка внутри строки экранируется удвоением: {@code ""}.
   * Этот тип позволяет отображать экранирование аналогично escape-последовательностям
   * ({@code \n}, {@code \t}) в других языках — с цветом {@code constant.character.escape}
   * в TextMate-темах.
   */
  public final String STRING_ESCAPE = "stringEscape";
}
