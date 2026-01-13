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
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.eclipse.lsp4j.Range;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Поставщик подсветки для процедур и функций (Procedure/EndProcedure, Function/EndFunction).
 * <p>
 * При клике на ключевое слово Процедура/Функция или КонецПроцедуры/КонецФункции
 * подсвечивается соответствующее парное ключевое слово.
 */
@Component
public class SubroutineDocumentHighlightSupplier extends AbstractASTDocumentHighlightSupplier {

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

    // Проверяем, является ли токен одним из ключевых слов процедуры/функции
    if (!isSubroutineKeyword(tokenType)) {
      return Collections.emptyList();
    }

    // Ищем метод по дереву символов
    var symbolTree = documentContext.getSymbolTree();
    for (var method : symbolTree.getMethods()) {
      var methodRange = method.getRange();
      if (Ranges.containsPosition(methodRange, position)) {
        return highlightMethod(method, documentContext);
      }
    }

    return Collections.emptyList();
  }

  private boolean isSubroutineKeyword(int tokenType) {
    return tokenType == BSLParser.PROCEDURE_KEYWORD
      || tokenType == BSLParser.ENDPROCEDURE_KEYWORD
      || tokenType == BSLParser.FUNCTION_KEYWORD
      || tokenType == BSLParser.ENDFUNCTION_KEYWORD;
  }

  private List<DocumentHighlight> highlightMethod(MethodSymbol method, DocumentContext documentContext) {
    List<DocumentHighlight> highlights = new ArrayList<>();

    // Получаем Range из AST для ключевых слов
    var ast = documentContext.getAst();

    // Ищем контекст метода в AST по всем subs (включая вложенные)
    findAndHighlightMethod(ast.subs(), method, highlights);

    return highlights;
  }

  private void findAndHighlightMethod(BSLParser.SubsContext subs, MethodSymbol method, List<DocumentHighlight> highlights) {
    if (subs == null) {
      return;
    }

    var methodSubNameRange = method.getSubNameRange();
    for (var sub : subs.sub()) {
      // Сравниваем по имени метода (subName), а не по всему Range
      Range subNameRange;
      if (sub.procedure() != null) {
        subNameRange = Ranges.create(sub.procedure().procDeclaration().subName());
      } else if (sub.function() != null) {
        subNameRange = Ranges.create(sub.function().funcDeclaration().subName());
      } else {
        continue;
      }

      if (subNameRange.equals(methodSubNameRange)) {
        if (method.isFunction()) {
          var function = sub.function();
          if (function != null) {
            addTerminalHighlight(highlights, function.funcDeclaration().FUNCTION_KEYWORD());
            addTerminalHighlight(highlights, function.ENDFUNCTION_KEYWORD());
          }
        } else {
          var procedure = sub.procedure();
          if (procedure != null) {
            addTerminalHighlight(highlights, procedure.procDeclaration().PROCEDURE_KEYWORD());
            addTerminalHighlight(highlights, procedure.ENDPROCEDURE_KEYWORD());
          }
        }
        return;
      }
    }
  }

  private void addTerminalHighlight(List<DocumentHighlight> highlights, TerminalNode node) {
    if (node != null) {
      var range = new Range(
        new org.eclipse.lsp4j.Position(
          node.getSymbol().getLine() - 1,
          node.getSymbol().getCharPositionInLine()
        ),
        new org.eclipse.lsp4j.Position(
          node.getSymbol().getLine() - 1,
          node.getSymbol().getCharPositionInLine() + node.getText().length()
        )
      );
      highlights.add(new DocumentHighlight(range));
    }
  }
}

