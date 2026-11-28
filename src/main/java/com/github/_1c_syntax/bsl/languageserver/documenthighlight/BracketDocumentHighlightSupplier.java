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
import org.eclipse.lsp4j.DocumentHighlightKind;
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
    var roundBracket = tokenType == BSLParser.LPAREN || tokenType == BSLParser.RPAREN;
    var openType = roundBracket ? BSLParser.LPAREN : BSLParser.LBRACK;
    var closeType = roundBracket ? BSLParser.RPAREN : BSLParser.RBRACK;

    // Текущая скобка подсвечивается и без пары
    highlights.add(new DocumentHighlight(Ranges.create(token), DocumentHighlightKind.Text));

    var matchingToken = MatchingBrackets.find(documentContext.getTokens(), token.getTokenIndex(), openType, closeType);
    if (matchingToken != null) {
      highlights.add(new DocumentHighlight(Ranges.create(matchingToken), DocumentHighlightKind.Text));
    }

    return highlights;
  }
}
