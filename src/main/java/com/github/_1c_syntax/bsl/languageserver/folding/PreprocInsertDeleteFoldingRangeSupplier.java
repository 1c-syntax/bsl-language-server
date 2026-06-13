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
package com.github._1c_syntax.bsl.languageserver.folding;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Сапплаер областей сворачивания блоков расширений конфигурации
 * <code>#Вставка ... #КонецВставки</code> и <code>#Удаление ... #КонецУдаления</code>.
 */
@Component
public class PreprocInsertDeleteFoldingRangeSupplier implements FoldingRangeSupplier {

  @Override
  public List<FoldingRange> getFoldingRanges(DocumentContext documentContext) {
    List<FoldingRange> foldingRanges = new ArrayList<>();
    Deque<Token> insertStack = new ArrayDeque<>();
    Deque<Token> deleteStack = new ArrayDeque<>();

    for (Token token : documentContext.getTokens()) {
      switch (token.getType()) {
        case BSLLexer.PREPROC_INSERT -> insertStack.push(token);
        case BSLLexer.PREPROC_ENDINSERT -> addFoldingRange(foldingRanges, insertStack, token);
        case BSLLexer.PREPROC_DELETE -> deleteStack.push(token);
        case BSLLexer.PREPROC_ENDDELETE -> addFoldingRange(foldingRanges, deleteStack, token);
        default -> {
          // прочие токены не участвуют в сворачивании
        }
      }
    }

    return foldingRanges;
  }

  private static void addFoldingRange(List<FoldingRange> foldingRanges, Deque<Token> startStack, Token endToken) {
    if (startStack.isEmpty()) {
      return;
    }

    Token startToken = startStack.pop();

    int start = startToken.getLine();
    int stop = endToken.getLine();

    FoldingRange foldingRange = new FoldingRange(start - 1, stop - 1);
    foldingRange.setKind(FoldingRangeKind.Region);

    foldingRanges.add(foldingRange);
  }

}
