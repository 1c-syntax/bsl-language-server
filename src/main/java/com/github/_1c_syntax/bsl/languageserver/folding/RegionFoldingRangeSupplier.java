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

import com.github._1c_syntax.bsl.languageserver.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Сапплаер областей сворачивания областей (<code>#Область ... #КонецОбласти</code>).
 */
@Component
@RequiredArgsConstructor
public class RegionFoldingRangeSupplier implements FoldingRangeSupplier {

  private final ClientCapabilitiesHolder clientCapabilitiesHolder;

  // Кэшируется на initialize. Управляет выставлением collapsedText: при отсутствии заявленной
  // клиентом поддержки текст-заглушка свёрнутого блока не передаётся.
  private boolean collapsedTextSupported;

  /**
   * Обработчик события {@link LanguageServerInitializeRequestReceivedEvent}.
   * <p>
   * Кэширует клиентскую возможность {@code textDocument.foldingRange.foldingRange.collapsedText}
   * из сырых возможностей клиента, влияющую на наличие текста-заглушки у областей сворачивания.
   */
  @EventListener(LanguageServerInitializeRequestReceivedEvent.class)
  public void handleInitializeEvent() {
    collapsedTextSupported = FoldingRangeSupplier.isCollapsedTextSupported(
      clientCapabilitiesHolder.getCapabilities()
    );
  }

  @Override
  public List<FoldingRange> getFoldingRanges(DocumentContext documentContext) {
    return documentContext.getSymbolTree().getRegionsFlat().stream()
      .map(regionSymbol -> toFoldingRange(regionSymbol, collapsedTextSupported))
      .collect(Collectors.toList());
  }

  private static FoldingRange toFoldingRange(RegionSymbol regionSymbol, boolean collapsedTextSupported) {

    FoldingRange foldingRange = new FoldingRange(
      regionSymbol.getStartRange().getStart().getLine(),
      regionSymbol.getEndRange().getEnd().getLine()
    );
    foldingRange.setKind(FoldingRangeKind.Region);

    if (collapsedTextSupported) {
      foldingRange.setCollapsedText("Область " + regionSymbol.getName());
    }

    return foldingRange;
  }

}
