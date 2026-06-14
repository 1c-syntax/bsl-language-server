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
 * корректнее {@link SymbolKind#Function}. Конкретный вид определяется при
 * построении символа в
 * {@link com.github._1c_syntax.bsl.languageserver.context.computer.MethodSymbolComputer}
 * (там доступен {@link com.github._1c_syntax.bsl.languageserver.context.DocumentContext}
 * с типом модуля) и фиксируется флагом {@link #standaloneFunction}, чтобы вид
 * был единым для всех потребителей (структура документа, индекс рабочей области,
 * автодополнение и т. п.).
 * <p>
 * Вид хранится именно {@code boolean}-флагом, а не ссылкой на {@link SymbolKind}:
 * по замерам JOL такой флаг укладывается в существующий выравнивающий зазор
 * объекта и не увеличивает его размер ни при сжатых, ни при несжатых
 * указателях (8-байтовая ссылка на {@link SymbolKind} добавила бы +8 байт при
 * несжатых указателях). Символов методов в конфигурации много, поэтому размер
 * экземпляра важен.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public final class RegularMethodSymbol extends AbstractMethodSymbol {

  /**
   * {@code true}, если метод принадлежит модулю без состояния (модуль OneScript
   * либо общий модуль BSL) и потому является самостоятельной функцией —
   * {@link SymbolKind#Function}. Иначе метод представлен как
   * {@link SymbolKind#Method}. Значение по умолчанию — {@code false}.
   */
  @Builder.Default
  private final boolean standaloneFunction = false;

  @Override
  public SymbolKind getSymbolKind() {
    return standaloneFunction ? SymbolKind.Function : SymbolKind.Method;
  }

  @Override
  public void accept(SymbolTreeVisitor visitor) {
    visitor.visitRegularMethod(this);
  }
}
