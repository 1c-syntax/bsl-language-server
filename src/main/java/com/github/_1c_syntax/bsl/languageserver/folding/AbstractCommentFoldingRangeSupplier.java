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
package com.github._1c_syntax.bsl.languageserver.folding;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Абстрактный сапплаер для получения областей сворачивания комментариев.
 */
public abstract class AbstractCommentFoldingRangeSupplier implements FoldingRangeSupplier {

  @Override
  public List<FoldingRange> getFoldingRanges(DocumentContext documentContext) {
    List<FoldingRange> foldingRanges = new ArrayList<>();

    List<Token> comments = getComments(documentContext);

    int lastRangeStart = -1;
    int previousLine = -1;

    for (Token token : comments) {
      int tokenLine = token.getLine();

      if (tokenLine != previousLine + 1) {
        if (lastRangeStart != previousLine) {
          FoldingRange foldingRange = new FoldingRange(lastRangeStart - 1, previousLine - 1);
          foldingRange.setKind(FoldingRangeKind.Comment);

          foldingRanges.add(foldingRange);
        }
        // new range
        lastRangeStart = tokenLine;
      }

      previousLine = tokenLine;
    }

    // add last range
    if (lastRangeStart != previousLine) {
      FoldingRange foldingRange = new FoldingRange(lastRangeStart - 1, previousLine - 1);
      foldingRange.setKind(FoldingRangeKind.Comment);

      foldingRanges.add(foldingRange);
    }
    return foldingRanges;
  }

  /**
   * @param documentContext Контекст документа, для которого надо получить список комментариев
   * @return Список токенов-комментариев
   */
  protected abstract List<Token> getComments(DocumentContext documentContext);
}
