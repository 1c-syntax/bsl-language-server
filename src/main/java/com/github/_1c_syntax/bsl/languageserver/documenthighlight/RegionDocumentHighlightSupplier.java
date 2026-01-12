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
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Поставщик подсветки для областей (#Region/#EndRegion).
 * <p>
 * При клике на директиву Region или EndRegion подсвечивается соответствующая парная директива.
 * Использует дерево символов для поиска регионов.
 */
@Component
public class RegionDocumentHighlightSupplier implements DocumentHighlightSupplier {

  @Override
  public List<DocumentHighlight> getDocumentHighlight(DocumentHighlightParams params, DocumentContext documentContext) {
    var position = params.getPosition();
    var symbolTree = documentContext.getSymbolTree();
    
    // Получаем все регионы из дерева символов
    var regions = symbolTree.getRegionsFlat();
    
    // Ищем регион, в чьём startRange или endRange находится курсор
    for (var region : regions) {
      // Проверяем, находится ли курсор в начале региона (#Область)
      if (Ranges.containsPosition(region.getStartRange(), position)) {
        return highlightRegion(region);
      }
      
      // Проверяем, находится ли курсор в конце региона (#КонецОбласти)
      if (Ranges.containsPosition(region.getEndRange(), position)) {
        return highlightRegion(region);
      }
    }
    
    return Collections.emptyList();
  }

  private List<DocumentHighlight> highlightRegion(RegionSymbol region) {
    List<DocumentHighlight> highlights = new ArrayList<>();
    
    // Подсвечиваем начало региона
    highlights.add(new DocumentHighlight(region.getStartRange()));
    
    // Подсвечиваем конец региона
    highlights.add(new DocumentHighlight(region.getEndRange()));
    
    return highlights;
  }
}

