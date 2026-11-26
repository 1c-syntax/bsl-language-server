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
package com.github._1c_syntax.bsl.languageserver.util.assertions;

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.assertj.core.api.AbstractAssert;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SelectionRange;

import java.util.Objects;

public class SelectionRangeAssert extends AbstractAssert<SelectionRangeAssert, SelectionRange> {

  public SelectionRangeAssert(SelectionRange actual) {
    super(actual, SelectionRangeAssert.class);
  }

  public static SelectionRangeAssert assertThat(SelectionRange actual) {
    return new SelectionRangeAssert(actual);
  }

  public SelectionRangeAssert hasRange(int startLine, int startChar, int endLine, int endChar) {
    return hasRange(Ranges.create(startLine, startChar, endLine, endChar));
  }

  public SelectionRangeAssert hasRange(int startLine, int startChar, int endChar) {
    return hasRange(Ranges.create(startLine, startChar, endChar));
  }

  public SelectionRangeAssert hasRange(Range expectedRange) {
    isNotNull();

    // check condition
    Range actualRange = actual.getRange();
    if (!Objects.equals(actualRange, expectedRange)) {
      failWithMessage("Expected selection range to be <%s> but was <%s>", expectedRange.toString(), actualRange.toString());
    }

    // return the current assertion for method chaining
    return this;
  }

  public SelectionRangeAssert hasParentWithRange(int startLine, int startChar, int endLine, int endChar) {
    isNotNull();

    // check condition
    Range expectedRange = Ranges.create(startLine, startChar, endLine, endChar);
    Range actualRange = actual.getParent().getRange();
    if (!Objects.equals(actualRange, expectedRange)) {
      failWithMessage("Expected parent selection range to be <%s> but was <%s>", expectedRange.toString(), actualRange.toString());
    }

    // return the current assertion for method chaining
    return this;
  }

  public SelectionRangeAssert hasParentWithRange(int startLine, int startChar, int endChar) {
    return hasParentWithRange(startLine, startChar, startLine, endChar);
  }

  public SelectionRangeAssert extractParent() {
    return new SelectionRangeAssert(actual.getParent());
  }

}
