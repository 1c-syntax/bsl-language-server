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
package com.github._1c_syntax.bsl.languageserver.documenthighlight;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightParams;
import java.util.Optional;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Поставщик подсветки для парных скобок (круглых и квадратных).
 * <p>
 * При клике на скобку подсвечивается парная скобка, учитывая вложенность.
 */
@Component
public class BracketDocumentHighlightSupplier implements DocumentHighlightSupplier {

  @Override
  public List<DocumentHighlight> getDocumentHighlight(
    DocumentHighlightParams params,
    DocumentContext documentContext,
    Optional<TerminalNodeInfo> terminalNodeInfo
  ) {
    if (terminalNodeInfo.isEmpty()) {
      return Collections.emptyList();
    }

    var tokenType = terminalNodeInfo.get().tokenType();

    // Проверяем, является ли токен скобкой
    if (!isBracket(tokenType)) {
      return Collections.emptyList();
    }

    var token = terminalNodeInfo.get().terminalNode().getSymbol();
    return highlightMatchingBracket(token, documentContext);
  }

  private boolean isBracket(int tokenType) {
    return tokenType == BSLParser.LPAREN
      || tokenType == BSLParser.RPAREN
      || tokenType == BSLParser.LBRACK
      || tokenType == BSLParser.RBRACK;
  }

  private List<DocumentHighlight> highlightMatchingBracket(Token token, DocumentContext documentContext) {
    List<DocumentHighlight> highlights = new ArrayList<>();
    var tokenType = token.getType();
    var allTokens = documentContext.getTokens();

    int openType, closeType;
    boolean isOpening;

    // Определяем тип скобок
    if (tokenType == BSLParser.LPAREN) {
      openType = BSLParser.LPAREN;
      closeType = BSLParser.RPAREN;
      isOpening = true;
    } else if (tokenType == BSLParser.RPAREN) {
      openType = BSLParser.LPAREN;
      closeType = BSLParser.RPAREN;
      isOpening = false;
    } else if (tokenType == BSLParser.LBRACK) {
      openType = BSLParser.LBRACK;
      closeType = BSLParser.RBRACK;
      isOpening = true;
    } else if (tokenType == BSLParser.RBRACK) {
      openType = BSLParser.LBRACK;
      closeType = BSLParser.RBRACK;
      isOpening = false;
    } else {
      return highlights;
    }

    // Добавляем текущую скобку
    highlights.add(new DocumentHighlight(Ranges.create(token)));

    // Ищем парную скобку
    var matchingToken = findMatchingBracket(token, allTokens, openType, closeType, isOpening);
    if (matchingToken != null) {
      highlights.add(new DocumentHighlight(Ranges.create(matchingToken)));
    }

    return highlights;
  }

  private Token findMatchingBracket(Token token, List<Token> allTokens, int openType, int closeType, boolean isOpening) {
    int tokenIndex = token.getTokenIndex();
    int depth = 1;

    if (isOpening) {
      // Ищем закрывающую скобку вперед
      for (int i = tokenIndex + 1; i < allTokens.size(); i++) {
        var currentToken = allTokens.get(i);
        var currentType = currentToken.getType();

        if (currentType == openType) {
          depth++;
        } else if (currentType == closeType) {
          depth--;
          if (depth == 0) {
            return currentToken;
          }
        }
      }
    } else {
      // Ищем открывающую скобку назад
      for (int i = tokenIndex - 1; i >= 0; i--) {
        var currentToken = allTokens.get(i);
        var currentType = currentToken.getType();

        if (currentType == closeType) {
          depth++;
        } else if (currentType == openType) {
          depth--;
          if (depth == 0) {
            return currentToken;
          }
        }
      }
    }

    return null;
  }
}
