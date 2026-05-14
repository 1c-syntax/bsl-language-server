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
package com.github._1c_syntax.bsl.languageserver.types.symbol;

/**
 * Семантическая роль {@link SyntheticSymbol}.
 *
 * <p>
 * Synthetic-символы — это символы, объявление которых лежит вне BSL/OScript кода
 * (платформа, конфигурация, описание библиотеки). У них нет URI/Range, но они
 * полностью эквивалентны {@code SourceDefinedSymbol} с точки зрения hover /
 * completion / signature help / reference resolution.
 */
public enum SyntheticKind {
  /** Свойство глобальной области (например, {@code Справочники}, {@code Истина}). */
  PLATFORM_GLOBAL_PROPERTY,
  /** Метод глобальной области (например, {@code Сообщить}, {@code СтрНайти}). */
  PLATFORM_GLOBAL_METHOD,
  /** Свойство платформенного типа (например, {@code Массив.Количество}). */
  PLATFORM_MEMBER_PROPERTY,
  /** Метод платформенного типа (например, {@code Массив.Добавить}). */
  PLATFORM_MEMBER_METHOD,
  /**
   * Элемент коллекции конфигурации, имеющий собственное имя
   * (например, {@code Справочники.МойСправочник}).
   */
  CONFIGURATION_OBJECT
}
