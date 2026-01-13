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
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.jspecify.annotations.Nullable;
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
    var terminalNodeInfo = findTerminalNode(params.getPosition(), documentContext);
    if (terminalNodeInfo.isEmpty()) {
      return Collections.emptyList();
    }

    var info = terminalNodeInfo.get();
    if (!isLoopKeyword(info.tokenType())) {
      return Collections.emptyList();
    }

    var parent = (ParserRuleContext) info.terminalNode().getParent();

    // Находим ближайший цикл - сначала проверяем сам parent, потом его предков
    var loopStatement = findNearestLoopStatement(parent);
    if (loopStatement == null) {
      return Collections.emptyList();
    }

    // Определяем тип цикла и подсвечиваем
    return switch (loopStatement.getRuleIndex()) {
      case BSLParser.RULE_whileStatement -> highlightWhileStatement(loopStatement);
      case BSLParser.RULE_forStatement -> highlightForStatement(loopStatement);
      case BSLParser.RULE_forEachStatement -> highlightForEachStatement(loopStatement);
      default -> Collections.emptyList();
    };
  }

  private boolean isLoopKeyword(int tokenType) {
    return tokenType == BSLParser.WHILE_KEYWORD
      || tokenType == BSLParser.FOR_KEYWORD
      || tokenType == BSLParser.EACH_KEYWORD
      || tokenType == BSLParser.IN_KEYWORD
      || tokenType == BSLParser.TO_KEYWORD
      || tokenType == BSLParser.DO_KEYWORD
      || tokenType == BSLParser.ENDDO_KEYWORD
      || tokenType == BSLParser.BREAK_KEYWORD
      || tokenType == BSLParser.CONTINUE_KEYWORD;
  }

  /**
   * Находит ближайший Statement контекст цикла.
   * Сначала проверяет сам parent, затем поднимается по иерархии предков.
   * Это необходимо для корректной обработки вложенных циклов.
   */
  @Nullable
  private ParserRuleContext findNearestLoopStatement(ParserRuleContext context) {
    var current = context;
    while (current != null) {
      var ruleIndex = current.getRuleIndex();
      if (ruleIndex == BSLParser.RULE_whileStatement
          || ruleIndex == BSLParser.RULE_forStatement
          || ruleIndex == BSLParser.RULE_forEachStatement) {
        return current;
      }
      var parent = current.getParent();
      current = parent instanceof ParserRuleContext ? (ParserRuleContext) parent : null;
    }
    return null;
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

    // Добавляем break и continue внутри цикла
    addBreakAndContinueHighlights(highlights, whileStatementContext.codeBlock());

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

    // Добавляем break и continue внутри цикла
    addBreakAndContinueHighlights(highlights, forStatementContext.codeBlock());

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

    // Добавляем break и continue внутри цикла
    addBreakAndContinueHighlights(highlights, forEachStatementContext.codeBlock());

    return highlights;
  }

  /**
   * Добавляет подсветку для break и continue внутри блока кода цикла.
   * Ищет только на первом уровне вложенности - не заходит во вложенные циклы.
   */
  private void addBreakAndContinueHighlights(List<DocumentHighlight> highlights, BSLParser.CodeBlockContext codeBlock) {
    if (codeBlock == null) {
      return;
    }

    for (var statement : codeBlock.statement()) {
      var compoundStatement = statement.compoundStatement();
      if (compoundStatement == null) {
        continue;
      }

      // Подсвечиваем break
      var breakStatement = compoundStatement.breakStatement();
      if (breakStatement != null) {
        addTokenHighlight(highlights, breakStatement.BREAK_KEYWORD());
      }

      // Подсвечиваем continue
      var continueStatement = compoundStatement.continueStatement();
      if (continueStatement != null) {
        addTokenHighlight(highlights, continueStatement.CONTINUE_KEYWORD());
      }

      // Рекурсивно ищем в if/else блоках, но НЕ во вложенных циклах
      var ifStatement = compoundStatement.ifStatement();
      if (ifStatement != null) {
        addBreakAndContinueFromIfStatement(highlights, ifStatement);
      }

      var tryStatement = compoundStatement.tryStatement();
      if (tryStatement != null) {
        addBreakAndContinueFromTryStatement(highlights, tryStatement);
      }
    }
  }

  /**
   * Ищет break/continue в if-блоках.
   */
  private void addBreakAndContinueFromIfStatement(List<DocumentHighlight> highlights,
                                                   BSLParser.IfStatementContext ifStatement) {
    var ifBranch = ifStatement.ifBranch();
    if (ifBranch != null) {
      addBreakAndContinueHighlights(highlights, ifBranch.codeBlock());
    }

    for (var elsifBranch : ifStatement.elsifBranch()) {
      addBreakAndContinueHighlights(highlights, elsifBranch.codeBlock());
    }

    var elseBranch = ifStatement.elseBranch();
    if (elseBranch != null) {
      addBreakAndContinueHighlights(highlights, elseBranch.codeBlock());
    }
  }

  /**
   * Ищет break/continue в try-блоках.
   */
  private void addBreakAndContinueFromTryStatement(List<DocumentHighlight> highlights,
                                                    BSLParser.TryStatementContext tryStatement) {
    addBreakAndContinueHighlights(highlights, tryStatement.tryCodeBlock().codeBlock());
    addBreakAndContinueHighlights(highlights, tryStatement.exceptCodeBlock().codeBlock());
  }
}
