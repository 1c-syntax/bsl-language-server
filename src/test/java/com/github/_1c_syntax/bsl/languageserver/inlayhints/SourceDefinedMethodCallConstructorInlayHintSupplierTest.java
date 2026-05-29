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
package com.github._1c_syntax.bsl.languageserver.inlayhints;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Inlay-хинты имён параметров для вызова конструктора OneScript library-класса
 * {@code Новый <ИмяКласса>(...)}: ссылка резолвится в {@code ConstructorSymbol},
 * а supplier должен подставить имена параметров конструктора по позиционным
 * аргументам.
 */
@CleanupContextBeforeClassAndAfterClass
class SourceDefinedMethodCallConstructorInlayHintSupplierTest extends AbstractServerContextAwareTest {

  private static final String FIXTURE_DIR = "src/test/resources/oscript-libraries/constructor-inlay-test";
  private static final String CALLER_PATH = FIXTURE_DIR + "/src/Классы/Caller.os";

  @Autowired
  private SourceDefinedMethodCallInlayHintSupplier supplier;

  @Autowired
  private OScriptLibraryIndex index;

  @Test
  void testConstructorCallInlayHints() {

    // given
    initServerContext(Path.of(FIXTURE_DIR).toAbsolutePath());
    index.reindex(context);

    var documentContext = TestUtils.getDocumentContextFromFile(CALLER_PATH, context);

    var textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());
    var range = documentContext.getSymbolTree().getMethods().getFirst().getRange();
    var params = new InlayHintParams(textDocumentIdentifier, range);

    // when
    List<InlayHint> inlayHints = supplier.getInlayHints(documentContext, params);

    // then
    assertThat(inlayHints)
      .hasSize(2)
      .anySatisfy(inlayHint -> {
        assertThat(inlayHint.getLabel()).isEqualTo(Either.forLeft("Имя:"));
        assertThat(inlayHint.getKind()).isEqualTo(InlayHintKind.Parameter);
      })
      .anySatisfy(inlayHint -> {
        assertThat(inlayHint.getLabel()).isEqualTo(Either.forLeft("Значение:"));
        assertThat(inlayHint.getKind()).isEqualTo(InlayHintKind.Parameter);
      })
    ;
  }
}
