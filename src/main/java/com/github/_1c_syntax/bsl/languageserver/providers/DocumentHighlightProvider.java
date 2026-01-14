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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.documenthighlight.DocumentHighlightSupplier;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.eclipse.lsp4j.Position;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Провайдер для предоставления подсветки связанных элементов в документе.
 * <p>
 * Обрабатывает запросы {@code textDocument/documentHighlight}.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_documentHighlight">Document Highlight Request specification</a>
 */
@Component
@RequiredArgsConstructor
public final class DocumentHighlightProvider {

  private final List<DocumentHighlightSupplier> suppliers;

  /**
   * Получить подсветки связанных элементов в документе на основе позиции курсора.
   *
   * @param documentContext Контекст документа
   * @param params Параметры запроса document highlight
   * @return Список подсветок связанных элементов
   */
  public List<DocumentHighlight> getDocumentHighlight(DocumentContext documentContext, DocumentHighlightParams params) {
    var terminalNodeInfo = findTerminalNode(params.getPosition(), documentContext);

    return suppliers.stream()
      .flatMap(supplier -> supplier.getDocumentHighlight(params, documentContext, terminalNodeInfo).stream())
      .toList();
  }

  /**
   * Находит терминальный узел на позиции курсора и возвращает информацию о нём.
   * <p>
   * Поддерживает как позицию внутри токена, так и позицию сразу после токена
   * (когда курсор стоит справа от токена).
   *
   * @param position        позиция курсора
   * @param documentContext контекст документа
   * @return информация о терминальном узле
   */
  private Optional<DocumentHighlightSupplier.TerminalNodeInfo> findTerminalNode(
    Position position,
    DocumentContext documentContext
  ) {
    var ast = documentContext.getAst();

    // Сначала пробуем найти токен на текущей позиции
    var maybeTerminalNode = Trees.findTerminalNodeContainsPosition(ast, position);

    // Если не нашли и курсор не в начале строки, пробуем позицию слева (курсор справа от токена)
    if (maybeTerminalNode.isEmpty() && position.getCharacter() > 0) {
      var leftPosition = new Position(position.getLine(), position.getCharacter() - 1);
      maybeTerminalNode = Trees.findTerminalNodeContainsPosition(ast, leftPosition);
    }

    if (maybeTerminalNode.isEmpty()) {
      return Optional.empty();
    }

    var terminalNode = maybeTerminalNode.get();
    var token = terminalNode.getSymbol();
    var tokenType = token.getType();

    return Optional.of(new DocumentHighlightSupplier.TerminalNodeInfo(terminalNode, tokenType));
  }
}
