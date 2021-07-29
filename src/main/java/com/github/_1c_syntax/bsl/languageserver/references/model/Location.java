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
package com.github._1c_syntax.bsl.languageserver.references.model;

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.eclipse.lsp4j.Range;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embeddable;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.net.URI;

@Embeddable
@Table(indexes = @Index(columnList = "uri"))
@Getter
@Setter
@NoArgsConstructor
public class Location {

  @Convert(converter = URIAttributeConverter.class)
  @Column(columnDefinition = "LONGVARCHAR")
  private URI uri;

  private int startLine;
  private int startCharacter;
  private int endLine;
  private int endCharacter;

  @Transient
  public Range getRange() {
    return Ranges.create(startLine, startCharacter, endLine, endCharacter);
  }

  public void setRange(Range range) {
    startLine = range.getStart().getLine();
    startCharacter = range.getStart().getCharacter();
    endLine = range.getEnd().getLine();
    endCharacter = range.getEnd().getCharacter();
  }

}
