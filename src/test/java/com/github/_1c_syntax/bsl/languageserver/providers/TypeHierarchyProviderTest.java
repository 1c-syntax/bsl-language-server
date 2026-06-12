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
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchyPrepareParams;
import org.eclipse.lsp4j.TypeHierarchySubtypesParams;
import org.eclipse.lsp4j.TypeHierarchySupertypesParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class TypeHierarchyProviderTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeHierarchyProvider provider;

  private static final Path FIXTURE_ROOT = Path.of("src/test/resources/type-hierarchy").toAbsolutePath();

  @BeforeEach
  void init() {
    initServerContext(FIXTURE_ROOT);
  }

  @Test
  void prepareReturnsEmptyForBslFile() {
    var bsl = TestUtils.getDocumentContext("Процедура П()\nКонецПроцедуры\n");

    var items = provider.prepareTypeHierarchy(bsl, prepareParams(bsl));

    assertThat(items).isEmpty();
  }

  @Test
  void prepareReturnsEmptyForPlainOsClassNotInHierarchy() {
    // .os без &Расширяет, не library-класс и без наследников — в иерархии не участвует.
    var os = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI,
      "Процедура Метод() Экспорт\nКонецПроцедуры\n", context);

    var items = provider.prepareTypeHierarchy(os, prepareParams(os));

    assertThat(items).isEmpty();
  }

  @Test
  void prepareReturnsClassForOScriptFile() {
    var document = document("Млекопитающее.os");

    var items = provider.prepareTypeHierarchy(document, prepareParams(document));

    assertThat(items)
      .hasSize(1)
      .allMatch(item -> item.getName().equals("Млекопитающее"))
      .allMatch(item -> item.getKind() == SymbolKind.Class);
  }

  @Test
  void prepareReturnsRootClassWithoutParentButWithChildren() {
    var document = document("Животное.os");

    var items = provider.prepareTypeHierarchy(document, prepareParams(document));

    assertThat(items)
      .hasSize(1)
      .allMatch(item -> item.getName().equals("Животное"));
  }

  @Test
  void supertypesResolvesDeclaredParent() {
    var document = document("Кошка.os");

    var supertypes = provider.supertypes(document, new TypeHierarchySupertypesParams(item(document)));

    assertThat(supertypes)
      .hasSize(1)
      .allMatch(item -> item.getName().equals("Млекопитающее"));
  }

  @Test
  void supertypesResolvedForEachSibling() {
    var document = document("Собака.os");

    var supertypes = provider.supertypes(document, new TypeHierarchySupertypesParams(item(document)));

    assertThat(supertypes)
      .extracting(TypeHierarchyItem::getName)
      .contains("Млекопитающее");
  }

  @Test
  void supertypesEmptyForRootClass() {
    var document = document("Животное.os");

    var supertypes = provider.supertypes(document, new TypeHierarchySupertypesParams(item(document)));

    assertThat(supertypes).isEmpty();
  }

  @Test
  void supertypesIncludeImplementedInterfaces() {
    // given — Собака наследует Млекопитающее (&Расширяет) и реализует
    // интерфейс Плавающее (&Реализует).
    var document = document("Собака.os");

    // when
    var supertypes = provider.supertypes(document, new TypeHierarchySupertypesParams(item(document)));

    // then — в супертипах и родитель, и реализуемый интерфейс.
    assertThat(supertypes)
      .extracting(TypeHierarchyItem::getName)
      .containsExactlyInAnyOrder("Млекопитающее", "Плавающее");
  }

  @Test
  void subtypesReturnsAllDirectChildren() {
    var document = document("Млекопитающее.os");

    var subtypes = provider.subtypes(document, new TypeHierarchySubtypesParams(item(document)));

    assertThat(subtypes)
      .extracting(TypeHierarchyItem::getName)
      .containsExactlyInAnyOrder("Кошка", "Собака");
  }

  @Test
  void subtypesReturnsSingleChildOfRoot() {
    var document = document("Животное.os");

    var subtypes = provider.subtypes(document, new TypeHierarchySubtypesParams(item(document)));

    assertThat(subtypes)
      .extracting(TypeHierarchyItem::getName)
      .containsExactly("Млекопитающее");
  }

  @Test
  void subtypesEmptyForLeafClass() {
    var document = document("Кошка.os");

    var subtypes = provider.subtypes(document, new TypeHierarchySubtypesParams(item(document)));

    assertThat(subtypes).isEmpty();
  }

  private DocumentContext document(String fileName) {
    var uri = uri(fileName);
    var documentContext = context.getDocument(uri);
    assertThat(documentContext).as("document %s must be populated", fileName).isNotNull();
    return documentContext;
  }

  private URI uri(String fileName) {
    return Absolute.uri(FIXTURE_ROOT.resolve(fileName).toUri());
  }

  private TypeHierarchyItem item(DocumentContext document) {
    return provider.prepareTypeHierarchy(document, prepareParams(document)).get(0);
  }

  private static TypeHierarchyPrepareParams prepareParams(DocumentContext document) {
    var params = new TypeHierarchyPrepareParams();
    params.setTextDocument(new TextDocumentIdentifier(document.getUri().toString()));
    params.setPosition(new Position(0, 0));
    return params;
  }
}
