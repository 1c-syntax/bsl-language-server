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
package com.github._1c_syntax.bsl.languageserver.utils;

import lombok.experimental.UtilityClass;

import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Платформенные вызовы, которым обработчик передаётся <b>именем-строкой</b>:
 * {@code ПодключитьОбработчикОжидания("ОбновитьСписок", 60)},
 * {@code ПодключитьОбработчикИзмененияДанных("Объект.Дата", "ДатаИзменена", Истина)},
 * {@code Элементы.Товары.УстановитьДействие("ПриИзменении", "ТоварыПриИзменении")}.
 * <p>
 * Такой вызов — единственное обращение к процедуре во всём модуле, поэтому без его
 * разбора она выглядит никем не вызываемой, а переименование её не задевает.
 * {@code ОписаниеОповещения} устроено так же, но у него своя утилита
 * ({@link NotifyDescription}): там имён два и у каждого свой модуль.
 */
@UtilityClass
public class AttachedHandlers {

  /**
   * Имя вызова (в нижнем регистре, оба языка) → индекс параметра, в котором стоит имя
   * обработчика. Обработчик всегда объявлен в том же модуле, что и вызов: подключать
   * чужую процедуру платформа не даёт.
   */
  private static final Map<String, Integer> HANDLER_ARGUMENT_INDEX = Map.ofEntries(
    Map.entry("подключитьобработчикожидания", 0),
    Map.entry("attachidlehandler", 0),
    Map.entry("отключитьобработчикожидания", 0),
    Map.entry("detachidlehandler", 0),
    // Первым параметром идёт путь к данным, обработчик — вторым.
    Map.entry("подключитьобработчикизмененияданных", 1),
    Map.entry("attachdatachangehandler", 1),
    Map.entry("отключитьобработчикизмененияданных", 1),
    Map.entry("detachdatachangehandler", 1),
    // Первым параметром идёт имя события, обработчик — вторым.
    Map.entry("установитьдействие", 1),
    Map.entry("setaction", 1)
  );

  /**
   * Индекс параметра с именем обработчика.
   *
   * @param methodName имя вызываемого метода.
   * @return индекс параметра; пусто, если этот вызов обработчик по имени не подключает.
   */
  public static OptionalInt handlerArgumentIndex(String methodName) {
    var index = HANDLER_ARGUMENT_INDEX.get(methodName.toLowerCase(Locale.ROOT));
    return index == null ? OptionalInt.empty() : OptionalInt.of(index);
  }
}
