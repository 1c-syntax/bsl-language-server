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
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Сапплаер областей сворачивания областей (<code>#Область ... #КонецОбласти</code>).
 */
@Component
public class RegionFoldingRangeSupplier implements FoldingRangeSupplier {

  @Override
  public List<FoldingRange> getFoldingRanges(DocumentContext documentContext) {
    var regionKeyword = Resources.getResourceString(
      documentContext.getScriptVariantLanguage(), RegionFoldingRangeSupplier.class, "regionKeyword");
    return documentContext.getSymbolTree().getRegionsFlat().stream()
      .map(regionSymbol -> toFoldingRange(regionSymbol, regionKeyword))
      .collect(Collectors.toList());
  }

  private static FoldingRange toFoldingRange(RegionSymbol regionSymbol, String regionKeyword) {

    FoldingRange foldingRange = new FoldingRange(
      regionSymbol.getStartRange().getStart().getLine(),
      regionSymbol.getEndRange().getEnd().getLine()
    );
    foldingRange.setKind(FoldingRangeKind.Region);
    foldingRange.setCollapsedText(regionKeyword + " " + humanizeName(regionSymbol.getName()));

    return foldingRange;
  }

  /**
   * Преобразовать CamelCase-идентификатор области в читаемую фразу: разбить на слова по границам
   * заглавных букв и групп символов, первое слово оставить с заглавной буквы, остальные привести
   * к нижнему регистру, объединив слова пробелами.
   * <p>
   * Например, {@code СлужебныеПроцедурыИФункции} превращается в {@code Служебные процедуры и функции}.
   *
   * @param name Имя области (CamelCase-идентификатор).
   * @return Читаемая фраза из слов имени области, разделённых пробелами.
   */
  private static String humanizeName(String name) {
    var words = StringUtils.splitByCharacterTypeCamelCase(name);
    return IntStream.range(0, words.length)
      .mapToObj(index -> index == 0 ? words[index] : words[index].toLowerCase(Locale.ROOT))
      .collect(Collectors.joining(" "));
  }

}
