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
package com.github._1c_syntax.bsl.languageserver.lsif.supplier;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.lsif.LsifEmitter;
import com.github._1c_syntax.bsl.languageserver.providers.DocumentLinkProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Поставщик LSIF-данных для ссылок на документацию.
 * <p>
 * Генерирует вершину documentLinkResult и связывающее ребро для документа.
 * Использует {@link DocumentLinkProvider} для получения ссылок.
 *
 * @see LsifDataSupplier
 * @see DocumentLinkProvider
 */
@Component
@RequiredArgsConstructor
public class DocumentLinkLsifSupplier implements LsifDataSupplier {

  private final DocumentLinkProvider documentLinkProvider;

  /**
   * {@inheritDoc}
   * <p>
   * Генерирует documentLinkResult для ссылок в документе (например, ссылки на внешнюю документацию).
   */
  @Override
  public void supply(DocumentContext documentContext, long documentId, LsifEmitter emitter) {
    var documentLinks = documentLinkProvider.getDocumentLinks(documentContext);

    if (documentLinks.isEmpty()) {
      return;
    }

    // Emit documentLinkResult vertex
    var documentLinkResultId = emitter.emitDocumentLinkResult(documentLinks);

    // Link document to documentLinkResult
    emitter.emitDocumentLinkEdge(documentId, documentLinkResultId);
  }
}
