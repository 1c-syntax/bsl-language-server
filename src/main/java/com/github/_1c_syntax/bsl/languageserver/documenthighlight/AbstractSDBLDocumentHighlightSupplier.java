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
import com.github._1c_syntax.bsl.parser.SDBLTokenizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.Position;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Базовый класс для поставщиков подсветки в SDBL-запросах.
 * <p>
 * Предоставляет общую функциональность для работы с запросами и подсветки токенов.
 */
public abstract class AbstractSDBLDocumentHighlightSupplier implements DocumentHighlightSupplier {

  /**
   * Информация о найденном токене в запросе.
   *
   * @param tokenizer токенизатор запроса
   * @param token     найденный токен
   */
  protected record QueryTokenInfo(SDBLTokenizer tokenizer, Token token) {
  }

  /**
   * Находит токен на позиции курсора в одном из запросов документа.
   * <p>
   * Поддерживает как позицию внутри токена, так и позицию сразу после токена
   * (когда курсор стоит справа от токена).
   *
   * @param position        позиция курсора
   * @param documentContext контекст документа
   * @return информация о токене, если найден
   */
  protected Optional<QueryTokenInfo> findTokenInQueries(Position position, DocumentContext documentContext) {
    for (var tokenizer : documentContext.getQueries()) {
      var tokens = tokenizer.getTokens();
      for (var token : tokens) {
        var range = Ranges.create(token);
        // Проверяем позицию внутри токена
        if (Ranges.containsPosition(range, position)) {
          return Optional.of(new QueryTokenInfo(tokenizer, token));
        }
        // Проверяем позицию сразу после токена (курсор справа от токена)
        if (range.getEnd().equals(position)) {
          return Optional.of(new QueryTokenInfo(tokenizer, token));
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Добавляет подсветку для токена.
   *
   * @param highlights список подсветок
   * @param token      токен для подсветки (может быть null)
   */
  protected void addTokenHighlight(List<DocumentHighlight> highlights, @Nullable Token token) {
    if (token == null) {
      return;
    }
    var range = Ranges.create(token);
    highlights.add(new DocumentHighlight(range));
  }

  /**
   * Добавляет подсветку для терминального узла.
   *
   * @param highlights   список подсветок
   * @param terminalNode терминальный узел для подсветки (может быть null)
   */
  protected void addTerminalHighlight(List<DocumentHighlight> highlights, @Nullable TerminalNode terminalNode) {
    if (terminalNode == null) {
      return;
    }
    addTokenHighlight(highlights, terminalNode.getSymbol());
  }
}

