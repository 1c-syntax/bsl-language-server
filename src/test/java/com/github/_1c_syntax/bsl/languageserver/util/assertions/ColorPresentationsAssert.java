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
import org.eclipse.lsp4j.ColorPresentation;
import org.eclipse.lsp4j.TextEdit;

import java.util.List;

public class ColorPresentationsAssert extends AbstractListAssert<ColorPresentationsAssert, List<ColorPresentation>, ColorPresentation, ColorPresentationAssert> {

  private final ColorPresentationAssertFactory assertFactory = new ColorPresentationAssertFactory();

  public ColorPresentationsAssert(List<ColorPresentation> actual) {
    super(actual, ColorPresentationsAssert.class);
  }

  /**
   * Ассерт для проверки совпадения метки.
   *
   * @param label Метка.
   * @return Ссылка на объект для текучести.
   */
  public ColorPresentationsAssert hasLabel(String label) {

    return anySatisfy(colorInformation ->
      assertFactory.createAssert(colorInformation).hasLabel(label)
    );

  }

  /**
   * Ассерт для проверки совпадения редактирования текста.
   *
   * @param textEdit Редактирование текста (см. {@link TextEdit}.
   * @return Ссылка на объект для текучести.
   */
  public ColorPresentationsAssert hasTextEdit(TextEdit textEdit) {

    return anySatisfy(colorInformation ->
      assertFactory.createAssert(colorInformation).hasTextEdit(textEdit)
    );

  }

  /**
   * Ассерт для проверки совпадения метки и редактирования текста.
   *
   * @param label    Метка.
   * @param textEdit Редактирование текста (см. {@link TextEdit}.
   * @return Ссылка на объект для текучести
   */
  public ColorPresentationsAssert hasLabelAndTextEdit(String label, TextEdit textEdit) {
    return anySatisfy(colorInformation ->
      assertFactory.createAssert(colorInformation).hasLabelAndTextEdit(label, textEdit)
    );
  }

  @Override
  protected ColorPresentationAssert toAssert(ColorPresentation value, String description) {
    return assertFactory.createAssert(value).describedAs(description);
  }

  @Override
  protected ColorPresentationsAssert newAbstractIterableAssert(Iterable<? extends ColorPresentation> iterable) {
    return new ColorPresentationsAssert(Lists.newArrayList(iterable));
  }
}
