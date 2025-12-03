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
import org.eclipse.lsp4j.FoldingRange;

import java.util.List;

public class FoldingRangesAssert extends AbstractListAssert<FoldingRangesAssert, List<FoldingRange>, FoldingRange, FoldingRangeAssert> {

  private final FoldingRangeAssertFactory assertFactory = new FoldingRangeAssertFactory();

  public FoldingRangesAssert(List<FoldingRange> actual) {
    super(actual, FoldingRangesAssert.class);
  }

  public FoldingRangesAssert hasRange(int startLine, int endLine) {

    return anySatisfy(foldingRange ->
      assertFactory.createAssert(foldingRange).hasRange(startLine, endLine)
    );

  }

  /**
   * Ассерт для проверки совпадения диапазона и сообщения
   *
   * @param kind   Тип области сворачивания см. {@link org.eclipse.lsp4j.FoldingRangeKind}
   * @param startLine Первая строка диапазона
   * @param endLine   Последняя строка диапазона
   * @return Ссылка на объект для текучести
   */
  public FoldingRangesAssert hasKindAndRange(String kind, int startLine, int endLine) {
    return anySatisfy(diagnostic ->
      assertFactory.createAssert(diagnostic).hasKindAndRange(kind, startLine, endLine)
    );
  }

  @Override
  protected FoldingRangeAssert toAssert(FoldingRange value, String description) {
    return assertFactory.createAssert(value);
  }

  @Override
  protected FoldingRangesAssert newAbstractIterableAssert(Iterable<? extends FoldingRange> iterable) {
    return new FoldingRangesAssert(Lists.newArrayList(iterable));
  }
}
