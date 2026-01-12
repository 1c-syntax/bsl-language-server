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
 * Поставщик подсветки для циклов (For/While/Do/EndDo).
 * <p>
 * При клике на любое из ключевых слов цикла подсвечиваются все связанные ключевые слова того же цикла.
 */
@Component
public class LoopStatementDocumentHighlightSupplier extends AbstractASTDocumentHighlightSupplier {

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

    // Проверяем, является ли токен одним из ключевых слов цикла
    if (!isLoopKeyword(tokenType)) {
      return Collections.emptyList();
    }

    var parent = (ParserRuleContext) terminalNode.getParent();

    // Проверяем для While
    var whileStatement = Trees.getAncestorByRuleIndex(parent, BSLParser.RULE_whileStatement);
    if (whileStatement != null) {
      return highlightWhileStatement(whileStatement);
    }

    // Проверяем для For
    var forStatement = Trees.getAncestorByRuleIndex(parent, BSLParser.RULE_forStatement);
    if (forStatement != null) {
      return highlightForStatement(forStatement);
    }

    // Проверяем для ForEach
    var forEachStatement = Trees.getAncestorByRuleIndex(parent, BSLParser.RULE_forEachStatement);
    if (forEachStatement != null) {
      return highlightForEachStatement(forEachStatement);
    }

    return Collections.emptyList();
  }

  private boolean isLoopKeyword(int tokenType) {
    return tokenType == BSLParser.WHILE_KEYWORD
      || tokenType == BSLParser.FOR_KEYWORD
      || tokenType == BSLParser.EACH_KEYWORD
      || tokenType == BSLParser.IN_KEYWORD
      || tokenType == BSLParser.TO_KEYWORD
      || tokenType == BSLParser.DO_KEYWORD
      || tokenType == BSLParser.ENDDO_KEYWORD;
  }

  private List<DocumentHighlight> highlightWhileStatement(ParserRuleContext whileStatement) {
    List<DocumentHighlight> highlights = new ArrayList<>();

    // Приводим к конкретному типу контекста для доступа к геттерам токенов
    if (!(whileStatement instanceof BSLParser.WhileStatementContext whileStatementContext)) {
      return highlights;
    }

    // Используем геттеры из контекста для прямого доступа к токенам
    addTokenHighlight(highlights, whileStatementContext.WHILE_KEYWORD());
    addTokenHighlight(highlights, whileStatementContext.DO_KEYWORD());
    addTokenHighlight(highlights, whileStatementContext.ENDDO_KEYWORD());

    return highlights;
  }

  private List<DocumentHighlight> highlightForStatement(ParserRuleContext forStatement) {
    List<DocumentHighlight> highlights = new ArrayList<>();

    // Приводим к конкретному типу контекста для доступа к геттерам токенов
    if (!(forStatement instanceof BSLParser.ForStatementContext forStatementContext)) {
      return highlights;
    }

    // Используем геттеры из контекста для прямого доступа к токенам
    addTokenHighlight(highlights, forStatementContext.FOR_KEYWORD());
    addTokenHighlight(highlights, forStatementContext.TO_KEYWORD());
    addTokenHighlight(highlights, forStatementContext.DO_KEYWORD());
    addTokenHighlight(highlights, forStatementContext.ENDDO_KEYWORD());

    return highlights;
  }

  private List<DocumentHighlight> highlightForEachStatement(ParserRuleContext forEachStatement) {
    List<DocumentHighlight> highlights = new ArrayList<>();

    // Приводим к конкретному типу контекста для доступа к геттерам токенов
    if (!(forEachStatement instanceof BSLParser.ForEachStatementContext forEachStatementContext)) {
      return highlights;
    }

    // Используем геттеры из контекста для прямого доступа к токенам
    addTokenHighlight(highlights, forEachStatementContext.FOR_KEYWORD());
    addTokenHighlight(highlights, forEachStatementContext.EACH_KEYWORD());
    addTokenHighlight(highlights, forEachStatementContext.IN_KEYWORD());
    addTokenHighlight(highlights, forEachStatementContext.DO_KEYWORD());
    addTokenHighlight(highlights, forEachStatementContext.ENDDO_KEYWORD());

    return highlights;
  }
}
