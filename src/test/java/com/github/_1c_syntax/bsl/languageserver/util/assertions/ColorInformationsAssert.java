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

import org.assertj.core.api.AbstractListAssert;
import org.assertj.core.util.Lists;
import org.eclipse.lsp4j.Color;
import org.eclipse.lsp4j.ColorInformation;
import org.eclipse.lsp4j.Range;

import java.util.List;

public class ColorInformationsAssert extends AbstractListAssert<ColorInformationsAssert, List<ColorInformation>, ColorInformation, ColorInformationAssert> {

  private final ColorInformationAssertFactory assertFactory = new ColorInformationAssertFactory();

  public ColorInformationsAssert(List<ColorInformation> actual) {
    super(actual, ColorInformationsAssert.class);
  }

  /**
   * Ассерт для проверки совпадения цвета.
   *
   * @param color Цвет (см. {@link org.eclipse.lsp4j.Color}.
   * @return Ссылка на объект для текучести.
   */
  public ColorInformationsAssert hasColor(Color color) {

    return anySatisfy(colorInformation ->
      assertFactory.createAssert(colorInformation).hasColor(color)
    );

  }

  /**
   * Ассерт для проверки совпадения диапазона.
   *
   * @param range Диапазон (см. {@link org.eclipse.lsp4j.Range}.
   * @return Ссылка на объект для текучести.
   */
  public ColorInformationsAssert hasRange(Range range) {

    return anySatisfy(colorInformation ->
      assertFactory.createAssert(colorInformation).hasRange(range)
    );

  }

  /**
   * Ассерт для проверки совпадения цвета и диапазона.
   *
   * @param color Цвет (см. {@link org.eclipse.lsp4j.Color}.
   * @param range Диапазон (см. {@link org.eclipse.lsp4j.Range}.
   * @return Ссылка на объект для текучести.
   */
  public ColorInformationsAssert hasColorAndRange(Color color, Range range) {
    return anySatisfy(colorInformation ->
      assertFactory.createAssert(colorInformation).hasColorAndRange(color, range)
    );
  }

  @Override
  protected ColorInformationAssert toAssert(ColorInformation value, String description) {
    return assertFactory.createAssert(value).describedAs(description);
  }

  @Override
  protected ColorInformationsAssert newAbstractIterableAssert(Iterable<? extends ColorInformation> iterable) {
    return new ColorInformationsAssert(Lists.newArrayList(iterable));
  }
}
