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

import com.github._1c_syntax.bsl.languageserver.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.folding.FoldingRangeSupplier;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeCapabilities;
import org.eclipse.lsp4j.FoldingRangeSupportCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Провайдер для предоставления информации о сворачиваемых областях кода.
 * <p>
 * Обрабатывает запросы {@code textDocument/foldingRange}.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_foldingRange">Folding Range specification</a>
 */
@Component
@RequiredArgsConstructor
public final class FoldingRangeProvider {

  private final List<FoldingRangeSupplier> foldingRangeSuppliers;
  private final ClientCapabilitiesHolder clientCapabilitiesHolder;

  /**
   * Получить список сворачиваемых областей в документе.
   * <p>
   * Сапплаеры всегда заполняют осмысленный текст-заглушку свёрнутого блока
   * ({@link FoldingRange#setCollapsedText(String)}). Если клиент не заявил поддержку возможности
   * {@code textDocument.foldingRange.foldingRange.collapsedText} (LSP 3.17), провайдер сбрасывает
   * этот текст в {@code null}, чтобы клиент подставил собственную заглушку.
   *
   * @param documentContext Контекст документа
   * @return Список областей, которые можно свернуть
   */
  public List<FoldingRange> getFoldingRange(DocumentContext documentContext) {
    var foldingRanges = foldingRangeSuppliers.stream()
      .map(foldingRangeSupplier -> foldingRangeSupplier.getFoldingRanges(documentContext))
      .flatMap(Collection::stream)
      .collect(Collectors.toList());

    if (!isCollapsedTextSupported(clientCapabilitiesHolder.getCapabilities())) {
      foldingRanges.forEach(foldingRange -> foldingRange.setCollapsedText(null));
    }

    return foldingRanges;
  }

  /**
   * Вычислить из сырых клиентских возможностей, поддерживает ли клиент свойство
   * {@code collapsedText} у областей сворачивания
   * (см. возможность {@code textDocument.foldingRange.foldingRange.collapsedText}, LSP 3.17).
   *
   * @param capabilities Заявленные клиентом возможности (могут отсутствовать).
   * @return {@code true}, если клиент заявил поддержку {@code collapsedText}, иначе {@code false}.
   */
  private static boolean isCollapsedTextSupported(Optional<ClientCapabilities> capabilities) {
    return capabilities
      .map(ClientCapabilities::getTextDocument)
      .map(TextDocumentClientCapabilities::getFoldingRange)
      .map(FoldingRangeCapabilities::getFoldingRange)
      .map(FoldingRangeSupportCapabilities::getCollapsedText)
      .orElse(Boolean.FALSE);
  }

}
