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
package com.github._1c_syntax.bsl.languageserver.types.oscript;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.providers.CompletionProvider;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет, что implicit-записи {@link OScriptLibraryIndex} скрываются
 * из no-dot completion по умолчанию и появляются при включении
 * {@code oscript.showImplicitLibraryEntriesInCompletion}.
 */
@CleanupContextBeforeClassAndAfterClass
class ImplicitEntryCompletionFilterTest extends AbstractServerContextAwareTest {

  private static final String FIXTURE_DIR = "src/test/resources/oscript-libraries/implicit-test";
  private static final String LIB_NAME = "implicit-test";
  private static final String IMPLICIT_CLASS_NAME = "InternalSecret";
  private static final String PUBLIC_CLASS_NAME = "PublicHello";

  @Autowired
  private CompletionProvider completionProvider;

  @Autowired
  private OScriptLibraryIndex index;

  @Autowired
  private LanguageServerConfiguration languageServerConfiguration;

  @AfterEach
  void resetFlag() {
    languageServerConfiguration.getOscriptOptions().setShowImplicitLibraryEntriesInCompletion(false);
  }

  @Test
  void implicitEntryHiddenInNoDotCompletionByDefault() {
    // given
    setupFixtureWithImplicitEntry();
    var line = "Перем X = Новый Inter";
    var dc = getDocumentContextWithUseDirective(line);
    var params = paramsAtEndOfLine(line);

    // when
    var items = completionProvider.getCompletion(dc, params).getItems();

    // then
    var labels = items.stream().map(CompletionItem::getLabel).toList();
    assertThat(labels.contains(IMPLICIT_CLASS_NAME))
      .as("при default-флаге implicit-класс не должен попадать в подсказки после `Новый`")
      .isFalse();
  }

  @Test
  void implicitEntryShownWhenFlagEnabled() {
    // given
    setupFixtureWithImplicitEntry();
    languageServerConfiguration.getOscriptOptions().setShowImplicitLibraryEntriesInCompletion(true);
    var line = "Перем X = Новый Inter";
    var dc = getDocumentContextWithUseDirective(line);
    var params = paramsAtEndOfLine(line);

    // when
    var items = completionProvider.getCompletion(dc, params).getItems();

    // then
    assertThat(items)
      .extracting(CompletionItem::getLabel)
      .as("при включённом флаге implicit-класс предлагается в no-dot completion")
      .contains(IMPLICIT_CLASS_NAME);
  }

  @Test
  void publicEntryRemainsVisibleAtDefaultFlag() {
    // given
    setupFixtureWithImplicitEntry();
    var line = "Перем X = Новый Public";
    var dc = getDocumentContextWithUseDirective(line);
    var params = paramsAtEndOfLine(line);

    // when
    var items = completionProvider.getCompletion(dc, params).getItems();

    // then
    assertThat(items)
      .extracting(CompletionItem::getLabel)
      .as("фильтр по implicit не должен задевать публичные классы из lib.config")
      .contains(PUBLIC_CLASS_NAME);
  }

  private void setupFixtureWithImplicitEntry() {
    var fixtureRoot = Path.of(FIXTURE_DIR).toAbsolutePath();
    initServerContext(fixtureRoot, false);
    index.reindex(context);
    var internalFile = fixtureRoot.resolve("src/" + IMPLICIT_CLASS_NAME + ".os");
    index.registerEntry(
      IMPLICIT_CLASS_NAME,
      internalFile,
      OScriptLibraryIndex.EntryKind.CLASS,
      context,
      LIB_NAME,
      true
    );
  }

  private DocumentContext getDocumentContextWithUseDirective(String line) {
    var content = "#Использовать " + LIB_NAME + "\n" + line;
    return TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);
  }

  private static CompletionParams paramsAtEndOfLine(String line) {
    var params = new CompletionParams();
    params.setPosition(new Position(1, line.length()));
    return params;
  }
}
