/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import org.eclipse.lsp4j.Range;

import java.net.URI;

/**
 * Месторасположение появления символа.
 */
@Value
@AllArgsConstructor
@Builder
public class Location {

  /**
   * URI файла, в котором расположен символ.
   */
  URI uri;

  int startLine;
  int startCharacter;
  int endLine;
  int endCharacter;

  public Location(URI uri, Range range) {
    this.uri = uri;
    var start = range.getStart();
    var end = range.getEnd();
    startLine = start.getLine();
    startCharacter = start.getCharacter();
    endLine = end.getLine();
    endCharacter = end.getCharacter();

  }
  
  public Range getRange() {
    return Ranges.create(startLine, startCharacter, endLine, endCharacter);
  }
}
