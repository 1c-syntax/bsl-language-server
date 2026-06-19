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

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.providers.CompletionProvider;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.EntryKind;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.LibraryEntry;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Воспроизведение: рядом с потребляющим скриптом (плоский {@code .os} в корне
 * workspace) лежит каталог-библиотека, подключаемый относительным путём
 * {@code #Использовать "lib"}; автокомплит по классу из этого каталога не работает.
 */
@CleanupContextBeforeClassAndAfterClass
class UseRelativePathLibraryTest extends AbstractServerContextAwareTest {

  @Autowired
  private OScriptLibraryIndex index;

  @Autowired
  private CompletionProvider completionProvider;

  private void initWorkspace() {
    var fixtureRoot = Path.of("src/test/resources/oscript-libraries/use-relative-path-test").toAbsolutePath();
    initServerContext(fixtureRoot, false);
    index.reindex(context);
  }

  @Test
  void classFromRelativeLibIsIndexedDespiteFlatScriptInWorkspaceRoot() {
    initWorkspace();

    assertThat(index.findEntries(EntryKind.CLASS))
      .extracting(LibraryEntry::qualifiedName)
      .contains("МойКласс");
  }

  @Test
  void noDotCompletionAfterNovyiSurfacesClassFromRelativeUse() {
    initWorkspace();

    var content = "#Использовать \"lib\"\nА = Новый Мой";
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(1, "А = Новый Мой".length()));

    var items = completionProvider.getCompletion(dc, params).getItems();

    assertThat(items)
      .extracting(CompletionItem::getLabel)
      .contains("МойКласс");
  }
}
