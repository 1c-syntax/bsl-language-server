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
package com.github._1c_syntax.bsl.languageserver.semantictokens;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.SemanticTokensTestHelper;
import com.github._1c_syntax.bsl.languageserver.util.SemanticTokensTestHelper.ExpectedToken;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.lsp4j.SemanticTokenModifiers.DefaultLibrary;

/**
 * Разметка цепочки обращения к дереву метаданных
 * ({@code Метаданные.Справочники.Справочник1.Реквизиты.Реквизит1.Тип}).
 * <p>
 * Само свойство {@code Метаданные} объявлено только в синтакс-помощнике — в
 * JSON-фолбэке его нет, и цепочка не резолвится вовсе, поэтому тест требует
 * установленной 1С.
 */
@CleanupContextBeforeClassAndAfterEachTestMethod
@Import(SemanticTokensTestHelper.class)
@TestPropertySource(properties = "app.platform-context.enabled=true")
@EnabledIfEnvironmentVariable(named = "BSL_LANGUAGE_SERVER_RUN_HBK_TESTS",
  matches = "true",
  disabledReason = "Требует HBK 1С (свойство `Метаданные` объявлено только в синтакс-помощнике)")
class MetadataChainSemanticTokensHbkTest extends AbstractServerContextAwareTest {

  @Autowired
  private GlobalScopeSemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensTestHelper helper;

  @Test
  void wholeMetadataChainIsPainted() {
    initServerContext(TestUtils.PATH_TO_METADATA);
    var bsl = """
      Процедура Тест()
          А = Метаданные.Справочники.Справочник1.Реквизиты.Реквизит1.Тип;
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(bsl, context);

    var decoded = helper.decodeFromEntries(supplier.getSemanticTokens(documentContext));

    // Корень — платформенная коллекция; коллекции внутри дерева — свойства;
    // имена объектов метаданных читаются в коде как имена объектов, поэтому Class.
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 8, 10, SemanticTokenTypes.Class, Set.of(DefaultLibrary), "Метаданные"),
      new ExpectedToken(1, 19, 11, SemanticTokenTypes.Property, Set.of(DefaultLibrary), "Справочники"),
      new ExpectedToken(1, 31, 11, SemanticTokenTypes.Class, "Справочник1"),
      new ExpectedToken(1, 43, 9, SemanticTokenTypes.Property, Set.of(DefaultLibrary), "Реквизиты"),
      new ExpectedToken(1, 53, 9, SemanticTokenTypes.Class, "Реквизит1"),
      new ExpectedToken(1, 63, 3, SemanticTokenTypes.Property, Set.of(DefaultLibrary), "Тип")
    ));
  }

  @Test
  void chainBreaksOnUnknownMember() {
    initServerContext(TestUtils.PATH_TO_METADATA);
    var bsl = """
      Процедура Тест()
          А = Метаданные.Справочники.НетТакогоСправочника.Реквизиты;
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(bsl, context);

    var decoded = helper.decodeFromEntries(supplier.getSemanticTokens(documentContext));

    // Несуществующее имя не красится, и дальше по цепочке разметка не идёт:
    // выдуманный тип — хуже, чем отсутствие подсветки.
    assertThat(decoded)
      .filteredOn(token -> token.start() >= 31)
      .isEmpty();
  }
}
