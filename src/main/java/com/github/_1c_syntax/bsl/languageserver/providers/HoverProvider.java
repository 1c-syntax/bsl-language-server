/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.hover.MarkupContentBuilder;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Провайдер для отображения всплывающих подсказок при наведении курсора.
 * <p>
 * Обрабатывает запросы {@code textDocument/hover}.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_hover">Hover Request specification</a>
 */
@Component
@RequiredArgsConstructor
public final class HoverProvider {

  private final ReferenceResolver referenceResolver;
  private final Map<SymbolKind, MarkupContentBuilder<Symbol>> markupContentBuilders;

  /**
   * Получить информацию для отображения при наведении курсора на символ.
   *
   * @param documentContext Контекст документа
   * @param params Параметры запроса hover
   * @return Информация для отображения во всплывающей подсказке
   */
  public Optional<Hover> getHover(DocumentContext documentContext, HoverParams params) {
    Position position = params.getPosition();

    return referenceResolver.findReference(documentContext.getUri(), position)
      .flatMap((Reference reference) -> {
        var symbol = reference.getSymbol();
        var range = reference.getSelectionRange();

        return Optional.ofNullable(markupContentBuilders.get(symbol.getSymbolKind()))
          .map(markupContentBuilder -> markupContentBuilder.getContent(symbol))
          .map(content -> new Hover(content, range));
      });
  }

}
