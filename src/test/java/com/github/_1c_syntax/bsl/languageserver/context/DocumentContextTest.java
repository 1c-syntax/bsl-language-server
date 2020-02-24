/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentContextTest {

  @Test
  void testRebuild() throws IOException {

    DocumentContext documentContext = getDocumentContext("./src/test/resources/context/DocumentContextRebuildFirstTest.bsl");
    assertThat(documentContext.getTokens()).hasSize(38);

    File file = new File("./src/test/resources/context/DocumentContextRebuildSecondTest.bsl");
    String fileContent = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
    documentContext.rebuild(fileContent);
    assertThat(documentContext.getTokens()).hasSize(15);
  }

  @Test
  void testClearASTData() throws IllegalAccessException {
    // given
    DocumentContext documentContext = getDocumentContext();

    // when
    documentContext.clearASTData();

    // then
    final Object tokenizer = FieldUtils.readField(documentContext, "tokenizer", true);
    assertThat(tokenizer).isNull();
  }

  @Test
  void testMethodCompute() {

    DocumentContext documentContext = getDocumentContext();

    assertThat(documentContext.getMethods().size()).isEqualTo(2);

  }

  @Test
  void testMethodComputeParseError() throws IOException {

    DocumentContext documentContext =
      getDocumentContext("./src/test/resources/context/DocumentContextParseErrorTest.bsl");

    assertThat(documentContext.getMethods().isEmpty()).isTrue();

  }

  @Test
  void testGetRegionsFlatComputesAllLevels() {
    DocumentContext documentContext = getDocumentContext();

    assertThat(documentContext.getRegions()).hasSize(2);
    assertThat(documentContext.getRegionsFlat()).hasSize(4);
  }

  @Test
  void testRegionsAdjustingCompute() {
    // given
    DocumentContext documentContext = getDocumentContext();

    // when
    List<RegionSymbol> regions = documentContext.getRegions();

    // then
    assertThat(regions).anyMatch(regionSymbol -> regionSymbol.getMethods().size() > 0);
  }

  @Test
  void testMethodsAdjustingCompute() {
    // given
    DocumentContext documentContext = getDocumentContext();

    // when
    List<MethodSymbol> methods = documentContext.getMethods();

    // then
    assertThat(methods)
      .anyMatch(methodSymbol -> methodSymbol.getRegion().isPresent())
      .anySatisfy(methodSymbol -> methodSymbol.getRegion().ifPresent(
        regionSymbol -> assertThat(regionSymbol.getMethods()).contains(methodSymbol)
        )
      )
    ;
  }

  @Test
  void testUntitledSchema() {
    // given
    URI uri = URI.create("untitled:///fake.bsl");
    String fileContent = "";

    // when
    var documentContext = TestUtils.getDocumentContext(uri, fileContent);

    // then
    assertThat(documentContext.getFileType()).isEqualTo(FileType.BSL);
  }

  @Test
  void testUntitledSchemaFromVSC() {
    // given
    URI uri = URI.create("untitled:Untitled-1");
    String fileContent = "";

    // when
    var documentContext = TestUtils.getDocumentContext(uri, fileContent);

    // then
    assertThat(documentContext.getFileType()).isEqualTo(FileType.BSL);
  }

  @SneakyThrows
  public DocumentContext getDocumentContext() {

    return getDocumentContext("./src/test/resources/context/DocumentContextTest.bsl");
  }

  private DocumentContext getDocumentContext(String filePath) {
    return TestUtils.getDocumentContextFromFile(filePath);
  }

  @Test
  void testComputeMetricsLocForCover() {

    DocumentContext documentContext =
      getDocumentContext("./src/test/resources/context/DocumentContextLocForCoverTest.bsl");

    assertThat(documentContext.getMetrics().getCovlocData()).containsSequence(5, 6, 10, 11, 12, 18, 26, 28, 31, 32, 35, 37);

  }

  @Test
  void testComputeMetricsComments() {

    DocumentContext documentContext =
      getDocumentContext("./src/test/resources/context/DocumentContextCommentsTest.bsl");

    assertThat(documentContext.getMetrics().getComments()).isEqualTo(8);

  }


}