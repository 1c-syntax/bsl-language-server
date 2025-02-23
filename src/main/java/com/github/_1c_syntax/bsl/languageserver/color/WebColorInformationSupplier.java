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
package com.github._1c_syntax.bsl.languageserver.color;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.eclipse.lsp4j.Color;
import org.eclipse.lsp4j.ColorInformation;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.github._1c_syntax.bsl.languageserver.color.BSLColor.DEFAULT_ALPHA_CHANNEL;
import static com.github._1c_syntax.bsl.languageserver.color.BSLColor.MAX_COLOR_COMPONENT_VALUE;

/**
 * Сапплаер данными о наличии использования элементов цвета через обращение
 * к системному перечислению {@code WebЦвета}.
 */
@Component
public class WebColorInformationSupplier implements ColorInformationSupplier {

  private static final Pattern WEB_COLOR_PATTERN = CaseInsensitivePattern.compile("^(?:WebЦвета|WebColors)$");
  private static final Map<String, WebColor> WEB_COLORS = createWebColors();

  @Override
  public List<ColorInformation> getColorInformation(DocumentContext documentContext) {
    var complexIdentifiers = Trees.findAllRuleNodes(
      documentContext.getAst(),
      BSLParser.RULE_complexIdentifier
    );

    return complexIdentifiers.stream()
      .map(BSLParser.ComplexIdentifierContext.class::cast)
      .filter(complexIdentifier -> complexIdentifier.IDENTIFIER() != null)
      .filter(complexIdentifier -> complexIdentifier.modifier().size() == 1)
      .filter(complexIdentifier -> complexIdentifier.modifier(0).accessProperty() != null)
      .filter(complexIdentifier -> WEB_COLOR_PATTERN.matcher(complexIdentifier.IDENTIFIER().getText()).matches())
      .map(WebColorInformationSupplier::toColorInformation)
      .collect(Collectors.toList());
  }

  private static ColorInformation toColorInformation(BSLParser.ComplexIdentifierContext ctx) {
    var colorName = ctx.modifier(0).accessProperty().IDENTIFIER().getText();
    var webColor = WEB_COLORS.get(colorName);

    double red = (double) webColor.getRed() / MAX_COLOR_COMPONENT_VALUE;
    double green = (double) webColor.getGreen() / MAX_COLOR_COMPONENT_VALUE;
    double blue = (double) webColor.getBlue() / MAX_COLOR_COMPONENT_VALUE;

    var range = Ranges.create(ctx);
    var color = new Color(red, green, blue, DEFAULT_ALPHA_CHANNEL);

    return new ColorInformation(range, color);
  }

  private static Map<String, WebColor> createWebColors() {
    var colors = new TreeMap<String, WebColor>(String.CASE_INSENSITIVE_ORDER);
    for (WebColor color : WebColor.values()) {
      colors.put(color.getRu(), color);
      colors.put(color.getEn(), color);
    }

    return colors;
  }
}
