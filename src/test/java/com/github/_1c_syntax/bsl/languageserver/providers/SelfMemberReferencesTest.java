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
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ReferenceContext;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.nio.file.Path;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Find-references на неквалифицированном self-члене (реквизите self-типа модуля) ссылок
 * НЕ отдаёт: клиенту возвращаются location только по source-defined символам, а у self-члена
 * (синтетический {@code PlatformMemberSymbol}) source-объявления нет. Guard от повторного
 * добавления occurrence-поиска в {@code ReferencesProvider}.
 */
class SelfMemberReferencesTest extends AbstractServerContextAwareTest {

  private static final URI OBJECT_MODULE_URI = Path.of(
    "./src/test/resources/metadata/designer/Catalogs/Справочник1/Ext/ObjectModule.bsl").toUri();

  @Autowired
  private ReferencesProvider referencesProvider;

  @Test
  void bareSelfAttributeReturnsNoReferences() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    var content = """
      Процедура Тест()
        Х = Реквизит1;
        У = Реквизит1;
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(OBJECT_MODULE_URI, content, context);
    try {
      var params = new ReferenceParams();
      params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
      var col = content.split("\n")[1].indexOf("Реквизит1");
      params.setPosition(new Position(1, col));
      params.setContext(new ReferenceContext(true));

      var references = referencesProvider.getReferences(documentContext, params);

      assertThat(references)
        .as("find-references отдаёт location только по source-defined символам; "
          + "у self-члена source-объявления нет — ссылок не возвращаем")
        .isEmpty();
    } finally {
      context.removeDocument(documentContext.getUri());
    }
  }
}
