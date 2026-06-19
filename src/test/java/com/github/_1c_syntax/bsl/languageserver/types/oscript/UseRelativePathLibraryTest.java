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

  @Autowired
  private HoverProvider hoverProvider;

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

  @Test
  void hoverOnClassNameInNewExpressionFromRelativeUse() {
    initWorkspace();

    var content = "#Использовать \"lib\"\nА = Новый МойКласс();\n";
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    int typeNameStart = content.indexOf("МойКласс") - content.indexOf('\n') - 1;
    var params = new HoverParams();
    params.setPosition(new Position(1, typeNameStart + 2));

    var hover = hoverProvider.getHover(dc, params);

    assertThat(hover)
      .as("hover на имени класса в `Новый МойКласс()` должен быть непустым")
      .isPresent();
    assertThat(hover.get().getContents().getRight().getValue())
      .contains("МойКласс");
  }

  @Test
  void hoverOnClassNameWithDanglingDotMemberAccessBelow() {
    // Точное воспроизведение исходного файла: ниже строки `Новый МойКласс()`
    // есть незавершённое обращение к члену `Клас.Контейнер.` (parse error).
    initWorkspace();

    var content = ""
      + "#Использовать \"lib\"\n"
      + "\n"
      + "Клас = Новый МойКласс();\n"
      + "Клас.Контейнер.\n";
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    var params = new HoverParams();
    params.setPosition(new Position(2, "Клас = Новый Мой".length()));

    var hover = hoverProvider.getHover(dc, params);

    assertThat(hover)
      .as("hover на МойКласс должен работать даже при висячей точке `Клас.Контейнер.` ниже")
      .isPresent();
    assertThat(hover.get().getContents().getRight().getValue())
      .contains("МойКласс");
  }

  @Test
  void hoverOnClassNameInRealConsumingFileInWorkspace() {
    // Точное воспроизведение: курсор наводится в самом файле test.os, который
    // лежит в корне workspace (и сам регистрируется как flat-модуль), а не в
    // постороннем синтетическом документе.
    initWorkspace();

    var fileUri = Path.of("src/test/resources/oscript-libraries/use-relative-path-test/test.os")
      .toAbsolutePath().toUri();
    var content = ""
      + "#Использовать \"lib\"\n"
      + "\n"
      + "Клас = Новый МойКласс();\n";
    var dc = TestUtils.getDocumentContext(fileUri, content, context);

    var params = new HoverParams();
    params.setPosition(new Position(2, "Клас = Новый Мой".length()));

    var hover = hoverProvider.getHover(dc, params);

    assertThat(hover)
      .as("hover на МойКласс после `Новый` в реальном файле workspace должен быть непустым")
      .isPresent();
    assertThat(hover.get().getContents().getRight().getValue())
      .contains("МойКласс");
  }
}
