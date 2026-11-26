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
package com.github._1c_syntax.bsl.languageserver.utils.expressiontree;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.Value;

/**
 * Узел вызова конструктора в дереве выражений.
 * <p>
 * Представляет вызов конструктора типа: Новый Структура(), Новый("Структура").
 */
@Value
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ConstructorCallNode extends AbstractCallNode {

  BslExpression typeName;

  /**
   * @return Истина, если конструктор вызван статическим указанием имени типа.
   * Ложь, если это вызов в функциональном стиле с указанием имени типа строкой.
   */
  public boolean isStaticallyTyped() {
    return typeName instanceof TerminalSymbolNode && typeName.getNodeType() == ExpressionNodeType.LITERAL;
  }

  /**
   * Конструирование статического вызова конструктора
   * @param typeName терминальный символ имени типа
   * @return ветка конструктора
   */
  public static ConstructorCallNode createStatic(TerminalSymbolNode typeName) {
    return new ConstructorCallNode(typeName);
  }

  /**
   * Конструирование вызова конструктора в функциональном стиле
   * @param typeNameExpression подвыражение с именем типа
   * @return ветка конструктора
   */
  public static ConstructorCallNode createDynamic(BslExpression typeNameExpression) {
    return new ConstructorCallNode(typeNameExpression);
  }

}
