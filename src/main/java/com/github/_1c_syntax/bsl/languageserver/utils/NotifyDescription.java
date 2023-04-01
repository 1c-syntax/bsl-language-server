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
package com.github._1c_syntax.bsl.languageserver.utils;

import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import lombok.experimental.UtilityClass;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@UtilityClass
public class NotifyDescription {

  private static final Pattern NOTIFY_DESCRIPTION = CaseInsensitivePattern.compile(
    "^(ОписаниеОповещения|NotifyDescription)$");

  private static final int MIN_PARAM_LIST_SIZE = 1;
  private static final int FULL_PARAM_LIST_SIZE = 5;

  public static final int HANDLER_INDEX = 0;
  public static final int HANDLER_MODULE_INDEX = 1;
  public static final int HANDLER_ERROR_INDEX = 3;
  public static final int HANDLER_ERROR_MODULE_INDEX = 4;

  public static boolean isNotifyDescription(BSLParser.NewExpressionContext newExpression) {
    var result = Optional.of(newExpression)
      .map(BSLParser.NewExpressionContext::typeName)
      .map(BSLParser.TypeNameContext::getText)
      .filter(t -> NOTIFY_DESCRIPTION.matcher(t).find());
    return result.isPresent();
  }

  public static boolean notifyDescriptionContainsHandler(Collection<?> callParamList) {
    return callParamList.size() > MIN_PARAM_LIST_SIZE;
  }

  public static boolean notifyDescriptionContainsErrorHandler(Collection<?> callParamList) {
    return callParamList.size() == FULL_PARAM_LIST_SIZE;
  }

  public static Optional<BSLParser.MemberContext> getFirstMember(BSLParser.CallParamContext callParamContext) {
    return Optional.ofNullable(callParamContext.expression())
      .map(BSLParser.ExpressionContext::member)
      .filter(Predicate.not(List::isEmpty))
      .map(member -> member.get(0));
  }

}
