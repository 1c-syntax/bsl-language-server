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
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Поставщик подсветки для конструкций JOIN в SDBL-запросах.
 * <p>
 * При клике на ключевые слова JOIN (LEFT/RIGHT/FULL/INNER JOIN) или ON/ПО
 * подсвечиваются все связанные ключевые слова данного соединения.
 */
@Component
public class SDBLJoinDocumentHighlightSupplier extends AbstractSDBLDocumentHighlightSupplier {

  @Override
  public List<DocumentHighlight> getDocumentHighlight(DocumentHighlightParams params, DocumentContext documentContext) {
    var position = params.getPosition();

    var tokenInfo = findTokenInQueries(position, documentContext);
    if (tokenInfo.isEmpty()) {
      return Collections.emptyList();
    }

    var info = tokenInfo.get();
    var tokenType = info.token().getType();

    if (!isJoinKeyword(tokenType)) {
      return Collections.emptyList();
    }

    // Ищем joinPart в AST запроса
    var ast = info.tokenizer().getAst();
    var joinPart = findJoinPartAtPosition(ast, position);

    if (joinPart == null) {
      return Collections.emptyList();
    }

    return highlightJoinPart(joinPart);
  }

  private boolean isJoinKeyword(int tokenType) {
    return tokenType == SDBLLexer.JOIN
      || tokenType == SDBLLexer.LEFT
      || tokenType == SDBLLexer.RIGHT
      || tokenType == SDBLLexer.FULL
      || tokenType == SDBLLexer.INNER
      || tokenType == SDBLLexer.OUTER
      || tokenType == SDBLLexer.ON_EN
      || tokenType == SDBLLexer.PO_RU;
  }

  private SDBLParser.@Nullable JoinPartContext findJoinPartAtPosition(
    SDBLParser.QueryPackageContext ast, Position position) {

    var joinParts = Trees.findAllRuleNodes(ast, SDBLParser.RULE_joinPart);
    for (var ctx : joinParts) {
      if (ctx instanceof SDBLParser.JoinPartContext joinCtx) {
        var range = Ranges.create(joinCtx);
        if (Ranges.containsPosition(range, position)) {
          return joinCtx;
        }
      }
    }
    return null;
  }

  private List<DocumentHighlight> highlightJoinPart(SDBLParser.JoinPartContext joinCtx) {
    List<DocumentHighlight> highlights = new ArrayList<>();

    // LEFT/RIGHT/FULL/INNER
    addTerminalHighlight(highlights, joinCtx.LEFT());
    addTerminalHighlight(highlights, joinCtx.RIGHT());
    addTerminalHighlight(highlights, joinCtx.FULL());
    addTerminalHighlight(highlights, joinCtx.INNER());

    // OUTER (опционально после LEFT/RIGHT/FULL)
    addTerminalHighlight(highlights, joinCtx.OUTER());

    // JOIN
    addTerminalHighlight(highlights, joinCtx.JOIN());

    // ON/ПО
    addTerminalHighlight(highlights, joinCtx.ON_EN());
    addTerminalHighlight(highlights, joinCtx.PO_RU());

    return highlights;
  }
}

