/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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
package com.github._1c_syntax.bsl.languageserver.utils.bsl;

import com.github._1c_syntax.bsl.languageserver.utils.Strings;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.experimental.UtilityClass;
import org.antlr.v4.runtime.RuleContext;

import java.util.List;
import java.util.Optional;

/**
 * Набор методов для работы с конструкторами объектов 1С
 */
@UtilityClass
public class Constructors {

  /**
   * Вычисляет имя типа создаваемого объекта, работает с
   *  Новый ТипОбъекта;
   *  Новый("ТипОбъекта")
   * @param newExpression контекст выражения
   * @return имя типа объекта
   */
  public static Optional<String> typeName(BSLParser.NewExpressionContext newExpression) {
    return Optional.ofNullable(newExpression.typeName())
      .map(RuleContext::getText)
      .or(() -> getTypeNameFromArgs(newExpression));
  }

  private static Optional<String> getTypeNameFromArgs(BSLParser.NewExpressionContext newExpression){
    return Optional.ofNullable(newExpression.doCall())
      .map(BSLParser.DoCallContext::callParamList)
      .map(BSLParser.CallParamListContext::callParam)
      .flatMap(Constructors::first)
      .map(BSLParser.CallParamContext::expression)
      .map(BSLParser.ExpressionContext::member)
      .flatMap(Constructors::first)
      .map(BSLParser.MemberContext::constValue)
      .filter(constValue -> constValue.string() != null)
      .map(RuleContext::getText)
      .map(Strings::trimQuotes);
  }

  private static <T> Optional<T> first(List<T> list) {
    if (list.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(list.get(0));
    }
  }
}
