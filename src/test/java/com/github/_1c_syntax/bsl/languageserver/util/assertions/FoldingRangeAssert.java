/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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
package com.github._1c_syntax.bsl.languageserver.util.assertions;

import org.assertj.core.api.AbstractAssert;
import org.eclipse.lsp4j.FoldingRange;

public class FoldingRangeAssert extends AbstractAssert<FoldingRangeAssert, FoldingRange> {

  public FoldingRangeAssert(FoldingRange actual) {
    super(actual, FoldingRangeAssert.class);
  }

  public static FoldingRangeAssert assertThat(FoldingRange actual) {
    return new FoldingRangeAssert(actual);
  }

  public FoldingRangeAssert hasRange(int startLine, int endLine) {
    // check that actual TolkienCharacter we want to make assertions on is not null.
    isNotNull();

    // check condition
    if (!(actual.getStartLine() == startLine && actual.getEndLine() == endLine)) {
      failWithMessage(
        "Expected folding range to be <%d,%d> but was <%d,%d>",
        startLine,
        endLine,
        actual.getStartLine(),
        actual.getEndLine()
      );
    }

    // return the current assertion for method chaining
    return this;
  }

  public FoldingRangeAssert hasKind(String kind) {
    // check that actual TolkienCharacter we want to make assertions on is not null.
    isNotNull();

    // check condition
    if (!actual.getKind().equals(kind)) {
      failWithMessage(
        "Expected folding kind to be <%s> but was <%s>",
        kind,
        actual.getKind()
      );
    }

    // return the current assertion for method chaining
    return this;
  }

  public FoldingRangeAssert hasKindAndRange(String kind, int startLine, int endLine) {
    return hasKind(kind).hasRange(startLine, endLine);
  }
}
