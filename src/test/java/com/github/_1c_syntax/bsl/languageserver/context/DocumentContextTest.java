/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
package com.github._1c_syntax.bsl.languageserver.context;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentContextTest {

  @Test
  void testClearASTData() throws IOException, IllegalAccessException {
    // given
    DocumentContext documentContext = getDocumentContext();

    // when
    documentContext.clearASTData();

    // then
    final Object lazyAst = FieldUtils.readField(documentContext, "ast", true);
    final Object ast = FieldUtils.readField(lazyAst, "value", true);
    assertThat(ast).isNull();
  }

  @Test
  void testMethodCompute() throws IOException {

    DocumentContext documentContext = getDocumentContext();

    assertThat(documentContext.getMethods().size()).isEqualTo(2);

  }

  @Test
  void testMethodComputeParseError() throws IOException {

    DocumentContext documentContext =
      getDocumentContext("./src/test/resources/context/DocumentContextParseErrorTest.bsl");

    assertThat(documentContext.getMethods().isEmpty()).isTrue();

  }

  public DocumentContext getDocumentContext() throws IOException {

    String filePath = "./src/test/resources/context/DocumentContextTest.bsl";
    return getDocumentContext(filePath);
  }

  private DocumentContext getDocumentContext(String filePath) throws IOException {

    // given
    String fileContent = FileUtils.readFileToString(
      new File(filePath),
      StandardCharsets.UTF_8
    );

    return new DocumentContext("fake-uri.bsl", fileContent);
  }

}