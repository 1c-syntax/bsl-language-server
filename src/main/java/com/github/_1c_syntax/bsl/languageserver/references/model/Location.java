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

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.net.URI;

/**
 * Месторасположение появления символа.
 *
 * @param uri            URI файла, в котором расположен символ.
 * @param startLine      Строка, в которой начинается символ.
 * @param startCharacter Столбец, в котором начинается символ.
 * @param endLine        Строка, в которой заканчивается символ.
 * @param endCharacter   Столбец, в котором заканчивается символ.
 */
public record Location(URI uri, int startLine, int startCharacter, int endLine, int endCharacter) {

  public Location(URI uri, Range range) {
    this(uri, range.getStart().getLine(), range.getStart().getCharacter(), range.getEnd().getLine(), range.getEnd().getCharacter());
  }

  public Range getRange() {
    return Ranges.create(startLine, startCharacter, endLine, endCharacter);
  }

  public Position getStart() {
    return new Position(startLine, startCharacter);
  }
}
