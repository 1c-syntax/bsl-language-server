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
package com.github._1c_syntax.bsl.languageserver.references.model;

import java.net.URI;

/**
 * Реализация {@link SymbolOccurrence} с координатами диапазона в {@code int}.
 * <p>
 * Используется для файлов, где строка или столбец не укладываются в {@code short}.
 * Обычно создаётся через фабрику {@link SymbolOccurrence#of}.
 *
 * @param occurrenceType Тип обращения к символу.
 * @param symbol         Символ, к которому происходит обращение.
 * @param uri            URI файла, в котором расположено обращение.
 * @param startLine      Строка начала обращения.
 * @param startCharacter Столбец начала обращения.
 * @param endLine        Строка конца обращения.
 * @param endCharacter   Столбец конца обращения.
 */
public record IntBasedSymbolOccurrence(
  OccurrenceType occurrenceType,
  Symbol symbol,
  URI uri,
  int startLine,
  int startCharacter,
  int endLine,
  int endCharacter
) implements SymbolOccurrence {
}
