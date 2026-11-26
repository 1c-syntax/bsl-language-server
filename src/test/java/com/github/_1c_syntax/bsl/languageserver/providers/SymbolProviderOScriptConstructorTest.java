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
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Регресс на {@link SymbolProvider#getSymbols(WorkspaceSymbolParams, CancelChecker)} и
 * {@link DocumentSymbolProvider#getDocumentSymbols}: до выделения
 * {@code ConstructorSymbol} в отдельный {@link SymbolKind#Constructor} фильтр
 * workspace symbol search поддерживал только {@link SymbolKind#Method}/{@link
 * SymbolKind#Variable}, а IDE-outline не имела возможности отрисовать иконку
 * конструктора отдельно от обычного метода.
 */
@CleanupContextBeforeClassAndAfterClass
class SymbolProviderOScriptConstructorTest extends AbstractServerContextAwareTest {

  private static final String FIXTURE_DIR = "src/test/resources/oscript-libraries/internal-classes-test";
  private static final String CLASS_WITH_CTOR_FILE =
    FIXTURE_DIR + "/oscript_modules/internal-classes-lib/src/Классы/PublicEntity.os";

  @Autowired
  private SymbolProvider symbolProvider;

  @Autowired
  private DocumentSymbolProvider documentSymbolProvider;

  @Test
  void workspaceSymbolSearchIncludesOscriptConstructors() {
    // given
    initServerContext(Path.of(FIXTURE_DIR).toAbsolutePath(), true);

    // when
    var symbols = symbolProvider.getSymbols(new WorkspaceSymbolParams("ПриСозданииОбъекта"), () -> {
      // no-op: проверка отмены не требуется в тесте поиска
    });

    // then
    assertThat(symbols)
      .as("ПриСозданииОбъекта из OScript-классов должен попадать в workspace symbol search")
      .isNotEmpty()
      .allMatch(symbol -> symbol.getName().equalsIgnoreCase("ПриСозданииОбъекта"))
      .anyMatch(symbol -> symbol.getKind() == SymbolKind.Constructor);
  }

  @Test
  void documentSymbolOutlineExposesConstructorKind() {
    // given: OScript-класс с ПриСозданииОбъекта — конструктор должен попасть
    // в outline с SymbolKind.Constructor, чтобы IDE отрисовала соответствующую иконку.
    initServerContext(Path.of(FIXTURE_DIR).toAbsolutePath(), true);
    var dc = TestUtils.getDocumentContextFromFile(CLASS_WITH_CTOR_FILE, context);

    // when
    var documentSymbols = documentSymbolProvider.getDocumentSymbols(dc);

    // then
    assertThat(documentSymbols)
      .filteredOn(symbol -> symbol.getName().equalsIgnoreCase("ПриСозданииОбъекта"))
      .hasSize(1)
      .allMatch(symbol -> symbol.getKind() == SymbolKind.Constructor);
  }
}
