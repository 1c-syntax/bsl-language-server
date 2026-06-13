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
package com.github._1c_syntax.bsl.languageserver.folding;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeCapabilities;
import org.eclipse.lsp4j.FoldingRangeSupportCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;

import java.util.List;
import java.util.Optional;

/**
 * Базовый интерфейс для наполнения {@link com.github._1c_syntax.bsl.languageserver.providers.FoldingRangeProvider}
 * данными о областях сворачивания.
 */
public interface FoldingRangeSupplier {
  /**
   * Рассчитывает области сворачивания для документа.
   *
   * @param documentContext Контекст документа, для которого надо рассчитать области сворачивания
   * @return Список областей сворачивания
   */
  List<FoldingRange> getFoldingRanges(DocumentContext documentContext);

  /**
   * Вычислить из сырых клиентских возможностей, поддерживает ли клиент свойство
   * {@code collapsedText} у областей сворачивания
   * (см. возможность {@code textDocument.foldingRange.foldingRange.collapsedText}, LSP 3.17).
   * <p>
   * Свойство позволяет серверу задавать осмысленный текст-заглушку свёрнутого блока вместо
   * подстановки клиентом первой строки диапазона. Если клиент не заявил поддержку, сервер
   * не должен выставлять {@link FoldingRange#setCollapsedText(String)}.
   *
   * @param capabilities Заявленные клиентом возможности (могут отсутствовать).
   * @return {@code true}, если клиент заявил поддержку {@code collapsedText}, иначе {@code false}.
   */
  static boolean isCollapsedTextSupported(Optional<ClientCapabilities> capabilities) {
    return capabilities
      .map(ClientCapabilities::getTextDocument)
      .map(TextDocumentClientCapabilities::getFoldingRange)
      .map(FoldingRangeCapabilities::getFoldingRange)
      .map(FoldingRangeSupportCapabilities::getCollapsedText)
      .orElse(Boolean.FALSE);
  }
}
