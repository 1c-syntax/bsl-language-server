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
package com.github._1c_syntax.bsl.languageserver.context.symbol;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.eclipse.lsp4j.SymbolKind;

/**
 * Реализация {@link MethodSymbol} по умолчанию — обычный метод или функция в
 * модуле BSL. Конструкторы OneScript-классов представлены отдельным
 * {@link ConstructorSymbol}, чтобы их можно было различать в symbol tree,
 * hover'е и go-to-definition. Общая структура полей — в {@link AbstractMethodSymbol}.
 * <p>
 * В LSP нет отдельного {@link SymbolKind} для процедур, поэтому процедуры и
 * функции BSL исторически представлены как {@link SymbolKind#Method}. Но методы
 * модулей без состояния — модулей OneScript и общих модулей BSL — это
 * самостоятельные функции, а не члены объекта со состоянием, поэтому для них
 * корректнее {@link SymbolKind#Function}. Конкретный вид вычисляется при
 * построении символа в
 * {@link com.github._1c_syntax.bsl.languageserver.context.computer.MethodSymbolComputer}
 * (там доступен {@link com.github._1c_syntax.bsl.languageserver.context.DocumentContext}
 * с типом файла и типом модуля) и сохраняется в {@link #symbolKind}, чтобы вид
 * был единым для всех потребителей (структура документа, индекс рабочей области,
 * автодополнение и т. п.).
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public final class RegularMethodSymbol extends AbstractMethodSymbol {

  /**
   * Вид символа метода: {@link SymbolKind#Function} для методов модулей без
   * состояния (модули OneScript и общие модули BSL), иначе
   * {@link SymbolKind#Method}. Значение по умолчанию — {@link SymbolKind#Method}.
   */
  @Builder.Default
  private final SymbolKind symbolKind = SymbolKind.Method;

  @Override
  public SymbolKind getSymbolKind() {
    return symbolKind;
  }

  @Override
  public void accept(SymbolTreeVisitor visitor) {
    visitor.visitRegularMethod(this);
  }
}
