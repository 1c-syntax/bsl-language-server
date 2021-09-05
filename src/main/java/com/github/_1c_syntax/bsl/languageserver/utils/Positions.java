/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.eclipse.lsp4j.Position;

import java.io.LineNumberReader;
import java.io.StringReader;

@UtilityClass
public class Positions {

  @SneakyThrows
  public int getOffset(DocumentContext documentContext, Position position) {
    var content = documentContext.getContent();
    var lineNumberReader = new LineNumberReader(new StringReader(content), content.length());

    var line = position.getLine();
    var offset = 0;
    while (line > lineNumberReader.getLineNumber()) {
      var readLine = lineNumberReader.readLine();
      offset += readLine.length();
    }

    var character = position.getCharacter();
    var skipped = lineNumberReader.skip(character);
    if (skipped != character) {
      throw new IllegalStateException(); // todo: excp text
    }

    offset += character;

    return offset;
  }

  @SneakyThrows
  public Position getPosition(DocumentContext documentContext, int offset) {
    var content = documentContext.getContent();
    var lineNumberReader = new LineNumberReader(new StringReader(content), content.length());

    var skipped = lineNumberReader.skip(offset);

    if (skipped != offset) {
      throw new IllegalStateException(); // todo: excp text
    }

    var line = lineNumberReader.getLineNumber();
    // todo: dirty hack
    var offsetAtStartOfLine = getOffset(documentContext, new Position(line, 0));

    return new Position(line, offset - offsetAtStartOfLine);
  }

  public boolean isBefore(Position left, Position right) {
    return org.eclipse.lsp4j.util.Positions.isBefore(left, right);
  }
}
