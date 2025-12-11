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
package com.github._1c_syntax.bsl.languageserver.utils.expressiontree;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.antlr.v4.runtime.tree.ParseTree;
import org.jspecify.annotations.Nullable;

/**
 * Базовый класс для узлов дерева выражений.
 * <p>
 * Представляет любой узел в дереве выражений BSL:
 * литералы, идентификаторы, операции, вызовы методов.
 */
@Data
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BslExpression {
  private final ExpressionNodeType nodeType;
  private @Nullable ParseTree representingAst;

  @ToString.Exclude
  @Setter(AccessLevel.PACKAGE)
  private @Nullable BslExpression parent;

  /**
   * Синтаксический-помощник для более удобных downcast-ов
   * @param <T> тип, к которому надо привести данный узел
   * @return значение заданного типа
   */
  @SuppressWarnings("unchecked")
  public <T extends BslExpression> T cast() {
    return (T)this;
  }
}
