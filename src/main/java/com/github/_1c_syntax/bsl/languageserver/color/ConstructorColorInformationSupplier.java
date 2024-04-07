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
package com.github._1c_syntax.bsl.languageserver.color;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.languageserver.utils.bsl.Constructors;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Color;
import org.eclipse.lsp4j.ColorInformation;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.github._1c_syntax.bsl.languageserver.color.BSLColor.DEFAULT_ALPHA_CHANNEL;
import static com.github._1c_syntax.bsl.languageserver.color.BSLColor.MAX_COLOR_COMPONENT_VALUE;

/**
 * Сапплаер данными о наличии использования элементов цвета через конструктор
 * {@code Новый Цвет()}.
 */
@Component
public class ConstructorColorInformationSupplier implements ColorInformationSupplier {

  private static final Pattern COLOR_PATTERN = CaseInsensitivePattern.compile("^(?:Цвет|Color)$");

  @Override
  public List<ColorInformation> getColorInformation(DocumentContext documentContext) {
    var newExpressions = Trees.findAllRuleNodes(
      documentContext.getAst(),
      BSLParser.RULE_newExpression
    );

    return newExpressions.stream()
      .map(BSLParser.NewExpressionContext.class::cast)
      .filter(newExpression -> Constructors.typeName(newExpression).filter(name -> COLOR_PATTERN.matcher(name).matches()).isPresent())
      .map(ConstructorColorInformationSupplier::toColorInformation)
      .collect(Collectors.toList());
  }

  private static ColorInformation toColorInformation(BSLParser.NewExpressionContext ctx) {
    byte redPosition;
    byte greenPosition;
    byte bluePosition;

    if (ctx.typeName() != null) {
      redPosition = 0;
      greenPosition = 1;
      bluePosition = 2;
    } else {
      redPosition = 1;
      greenPosition = 2;
      bluePosition = 3;
    }

    var callParams = Optional.ofNullable(ctx.doCall())
      .map(BSLParser.DoCallContext::callParamList)
      .orElseGet(() -> new BSLParser.CallParamListContext(null, 0));

    double red = getColorValue(callParams, redPosition);
    double green = getColorValue(callParams, greenPosition);
    double blue = getColorValue(callParams, bluePosition);

    var range = Ranges.create(ctx);
    var color = new Color(red, green, blue, DEFAULT_ALPHA_CHANNEL);

    return new ColorInformation(range, color);
  }

  private static Double getColorValue(BSLParser.CallParamListContext callParams, byte colorPosition) {
    return Optional.ofNullable(callParams.callParam(colorPosition))
      .map(BSLParser.CallParamContext::expression)
      .filter(expression -> expression.getTokens().size() == 1)
      .map(expression -> expression.getTokens().get(0))
      .map(Token::getText)
      .map(ConstructorColorInformationSupplier::tryParseInteger)
      .map(colorValue -> (double) colorValue / MAX_COLOR_COMPONENT_VALUE)
      .orElse(0.0);
  }

  private static Integer tryParseInteger(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
