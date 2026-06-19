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
import com.github._1c_syntax.bsl.languageserver.providers.HoverProvider;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.EntryKind;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.LibraryEntry;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Регресс на ТОЧНЫХ файлах из архива пользователя
 * ({@code oscript-libraries/archive-repro}): каталог {@code lib} подключается
 * относительным путём {@code #Использовать "lib"}, а класс лежит в
 * {@code lib/src/Классы/МойКласс.os}.
 * <p>
 * Особенность архива (zip с macOS): имя файла {@code МойКласс.os} хранится в
 * форме NFD ({@code й} = {@code и} + U+0306), тогда как идентификатор
 * {@code МойКласс} в коде — в NFC. Без NFC-нормализации имени library-класса
 * автодополнение/hover не находят класс.
 */
@CleanupContextBeforeClassAndAfterClass
class ArchiveReproDiagnosticTest extends AbstractServerContextAwareTest {

  @Autowired
  private OScriptLibraryIndex index;

  @Autowired
  private CompletionProvider completionProvider;

  @Autowired
  private HoverProvider hoverProvider;

  private static final Path FIXTURE_ROOT =
    Path.of("src/test/resources/oscript-libraries/archive-repro").toAbsolutePath();

  private void initWorkspace() {
    initServerContext(FIXTURE_ROOT, false);
    index.reindex(context);
  }

  @Test
  void libIsDiscoveredAndClassIsIndexedUnderNfcName() {
    initWorkspace();

    var entry = index.findByName("МойКласс");
    assertThat(entry)
      .as("МойКласс (NFC, как в коде) должен находиться в индексе несмотря на NFD-имя файла")
      .isPresent();
    assertThat(entry.get().kind()).isEqualTo(EntryKind.CLASS);
    assertThat(entry.get().libOrigin())
      .as("класс принадлежит библиотеке lib (каталог, подключённый по относительному пути)")
      .isEqualTo("lib");
    assertThat(entry.get().implicit()).isFalse();

    assertThat(index.findEntries(EntryKind.CLASS))
      .extracting(LibraryEntry::qualifiedName)
      .contains("МойКласс");
  }

  @Test
  void noDotCompletionAfterNovyiSurfacesClass() {
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

  @Test
  void hoverOnClassNameInNewExpression() {
    initWorkspace();

    var content = "#Использовать \"lib\"\nА = Новый МойКласс();\n";
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    int typeNameStart = content.indexOf("МойКласс") - content.indexOf('\n') - 1;
    var params = new HoverParams();
    params.setPosition(new Position(1, typeNameStart + 2));

    var hover = hoverProvider.getHover(dc, params);

    assertThat(hover)
      .as("hover на МойКласс после `Новый` должен быть непустым")
      .isPresent();
    assertThat(hover.get().getContents().getRight().getValue()).contains("МойКласс");
  }
}
