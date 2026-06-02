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
package com.github._1c_syntax.bsl.languageserver.hover;

import com.github._1c_syntax.bsl.languageserver.context.symbol.KeywordSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.MarkupContent;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import org.eclipse.lsp4j.MarkupKind;
import org.springframework.stereotype.Component;

/**
 * Построитель hover-контента для BSL-keyword'а
 * ({@code Если}, {@code Истина}, {@code Цикл}…).
 * <p>
 * {@link KeywordSymbol} приходит уже с локализованным описанием
 * (выбранным {@link com.github._1c_syntax.bsl.languageserver.references.KeywordReferenceFinder}
 * по текущей локали LS и AST-контексту). Билдер только оборачивает
 * keyword и описание в markdown-формат:
 * <pre>
 * ```bsl
 * &lt;keyword&gt;
 * ```
 *
 * _ключевое слово_
 *
 * &lt;описание&gt;
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class KeywordSymbolMarkupContentBuilder implements MarkupContentBuilder {

  private final Resources resources;

  @Override
  public MarkupContent getContent(Reference reference) {
    var symbol = (KeywordSymbol) reference.symbol();
    var label = resources.getResourceString(getClass(), "keywordLabel");
    var markdown = "```bsl\n" + symbol.getName() + "\n```\n\n_" + label + "_\n\n" + symbol.getDescription();
    return new MarkupContent(MarkupKind.MARKDOWN, markdown);
  }


  @Override
  public Class<? extends Symbol> getSymbolClass() {
    return KeywordSymbol.class;
  }
}
