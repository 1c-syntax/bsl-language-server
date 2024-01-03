/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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
import org.eclipse.lsp4j.ColorPresentation;
import org.eclipse.lsp4j.TextEdit;

import java.util.Objects;

public class ColorPresentationAssert extends AbstractAssert<ColorPresentationAssert, ColorPresentation> {

  public ColorPresentationAssert(ColorPresentation actual) {
    super(actual, ColorPresentationAssert.class);
  }

  public static ColorPresentationAssert assertThat(ColorPresentation actual) {
    return new ColorPresentationAssert(actual);
  }

  public ColorPresentationAssert hasLabel(String expected) {
    // check that actual TolkienCharacter we want to make assertions on is not null.
    isNotNull();

    // check condition
    if (!(Objects.equals(actual.getLabel(), expected))) {
      failWithMessage(
        "Expected label to be %s but was %s",
        expected,
        actual.getLabel()
      );
    }

    // return the current assertion for method chaining
    return this;
  }

  public ColorPresentationAssert hasTextEdit(TextEdit expected) {
    // check that actual TolkienCharacter we want to make assertions on is not null.
    isNotNull();

    // check condition
    if (!Objects.equals(actual.getTextEdit(), expected)) {
      failWithMessage(
        "Expected textEdit to be <%s> but was <%s>",
        expected,
        actual.getTextEdit()
      );
    }

    // return the current assertion for method chaining
    return this;
  }

  public ColorPresentationAssert hasLabelAndTextEdit(String label, TextEdit textEdit) {
    return hasLabel(label).hasTextEdit(textEdit);
  }
}
