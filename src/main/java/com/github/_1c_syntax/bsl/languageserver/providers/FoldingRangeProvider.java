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

import com.github._1c_syntax.bsl.languageserver.lsp.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import com.github._1c_syntax.bsl.languageserver.folding.FoldingRangeSupplier;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeCapabilities;
import org.eclipse.lsp4j.FoldingRangeSupportCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
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

  // Кэшируется на initialize. collapsedTextSupported — gate для текста-заглушки свёрнутого блока
  // (textDocument.foldingRange.foldingRange.collapsedText, LSP 3.17). Если клиент не заявил
  // поддержку, текст сбрасывается в null, чтобы клиент подставил собственную заглушку.
  private boolean collapsedTextSupported;

  // Кэшируется на initialize. rangeLimit — максимальное число сворачиваемых областей, которое
  // готов принять клиент (textDocument.foldingRange.rangeLimit). null означает отсутствие лимита.
  @Nullable
  private Integer rangeLimit;

  /**
   * Обработчик события {@link LanguageServerInitializeRequestReceivedEvent}.
   * <p>
   * Кэширует клиентские возможности секции {@code textDocument.foldingRange}:
   * <ul>
   *   <li>{@code foldingRange.collapsedText} (LSP 3.17) — определяет, отдавать ли клиенту
   *   текст-заглушку свёрнутого блока;</li>
   *   <li>{@code rangeLimit} — максимальное число сворачиваемых областей, которое готов принять
   *   клиент. Отсутствие значения трактуется как отсутствие лимита.</li>
   * </ul>
   */
  @EventListener(LanguageServerInitializeRequestReceivedEvent.class)
  public void handleInitializeEvent() {
    var foldingRangeCapabilities = clientCapabilitiesHolder.getCapabilities()
      .map(ClientCapabilities::getTextDocument)
      .map(TextDocumentClientCapabilities::getFoldingRange);

    collapsedTextSupported = foldingRangeCapabilities
      .map(FoldingRangeCapabilities::getFoldingRange)
      .map(FoldingRangeSupportCapabilities::getCollapsedText)
      .orElse(Boolean.FALSE);

    rangeLimit = foldingRangeCapabilities
      .map(FoldingRangeCapabilities::getRangeLimit)
      .orElse(null);
  }

  /**
   * Получить список сворачиваемых областей в документе.
   * <p>
   * Сапплаеры всегда заполняют осмысленный текст-заглушку свёрнутого блока
   * ({@link FoldingRange#setCollapsedText(String)}). Если клиент не заявил поддержку возможности
   * {@code textDocument.foldingRange.foldingRange.collapsedText} (LSP 3.17), провайдер сбрасывает
   * этот текст в {@code null}, чтобы клиент подставил собственную заглушку.
   * <p>
   * Если клиент заявил лимит на число областей ({@code textDocument.foldingRange.rangeLimit})
   * и вычисленный список его превышает, список усекается до лимита с приоритизацией наиболее
   * полезных областей (см. {@link #applyRangeLimit(List)}). Если лимит не заявлен, возвращаются
   * все области без изменений.
   *
   * @param documentContext Контекст документа
   * @return Список областей, которые можно свернуть
   */
  public List<FoldingRange> getFoldingRange(DocumentContext documentContext) {
    var foldingRanges = foldingRangeSuppliers.stream()
      .map(foldingRangeSupplier -> foldingRangeSupplier.getFoldingRanges(documentContext))
      .flatMap(Collection::stream)
      .collect(Collectors.toList());

    if (!collapsedTextSupported) {
      foldingRanges.forEach(foldingRange -> foldingRange.setCollapsedText(null));
    }

    return applyRangeLimit(foldingRanges);
  }

  /**
   * Усечь список сворачиваемых областей до заявленного клиентом лимита
   * ({@code textDocument.foldingRange.rangeLimit}).
   * <p>
   * Если лимит не заявлен ({@code rangeLimit == null}) или список не превышает лимит, список
   * возвращается без изменений.
   * <p>
   * Правило приоритизации при усечении: сохраняются наиболее полезные внешние/верхнеуровневые
   * области, поскольку клиент при превышении лимита усекает список произвольно и может скрыть
   * именно крупные блоки (области {@code #Область}, тела методов), оставив мелкие вложенные.
   * Области сортируются по убыванию размаха (число охватываемых строк, {@code endLine - startLine}):
   * чем крупнее область, тем выше её приоритет. Тем самым в выдачу попадают верхнеуровневые области,
   * а глубоко вложенные мелкие диапазоны отбрасываются первыми. При равном размахе сохраняется
   * относительный порядок (стабильная сортировка), что обеспечивает детерминированный результат.
   *
   * @param foldingRanges Полный список вычисленных сворачиваемых областей
   * @return Список, усечённый до лимита по приоритету, либо исходный список, если усечение не нужно
   */
  private List<FoldingRange> applyRangeLimit(List<FoldingRange> foldingRanges) {
    if (rangeLimit == null || foldingRanges.size() <= rangeLimit) {
      return foldingRanges;
    }

    return foldingRanges.stream()
      .sorted(Comparator.comparingInt(
        (FoldingRange foldingRange) -> foldingRange.getEndLine() - foldingRange.getStartLine())
        .reversed())
      .limit(rangeLimit)
      .collect(Collectors.toList());
  }

}
