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
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.nio.file.Path;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hover на неквалифицированном self-члене (реквизите/платформенном методе self-типа
 * модуля объекта). Индексируется как обращение к {@code PlatformMemberSymbol}
 * ({@code ReferenceIndexFiller}), поэтому hover идёт тем же путём, что и у обычных
 * членов — через {@code ReferenceIndex} и {@code PlatformMemberSymbolMarkupContentBuilder}.
 */
class SelfMemberHoverTest extends AbstractServerContextAwareTest {

  private static final URI OBJECT_MODULE_URI = Path.of(
    "./src/test/resources/metadata/designer/Catalogs/Справочник1/Ext/ObjectModule.bsl").toUri();

  @Autowired
  private HoverProvider hoverProvider;

  @Test
  void hoverOnBareSelfAttributeResolvesToItsMember() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    var content = """
      Процедура Тест()
        Х = Реквизит1;
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(OBJECT_MODULE_URI, content, context);
    try {
      var params = new HoverParams();
      var col = content.split("\n")[1].indexOf("Реквизит1");
      params.setPosition(new Position(1, col));

      var hover = hoverProvider.getHover(documentContext, params);

      assertThat(hover)
        .as("hover на голом self-реквизите должен резолвиться в его член типа объекта")
        .isPresent();
      assertThat(hover.get().getContents().getRight().getValue())
        .as("содержимое hover'а описывает именно этот реквизит")
        .contains("Реквизит1");
    } finally {
      context.removeDocument(documentContext.getUri());
    }
  }

  @Test
  void hoverOnBareSelfPlatformMethodResolvesToItsMember() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    var content = """
      Процедура Тест()
        Записать();
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(OBJECT_MODULE_URI, content, context);
    try {
      var params = new HoverParams();
      var col = content.split("\n")[1].indexOf("Записать");
      params.setPosition(new Position(1, col));

      var hover = hoverProvider.getHover(documentContext, params);

      assertThat(hover)
        .as("hover на голом self-методе (Записать) должен резолвиться в платформенный метод")
        .isPresent();
      assertThat(hover.get().getContents().getRight().getValue())
        .as("содержимое hover'а описывает именно метод Записать")
        .contains("Записать");
    } finally {
      context.removeDocument(documentContext.getUri());
    }
  }
}
