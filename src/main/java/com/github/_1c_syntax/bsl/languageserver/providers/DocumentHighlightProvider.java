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
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.springframework.stereotype.Component;

import java.util.List;

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
    return suppliers.stream()
      .flatMap(supplier -> supplier.getDocumentHighlight(params, documentContext).stream())
      .toList();
  }
}
