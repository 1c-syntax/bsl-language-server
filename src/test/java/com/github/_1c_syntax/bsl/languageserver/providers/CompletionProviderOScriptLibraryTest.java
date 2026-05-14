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
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class CompletionProviderOScriptLibraryTest extends AbstractServerContextAwareTest {

  @Autowired
  private CompletionProvider completionProvider;

  @Autowired
  private OScriptLibraryIndex index;

  @Test
  void noDotCompletionSurfacesLibraryModuleAsModule() {
    initLib();

    var content = "MyMod";
    var dc = TestUtils.getDocumentContext(content, context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(0, content.length()));

    var items = completionProvider.getCompletion(dc, params);

    assertThat(items)
      .filteredOn(it -> "MyModule".equals(it.getLabel()))
      .singleElement()
      .extracting(CompletionItem::getKind)
      .isEqualTo(CompletionItemKind.Module);
  }

  @Test
  void noDotCompletionAfterNovyiSurfacesLibraryClassAsClass() {
    initLib();

    var content = "А = Новый MyCl";
    var dc = TestUtils.getDocumentContext(content, context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(0, content.length()));

    var items = completionProvider.getCompletion(dc, params);

    assertThat(items)
      .filteredOn(it -> "MyClass".equals(it.getLabel()))
      .singleElement()
      .extracting(CompletionItem::getKind)
      .isEqualTo(CompletionItemKind.Class);
  }

  @Test
  void dotCompletionAfterLibraryModuleReturnsItsMembers() {
    initLib();

    var content = "MyModule.";
    var dc = TestUtils.getDocumentContext(content, context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(0, content.length()));

    var items = completionProvider.getCompletion(dc, params);

    assertThat(items)
      .extracting(CompletionItem::getLabel)
      .contains("ВывестиСообщение", "СформироватьСтроку", "СтатусМодуля");
  }

  @Test
  void noDotCompletionGatedByUseDirectivesShowsOnlyDeclaredLibs() {
    initLib();

    // Документ с #Использовать на другую (несуществующую) либу — наш MyModule
    // не должен попасть в список.
    var content = "#Использовать someother\nMyMod";
    var dc = TestUtils.getDocumentContext(content, context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(1, "MyMod".length()));

    var items = completionProvider.getCompletion(dc, params);

    assertThat(items)
      .filteredOn(it -> "MyModule".equals(it.getLabel()))
      .isEmpty();
  }

  @Test
  void noDotCompletionGatedByUseDirectivesShowsLibAfterImport() {
    initLib();

    var content = "#Использовать mylib\nMyMod";
    var dc = TestUtils.getDocumentContext(content, context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(1, "MyMod".length()));

    var items = completionProvider.getCompletion(dc, params);

    assertThat(items)
      .filteredOn(it -> "MyModule".equals(it.getLabel()))
      .singleElement()
      .extracting(CompletionItem::getKind)
      .isEqualTo(CompletionItemKind.Module);
  }

  private void initLib() {
    var fixtureRoot = Path.of("src/test/resources/oscript-libraries/mylib").toAbsolutePath();
    initServerContext(fixtureRoot, false);
    index.reindex(context);
  }
}
