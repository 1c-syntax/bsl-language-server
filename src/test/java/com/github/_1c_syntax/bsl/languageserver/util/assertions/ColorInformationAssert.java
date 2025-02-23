/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
import org.eclipse.lsp4j.Color;
import org.eclipse.lsp4j.ColorInformation;
import org.eclipse.lsp4j.Range;

public class ColorInformationAssert extends AbstractAssert<ColorInformationAssert, ColorInformation> {

  public ColorInformationAssert(ColorInformation actual) {
    super(actual, ColorInformationAssert.class);
  }

  public static ColorInformationAssert assertThat(ColorInformation actual) {
    return new ColorInformationAssert(actual);
  }

  public ColorInformationAssert hasRange(Range expected) {
    // check that actual TolkienCharacter we want to make assertions on is not null.
    isNotNull();

    // check condition
    if (!(actual.getRange().equals(expected))) {
      failWithMessage(
        "Expected range to be %s but was %s",
        expected,
        actual.getRange()
      );
    }

    // return the current assertion for method chaining
    return this;
  }

  public ColorInformationAssert hasColor(Color expected) {
    // check that actual TolkienCharacter we want to make assertions on is not null.
    isNotNull();

    // check condition
    if (!actual.getColor().equals(expected)) {
      failWithMessage(
        "Expected color to be <%s> but was <%s>",
        expected,
        actual.getColor()
      );
    }

    // return the current assertion for method chaining
    return this;
  }

  public ColorInformationAssert hasColorAndRange(Color color, Range range) {
    return hasColor(color).hasRange(range);
  }
}
