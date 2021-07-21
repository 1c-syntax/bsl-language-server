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
package com.github._1c_syntax.bsl.languageserver.color;

import org.eclipse.lsp4j.ColorPresentation;
import org.eclipse.lsp4j.ColorPresentationParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import java.util.List;
import java.util.stream.Collectors;

import static com.github._1c_syntax.bsl.languageserver.color.BSLColor.MAX_COLOR_COMPONENT_VALUE;

public class WebColorPresentationSupplier implements ColorPresentationSupplier {

  @Override
  public List<ColorPresentation> getColorPresentation(ColorPresentationParams params) {

    var range = params.getRange();
    var color = params.getColor();

    int red = (int) (color.getRed() * MAX_COLOR_COMPONENT_VALUE);
    int green = (int) (color.getGreen() * MAX_COLOR_COMPONENT_VALUE);
    int blue = (int) (color.getBlue() * MAX_COLOR_COMPONENT_VALUE);

    return WebColor.findByColor(red, green, blue)
      .map(webColor -> toColorPresentation(range, webColor))
      .stream()
      .collect(Collectors.toList());
  }

  private ColorPresentation toColorPresentation(Range range, WebColor webColor) {
    return new ColorPresentation(
      "Через WebЦвет",
      new TextEdit(range, "WebЦвета." + webColor.getRu())
    );
  }
}
