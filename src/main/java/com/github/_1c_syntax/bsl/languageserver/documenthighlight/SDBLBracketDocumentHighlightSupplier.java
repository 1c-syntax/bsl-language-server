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
import com.github._1c_syntax.bsl.parser.SDBLLexer;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Поставщик подсветки для скобок в SDBL-запросах.
 * <p>
 * При клике на открывающую или закрывающую скобку подсвечивается парная скобка.
 */
@Component
public class SDBLBracketDocumentHighlightSupplier extends AbstractSDBLDocumentHighlightSupplier {

  @Override
  public List<DocumentHighlight> getDocumentHighlight(DocumentHighlightParams params, DocumentContext documentContext) {
    var position = params.getPosition();

    var tokenInfo = findTokenInQueries(position, documentContext);
    if (tokenInfo.isEmpty()) {
      return Collections.emptyList();
    }

    var info = tokenInfo.get();
    var token = info.token();
    var tokenType = token.getType();

    if (!isBracket(tokenType)) {
      return Collections.emptyList();
    }

    var tokens = info.tokenizer().getTokens();
    var matchingBracket = findMatchingBracket(tokens, token);

    if (matchingBracket == null) {
      return Collections.emptyList();
    }

    List<DocumentHighlight> highlights = new ArrayList<>();
    addTokenHighlight(highlights, token);
    addTokenHighlight(highlights, matchingBracket);

    return highlights;
  }

  private boolean isBracket(int tokenType) {
    return tokenType == SDBLLexer.LPAREN
      || tokenType == SDBLLexer.RPAREN;
  }

  @Nullable
  private Token findMatchingBracket(List<? extends Token> tokens, Token bracket) {
    var tokenType = bracket.getType();
    var tokenIndex = bracket.getTokenIndex();

    if (tokenType == SDBLLexer.LPAREN) {
      return findClosingBracket(tokens, tokenIndex);
    } else if (tokenType == SDBLLexer.RPAREN) {
      return findOpeningBracket(tokens, tokenIndex);
    }

    return null;
  }

  @Nullable
  private Token findClosingBracket(List<? extends Token> tokens, int startIndex) {
    int depth = 0;
    for (int i = startIndex; i < tokens.size(); i++) {
      var token = tokens.get(i);
      var type = token.getType();

      if (type == SDBLLexer.LPAREN) {
        depth++;
      } else if (type == SDBLLexer.RPAREN) {
        depth--;
        if (depth == 0) {
          return token;
        }
      }
    }
    return null;
  }

  @Nullable
  private Token findOpeningBracket(List<? extends Token> tokens, int startIndex) {
    int depth = 0;
    for (int i = startIndex; i >= 0; i--) {
      var token = tokens.get(i);
      var type = token.getType();

      if (type == SDBLLexer.RPAREN) {
        depth++;
      } else if (type == SDBLLexer.LPAREN) {
        depth--;
        if (depth == 0) {
          return token;
        }
      }
    }
    return null;
  }
}

