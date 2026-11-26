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
import com.github._1c_syntax.bsl.languageserver.util.TestApplicationContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/**
 * Поиск ссылок находит документ по URI, под которым тот зарегистрирован, без нормализации:
 * она обращается к файловой системе, а поиск зовётся на каждом вызове метода при выводе типов.
 */
class ReferenceFinderDocumentLookupTest extends AbstractServerContextAwareTest {

  private static final String PATH = "./src/test/resources/references/ReferenceFinderDocumentLookup.bsl";

  @BeforeEach
  void setUp() {
    initServerContext();
  }

  static Stream<Arguments> finders() {
    return Stream.of(
      Arguments.of(SourceDefinedSymbolDeclarationReferenceFinder.class, new Position(0, 11)),
      Arguments.of(NewExpressionReferenceFinder.class, new Position(1, 20)),
      Arguments.of(PlatformMemberReferenceFinder.class, new Position(2, 12))
    );
  }

  @ParameterizedTest
  @MethodSource("finders")
  void registeredUriIsNotNormalized(Class<? extends ReferenceFinder> finderClass, Position position) {
    // given
    var finder = TestApplicationContext.getBean(finderClass);
    var documentContext = TestUtils.getDocumentContextFromFile(PATH);
    documentContext.getSymbolTree();
    var uri = documentContext.getUri();

    // when
    try (var absolute = mockStatic(Absolute.class, CALLS_REAL_METHODS)) {
      var reference = finder.findReference(uri, position);

      // then
      assertThat(reference).isPresent();
      absolute.verifyNoInteractions();
    }
  }
}
