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
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Поставщик подсветки для конструкций Try/Except/EndTry.
 * <p>
 * При клике на любое из ключевых слов блока Try подсвечиваются все связанные ключевые слова.
 */
@Component
public class TryStatementDocumentHighlightSupplier extends AbstractASTDocumentHighlightSupplier {

  @Override
  public List<DocumentHighlight> getDocumentHighlight(DocumentHighlightParams params, DocumentContext documentContext) {
    var position = params.getPosition();
    var ast = documentContext.getAst();

    // Находим терминальный узел на позиции курсора
    var maybeTerminalNode = Trees.findTerminalNodeContainsPosition(ast, position);
    if (maybeTerminalNode.isEmpty()) {
      return Collections.emptyList();
    }

    var terminalNode = maybeTerminalNode.get();
    var token = terminalNode.getSymbol();
    var tokenType = token.getType();

    // Проверяем, является ли токен одним из ключевых слов try-конструкции
    if (!isTryStatementKeyword(tokenType)) {
      return Collections.emptyList();
    }

    // Находим родительский узел tryStatement
    var tryStatement = Trees.getAncestorByRuleIndex(
      (ParserRuleContext) terminalNode.getParent(),
      BSLParser.RULE_tryStatement
    );

    if (tryStatement == null) {
      return Collections.emptyList();
    }

    return highlightTryStatement(tryStatement);
  }

  private boolean isTryStatementKeyword(int tokenType) {
    return tokenType == BSLParser.TRY_KEYWORD
      || tokenType == BSLParser.EXCEPT_KEYWORD
      || tokenType == BSLParser.ENDTRY_KEYWORD;
  }

  private List<DocumentHighlight> highlightTryStatement(ParserRuleContext tryStatement) {
    List<DocumentHighlight> highlights = new ArrayList<>();

    // Добавляем подсветку для Try
    addKeywordHighlight(highlights, tryStatement, BSLParser.TRY_KEYWORD);

    // Добавляем подсветку для Except
    addKeywordHighlight(highlights, tryStatement, BSLParser.EXCEPT_KEYWORD);

    // Добавляем подсветку для EndTry
    addKeywordHighlight(highlights, tryStatement, BSLParser.ENDTRY_KEYWORD);

    return highlights;
  }
}
