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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.providers.CallHierarchyProvider;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Регрессия: {@link ReferenceIndex#methodCallOccurrence} индексирует ЛЮБОЙ вызов метода
 * (в т.ч. {@code Новый ИмяКласса(...)}, попадающий туда же через
 * {@code ReferenceIndexFiller#tryRegisterLibraryClassReference}) под каноническим
 * {@link SymbolKind#Method}, тогда как
 * {@link com.github._1c_syntax.bsl.languageserver.context.symbol.ConstructorSymbol#getSymbolKind()}
 * возвращает {@link SymbolKind#Constructor}. До появления {@link ReferenceIndex#indexedKindOf}
 * {@link ReferenceIndex#getReferencesTo} строило ключ поиска по настоящему {@code Constructor} —
 * он не совпадал с сохранённым при индексации {@code Method}, и Find References / Rename /
 * Call Hierarchy incoming calls молча не находили ни одной ссылки на конструктор OneScript-класса.
 */
class ReferenceIndexConstructorSymbolTest extends AbstractServerContextAwareTest {

  private static final String FIXTURE_DIR = "src/test/resources/oscript-libraries/constructor-inlay-test";
  private static final String CALLER_PATH = FIXTURE_DIR + "/src/Классы/Caller.os";

  @Autowired
  private ReferenceIndex referenceIndex;

  @Autowired
  private OScriptLibraryIndex oScriptLibraryIndex;

  @Autowired
  private CallHierarchyProvider callHierarchyProvider;

  @BeforeEach
  @DirtiesContext
  void freshWorkspace() {
    initServerContext(Path.of(FIXTURE_DIR).toAbsolutePath());
    oScriptLibraryIndex.reindex(context);
  }

  @Test
  void getReferencesToConstructorFindsNewExpressionCall() {
    // given
    var classUri = oScriptLibraryIndex.findClassUri("ClassWithCtorParams").orElseThrow();
    var classDocumentContext = context.getDocument(classUri);
    var constructor = classDocumentContext.getSymbolTree().getConstructor().orElseThrow();
    // индексация ссылки из Caller.os требует, чтобы документ был построен/обойдён
    TestUtils.getDocumentContextFromFile(CALLER_PATH, context);

    // when
    var references = referenceIndex.getReferencesTo(constructor);

    // then
    assertThat(references)
      .as("Find References на конструктор OneScript-класса должен находить `Новый ClassWithCtorParams(...)`")
      .isNotEmpty();
  }

  @Test
  void callHierarchyIncomingCallsFindsConstructorCaller() {
    // given
    var classUri = oScriptLibraryIndex.findClassUri("ClassWithCtorParams").orElseThrow();
    var classDocumentContext = context.getDocument(classUri);
    var constructor = classDocumentContext.getSymbolTree().getConstructor().orElseThrow();
    TestUtils.getDocumentContextFromFile(CALLER_PATH, context);

    var callHierarchyItem = new CallHierarchyItem(
      constructor.getName(),
      SymbolKind.Constructor,
      classUri.toString(),
      constructor.getRange(),
      constructor.getSelectionRange()
    );

    // when — resolveSymbol передоверяет резолв символа referenceResolver.findReference по
    // (documentContext.getUri(), item.getSelectionRange().getStart()), поэтому documentContext
    // здесь — документ КЛАССА (где физически расположена selectionRange конструктора), а не Caller.os
    var incoming = callHierarchyProvider.incomingCalls(
      classDocumentContext, new CallHierarchyIncomingCallsParams(callHierarchyItem));

    // then
    assertThat(incoming)
      .as("Call Hierarchy incoming calls должен находить вызывающего Caller.os")
      .isNotEmpty();
  }
}
