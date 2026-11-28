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
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.hover.MarkupContentBuilder;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Провайдер для отображения всплывающих подсказок при наведении курсора.
 *
 * <p>Тонкий слой поверх {@link ReferenceResolver}: резолвит ссылку под курсором
 * и выбирает {@link MarkupContentBuilder} по классу разрешённого символа.
 * Никакой собственной логики поиска символов или типов: всё, что относится к
 * подбору ссылки, живёт в реализациях {@link com.github._1c_syntax.bsl.languageserver.references.ReferenceFinder}
 * (в том числе synthetic-символы для аннотаций и keyword'ов —
 * {@link com.github._1c_syntax.bsl.languageserver.references.AnnotationReferenceFinder},
 * {@link com.github._1c_syntax.bsl.languageserver.types.references.KeywordReferenceFinder}),
 * всё, что относится к формированию текста подсказки — в соответствующем
 * {@code MarkupContentBuilder}.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_hover">Hover Request specification</a>
 */
@Component
@RequiredArgsConstructor
public final class HoverProvider {

  private final ReferenceResolver referenceResolver;
  private final Map<Class<? extends Symbol>, MarkupContentBuilder> markupContentBuilders;

  public Optional<Hover> getHover(DocumentContext documentContext, HoverParams params) {
    return referenceResolver.findReference(documentContext.getUri(), params.getPosition())
      .flatMap(reference -> findBuilder(reference.symbol())
        .map(builder -> builder.getContent(reference))
        .map(content -> new Hover(content, reference.selectionRange())));
  }

  private Optional<MarkupContentBuilder> findBuilder(Symbol symbol) {
    var direct = markupContentBuilders.get(symbol.getClass());
    if (direct != null) {
      return Optional.of(direct);
    }
    return markupContentBuilders.entrySet().stream()
      .filter(entry -> entry.getKey().isInstance(symbol))
      .map(Map.Entry::getValue)
      .findFirst();
  }
}
