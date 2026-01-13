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
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.Position;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Базовый класс для поставщиков подсветки на основе AST.
 * <p>
 * Предоставляет общую функциональность для подсветки токенов, полученных через геттеры ANTLR-контекста.
 */
public abstract class AbstractASTDocumentHighlightSupplier implements DocumentHighlightSupplier {

  /**
   * Результат поиска терминального узла на позиции курсора.
   *
   * @param terminalNode найденный терминальный узел
   * @param tokenType    тип токена
   */
  protected record TerminalNodeInfo(TerminalNode terminalNode, int tokenType) {
  }

  /**
   * Находит терминальный узел на позиции курсора и возвращает информацию о нём.
   *
   * @param position        позиция курсора
   * @param documentContext контекст документа
   * @return информация о терминальном узле, если найден
   */
  protected Optional<TerminalNodeInfo> findTerminalNode(Position position, DocumentContext documentContext) {
    var ast = documentContext.getAst();
    var maybeTerminalNode = Trees.findTerminalNodeContainsPosition(ast, position);

    if (maybeTerminalNode.isEmpty()) {
      return Optional.empty();
    }

    var terminalNode = maybeTerminalNode.get();
    var token = terminalNode.getSymbol();
    var tokenType = token.getType();

    return Optional.of(new TerminalNodeInfo(terminalNode, tokenType));
  }

  /**
   * Добавляет подсветку для токена, полученного из TerminalNode.
   * <p>
   * Используется для прямого доступа к токенам через геттеры ANTLR-контекста.
   *
   * @param highlights Список подсветок, в который будет добавлена подсветка токена
   * @param terminalNode Терминальный узел с токеном (может быть null)
   */
  protected void addTokenHighlight(List<DocumentHighlight> highlights, @Nullable TerminalNode terminalNode) {
    if (terminalNode == null) {
      return;
    }

    var token = terminalNode.getSymbol();
    var range = Ranges.create(token);
    highlights.add(new DocumentHighlight(range));
  }
}
