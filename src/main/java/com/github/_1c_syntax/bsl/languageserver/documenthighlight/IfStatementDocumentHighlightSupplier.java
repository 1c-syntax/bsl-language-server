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
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Поставщик подсветки для конструкций If/ElseIf/Else/EndIf.
 * <p>
 * При клике на любое из ключевых слов блока If подсвечиваются все связанные:
 * If, ElseIf, Else, EndIf, относящиеся к одному блоку.
 */
@Component
public class IfStatementDocumentHighlightSupplier implements DocumentHighlightSupplier {

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

    // Проверяем, является ли токен одним из ключевых слов if-конструкции
    if (!isIfStatementKeyword(tokenType)) {
      return Collections.emptyList();
    }

    // Находим родительский узел ifStatement
    var ifStatement = Trees.getAncestorByRuleIndex(
      (ParserRuleContext) terminalNode.getParent(),
      BSLParser.RULE_ifStatement
    );

    if (ifStatement == null) {
      return Collections.emptyList();
    }

    return highlightIfStatement(ifStatement);
  }

  private boolean isIfStatementKeyword(int tokenType) {
    return tokenType == BSLParser.IF_KEYWORD
      || tokenType == BSLParser.THEN_KEYWORD
      || tokenType == BSLParser.ELSIF_KEYWORD
      || tokenType == BSLParser.ELSE_KEYWORD
      || tokenType == BSLParser.ENDIF_KEYWORD;
  }

  private List<DocumentHighlight> highlightIfStatement(ParserRuleContext ifStatement) {
    List<DocumentHighlight> highlights = new ArrayList<>();

    // Добавляем подсветку для If и Then
    var ifBranch = Trees.getFirstChild(ifStatement, BSLParser.RULE_ifBranch);
    ifBranch.ifPresent(branch -> {
      addKeywordHighlight(highlights, branch, BSLParser.IF_KEYWORD);
      addKeywordHighlight(highlights, branch, BSLParser.THEN_KEYWORD);
    });

    // Добавляем подсветку для всех ElseIf и Then
    var elsifBranches = Trees.findAllRuleNodes(ifStatement, BSLParser.RULE_elsifBranch);
    for (var elsifBranch : elsifBranches) {
      addKeywordHighlight(highlights, (ParserRuleContext) elsifBranch, BSLParser.ELSIF_KEYWORD);
      addKeywordHighlight(highlights, (ParserRuleContext) elsifBranch, BSLParser.THEN_KEYWORD);
    }

    // Добавляем подсветку для Else
    var elseBranch = Trees.getFirstChild(ifStatement, BSLParser.RULE_elseBranch);
    elseBranch.ifPresent(branch -> addKeywordHighlight(highlights, branch, BSLParser.ELSE_KEYWORD));

    // Добавляем подсветку для EndIf
    addKeywordHighlight(highlights, ifStatement, BSLParser.ENDIF_KEYWORD);

    return highlights;
  }

  private void addKeywordHighlight(List<DocumentHighlight> highlights, ParserRuleContext context, int tokenType) {
    var tokens = Trees.findAllTokenNodes(context, tokenType);
    for (var tokenNode : tokens) {
      if (tokenNode instanceof TerminalNode terminalNode) {
        var token = terminalNode.getSymbol();
        var range = Ranges.create(token);
        highlights.add(new DocumentHighlight(range));
      }
    }
  }
}
