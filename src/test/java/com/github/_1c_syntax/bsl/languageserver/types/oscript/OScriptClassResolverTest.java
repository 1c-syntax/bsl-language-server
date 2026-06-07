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
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class OScriptClassResolverTest extends AbstractServerContextAwareTest {

  @Autowired
  private OScriptClassResolver classResolver;

  @Autowired
  private OScriptLibraryIndex index;

  private static final Path FIXTURE_ROOT =
    Path.of("src/test/resources/oscript-libraries/extends-lib").toAbsolutePath();

  @Test
  void classNamesUsesLibraryQualifiedNameForLibraryClass() {
    initLib();
    var dc = libraryDocument("БазовыйКласс.os");

    assertThat(classResolver.classNames(dc)).containsExactly("БазовыйКласс");
    assertThat(classResolver.isLibraryClass(dc)).isTrue();
  }

  @Test
  void classNamesFallsBackToBasenameForNonLibraryFile() {
    initServerContext();
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI,
      "Процедура ПриСозданииОбъекта()\nКонецПроцедуры\n", context);

    assertThat(classResolver.classNames(dc)).containsExactly("fake-uri");
    assertThat(classResolver.isLibraryClass(dc)).isFalse();
  }

  @Test
  void resolveClassDocumentFindsLibraryClassByName() {
    initLib();

    var resolved = classResolver.resolveClassDocument("ПромежуточныйКласс", context);

    assertThat(resolved).isPresent();
    assertThat(resolved.get().getUri().getPath()).endsWith("ПромежуточныйКласс.os");
  }

  @Test
  void resolveClassDocumentReturnsEmptyForUnknownName() {
    initLib();
    assertThat(classResolver.resolveClassDocument("НетТакогоКласса", context)).isEmpty();
  }

  private void initLib() {
    initServerContext(FIXTURE_ROOT, false);
    index.reindex(context);
  }

  private com.github._1c_syntax.bsl.languageserver.context.DocumentContext libraryDocument(String fileName) {
    var uri = Absolute.uri(FIXTURE_ROOT.resolve("src").resolve(fileName).toUri());
    var dc = context.getDocument(uri);
    assertThat(dc).as("document %s must be indexed", fileName).isNotNull();
    return dc;
  }
}
