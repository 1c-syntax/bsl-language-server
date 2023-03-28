/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OscriptReferenceFinderTest {

  @Autowired
  private OscriptReferenceFinder referenceFinder;

  @Autowired
  private ServerContext serverContext;

  @Test
  void testFindReferenceToClass() {
    // given
    serverContext.setConfigurationRoot(Absolute.path("src/test/resources/metadata/oscript"));
    serverContext.populateContext();
    var mainOsContext = TestUtils.getDocumentContextFromFile("./src/test/resources/metadata/oscript/main.os");

    // when
    var optionalReference = referenceFinder.findReference(mainOsContext.getUri(), new Position(1, 25));

    // then
    assertThat(optionalReference).isPresent();
  }

  @Test
  void testFindReferenceToModule() {
    // given
    serverContext.setConfigurationRoot(Absolute.path("src/test/resources/metadata/oscript"));
    serverContext.populateContext();
    var mainOsContext = TestUtils.getDocumentContextFromFile("./src/test/resources/metadata/oscript/main.os");

    // when
    var optionalReference = referenceFinder.findReference(mainOsContext.getUri(), new Position(3, 17));
    // then
    assertThat(optionalReference).isPresent();

    // when
    var variable = referenceFinder.findReference(mainOsContext.getUri(), new Position(3, 53));
    // then
    assertThat(variable).isEmpty();
  }

}
