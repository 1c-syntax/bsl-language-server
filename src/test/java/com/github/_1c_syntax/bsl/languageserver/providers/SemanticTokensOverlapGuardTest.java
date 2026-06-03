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

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guard: сапплаеры не должны выдавать пересекающиеся токены на одном участке кода.
 * <p>
 * Собирает токены тем же путём, что и провайдер ({@link SemanticTokensProvider#collectTokens}),
 * на модуле из реальной конфигурации (с populateContext, чтобы работал инференс
 * типов) и проверяет отсутствие конфликтов через {@link TokenOverlaps#findOverlaps}.
 * Регрессионная защита: например, доступ {@code РегистрыСведений.РегистрСведений1}
 * красится GlobalScope как {@code Class}, и сапплаер свойств не должен накладывать
 * на тот же токен {@code Property}.
 */
@CleanupContextBeforeClassAndAfterClass
class SemanticTokensOverlapGuardTest extends AbstractServerContextAwareTest {

  private static final String MODULE =
    PATH_TO_METADATA + "/CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl";

  @Autowired
  private SemanticTokensProvider provider;

  @Autowired
  private SemanticTokensLegend legend;

  @Test
  void suppliersDoNotProduceOverlappingTokens() {
    // given — поднимаем конфигурацию с инференсом типов.
    initServerContext(PATH_TO_METADATA);
    var documentContext = TestUtils.getDocumentContextFromFile(MODULE, context);

    // when — собираем токены тем же путём, что и провайдер для full-запроса.
    var allTokens = provider.collectTokens(documentContext);
    var lines = documentContext.getContentList();
    var overlaps = TokenOverlaps.findOverlaps(allTokens,
      line -> line >= 0 && line < lines.length ? lines[line].length() : 0);

    // then — пересечений быть не должно.
    assertThat(overlaps)
      .as("Конфликты подсветки: %s", describe(overlaps, documentContext))
      .isEmpty();
  }

  private String describe(List<TokenOverlaps.TokenOverlap> overlaps, DocumentContext documentContext) {
    var lines = documentContext.getContentList();
    var sb = new StringBuilder();
    for (var overlap : overlaps) {
      var a = overlap.first();
      var b = overlap.second();
      var src = a.line() < lines.length ? lines[a.line()].strip() : "";
      sb.append(String.format("%nL%d c%d: %s/%d vs %s/%d | %s",
        a.line() + 1, a.start(), typeName(a.type()), a.modifiers(),
        typeName(b.type()), b.modifiers(), src));
    }
    return sb.toString();
  }

  private String typeName(int idx) {
    var types = legend.getTokenTypes();
    return idx >= 0 && idx < types.size() ? types.get(idx) : ("?" + idx);
  }
}
