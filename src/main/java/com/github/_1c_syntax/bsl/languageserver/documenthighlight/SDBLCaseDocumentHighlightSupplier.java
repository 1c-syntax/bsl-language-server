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
import com.github._1c_syntax.bsl.parser.SDBLLexer;
import com.github._1c_syntax.bsl.parser.SDBLParser;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.eclipse.lsp4j.Position;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Поставщик подсветки для конструкции ВЫБОР/КОГДА/ТОГДА/ИНАЧЕ/КОНЕЦ в SDBL-запросах.
 * <p>
 * При клике на любое ключевое слово конструкции CASE подсвечиваются все связанные:
 * CASE/WHEN/THEN/ELSE/END (ВЫБОР/КОГДА/ТОГДА/ИНАЧЕ/КОНЕЦ).
 */
@Component
public class SDBLCaseDocumentHighlightSupplier extends AbstractSDBLDocumentHighlightSupplier {

  @Override
  public List<DocumentHighlight> getDocumentHighlight(
    DocumentHighlightParams params,
    DocumentContext documentContext,
    Optional<TerminalNodeInfo> terminalNodeInfo
  ) {
    var position = params.getPosition();

    var tokenInfo = findTokenInQueries(position, documentContext);
    if (tokenInfo.isEmpty()) {
      return Collections.emptyList();
    }

    var info = tokenInfo.get();
    var tokenType = info.token().getType();

    if (!isCaseKeyword(tokenType)) {
      return Collections.emptyList();
    }

    // Ищем caseExpression в AST запроса
    var ast = info.tokenizer().getAst();
    var caseExpression = findCaseExpressionAtPosition(ast, position);

    if (caseExpression == null) {
      return Collections.emptyList();
    }

    return highlightCaseExpression(caseExpression);
  }

  private boolean isCaseKeyword(int tokenType) {
    return tokenType == SDBLLexer.CASE
      || tokenType == SDBLLexer.WHEN
      || tokenType == SDBLLexer.THEN
      || tokenType == SDBLLexer.ELSE
      || tokenType == SDBLLexer.END;
  }

  private SDBLParser.@Nullable CaseExpressionContext findCaseExpressionAtPosition(
    SDBLParser.QueryPackageContext ast, Position position) {

    var caseExpressions = Trees.findAllRuleNodes(ast, SDBLParser.RULE_caseExpression);
    for (var ctx : caseExpressions) {
      if (ctx instanceof SDBLParser.CaseExpressionContext caseCtx) {
        var range = Ranges.create(caseCtx);
        if (Ranges.containsPosition(range, position)) {
          // Проверяем, нет ли вложенного caseExpression, который тоже содержит позицию
          var nestedCase = findNestedCaseAtPosition(caseCtx, position);
          return nestedCase != null ? nestedCase : caseCtx;
        }
      }
    }
    return null;
  }

  private SDBLParser.@Nullable CaseExpressionContext findNestedCaseAtPosition(
    SDBLParser.CaseExpressionContext parentCase, Position position) {

    // Ищем вложенные CASE внутри caseBranch
    for (var branch : parentCase.caseBranch()) {
      var nestedCases = Trees.findAllRuleNodes(branch, SDBLParser.RULE_caseExpression);
      for (var ctx : nestedCases) {
        if (ctx instanceof SDBLParser.CaseExpressionContext nestedCaseCtx) {
          var range = Ranges.create(nestedCaseCtx);
          if (Ranges.containsPosition(range, position)) {
            // Рекурсивно проверяем ещё более глубокие вложенности
            var deeper = findNestedCaseAtPosition(nestedCaseCtx, position);
            return deeper != null ? deeper : nestedCaseCtx;
          }
        }
      }
    }

    // Проверяем elseExp (логическое выражение в ELSE)
    var elseExp = parentCase.elseExp;
    if (elseExp != null) {
      var nestedCases = Trees.findAllRuleNodes(elseExp, SDBLParser.RULE_caseExpression);
      for (var ctx : nestedCases) {
        if (ctx instanceof SDBLParser.CaseExpressionContext nestedCaseCtx) {
          var range = Ranges.create(nestedCaseCtx);
          if (Ranges.containsPosition(range, position)) {
            var deeper = findNestedCaseAtPosition(nestedCaseCtx, position);
            return deeper != null ? deeper : nestedCaseCtx;
          }
        }
      }
    }

    return null;
  }

  private List<DocumentHighlight> highlightCaseExpression(SDBLParser.CaseExpressionContext caseCtx) {
    List<DocumentHighlight> highlights = new ArrayList<>();

    // CASE/ВЫБОР
    addTerminalHighlight(highlights, caseCtx.CASE());

    // WHEN/КОГДА и THEN/ТОГДА для каждой ветки
    for (var branch : caseCtx.caseBranch()) {
      addTerminalHighlight(highlights, branch.WHEN());
      addTerminalHighlight(highlights, branch.THEN());
    }

    // ELSE/ИНАЧЕ (если есть elseExp, значит есть ELSE)
    if (caseCtx.elseExp != null) {
      addTerminalHighlight(highlights, caseCtx.ELSE());
    }

    // END/КОНЕЦ
    addTerminalHighlight(highlights, caseCtx.END());

    return highlights;
  }
}

