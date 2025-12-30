/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.context;

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import lombok.SneakyThrows;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class DocumentContextTest {

  @Test
  void testRebuild() throws IOException {

    var documentContext = getDocumentContext("./src/test/resources/context/DocumentContextRebuildFirstTest.bsl");
    assertThat(documentContext.getTokens()).hasSize(39);

    File file = new File("./src/test/resources/context/DocumentContextRebuildSecondTest.bsl");
    String fileContent = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
    documentContext.rebuild(fileContent, documentContext.getVersion() + 1);
    assertThat(documentContext.getTokens()).hasSize(16);
  }

  @Test
  void testClearASTData() throws IllegalAccessException {
    // given
    var documentContext = getDocumentContext();

    // when
    documentContext.clearSecondaryData();

    // then
    final Object tokenizer = FieldUtils.readField(documentContext, "tokenizer", true);
    assertThat(tokenizer).isNull();
  }

  @Test
  void testGetAstAfterFreezeAndClear() {
    // given
    var documentContext = getDocumentContext();
    
    // when - freeze the document to preserve computed data
    documentContext.freezeComputedData();
    // then clear secondary data (simulating what happens in ServerContext.tryClearDocument)
    documentContext.clearSecondaryData();
    
    // then - getAst() should not throw NPE on frozen documents
    assertThat(documentContext.getAst()).isNotNull();
  }

  @Test
  void testGetTokensAfterFreezeAndClear() {
    // given
    var documentContext = getDocumentContext();
    
    // when - freeze the document to preserve computed data
    documentContext.freezeComputedData();
    // then clear secondary data (simulating what happens in ServerContext.tryClearDocument)
    documentContext.clearSecondaryData();
    
    // then - getTokens() should not throw NPE on frozen documents
    assertThat(documentContext.getTokens()).isNotEmpty();
  }

  @Test
  void testMethodCompute() {

    var documentContext = getDocumentContext();

    assertThat(documentContext.getSymbolTree().getMethods().size()).isEqualTo(3);

  }

  @Test
  void testMethodParametersComputesCorrectly() {
    var documentContext = getDocumentContext();
    assertThat(documentContext.getSymbolTree().getMethods())
      .filteredOn(methodSymbol -> methodSymbol.getName().equals("ФункцияСПараметрами"))
      .flatExtracting(MethodSymbol::getParameters)
      .hasSize(4)
      .anyMatch(parameterDefinition ->
        parameterDefinition.getName().equals("Парам1")
          && !parameterDefinition.isByValue()
          && !parameterDefinition.isOptional()
      )
      .anyMatch(parameterDefinition ->
        parameterDefinition.getName().equals("Парам2")
          && parameterDefinition.isByValue()
          && !parameterDefinition.isOptional()
      )
      .anyMatch(parameterDefinition ->
        parameterDefinition.getName().equals("Парам3")
          && !parameterDefinition.isByValue()
          && parameterDefinition.isOptional()
      )
      .anyMatch(parameterDefinition ->
        parameterDefinition.getName().equals("Парам4")
          && parameterDefinition.isByValue()
          && parameterDefinition.isOptional()
      )
    ;

  }

  @Test
  void testMethodComputeParseError() {

    var documentContext =
      getDocumentContext("./src/test/resources/context/DocumentContextParseErrorTest.bsl");

    assertThat(documentContext.getSymbolTree().getMethods().isEmpty()).isTrue();

  }

  @Test
  void testGetRegionsFlatComputesAllLevels() {
    var documentContext = getDocumentContext();

    assertThat(documentContext.getSymbolTree().getModuleLevelRegions()).hasSize(2);
    assertThat(documentContext.getSymbolTree().getRegionsFlat()).hasSize(6);
  }

  @Test
  void testRegionsAdjustingCompute() {
    // given
    var documentContext = getDocumentContext();

    // when
    List<RegionSymbol> regions = documentContext.getSymbolTree().getModuleLevelRegions();

    // then
    assertThat(regions).anyMatch(regionSymbol -> !regionSymbol.getMethods().isEmpty());
  }

  @Test
  void testMethodsAdjustingCompute() {
    // given
    var documentContext = getDocumentContext();

    // when
    List<MethodSymbol> methods = documentContext.getSymbolTree().getMethods();

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
  void testComputeMetricsComments() {

    var documentContext =
      getDocumentContext("./src/test/resources/context/DocumentContextCommentsTest.bsl");

    assertThat(documentContext.getMetrics().getComments()).isEqualTo(8);

  }

  @Test
  void testContentList() {
    // given
    var documentContext = getDocumentContext();

    // when
    String[] contentList = documentContext.getContentList();

    // then
    assertThat(contentList).hasSize(40);
  }

  @Test
  void testContentListWithStandaloneCR() {
    // given
    var documentContext = getDocumentContext("./src/test/resources/context/DocumentContextBrokenLineFeeds.bsl");

    // when
    var contentList = documentContext.getContentList();

    // then
    assertThat(contentList).hasSize(3);
  }

  @Test
  void testEOF() {
    // given
    var documentContext = getDocumentContext();
    // when
    List<Token> tokens = documentContext.getTokens();
    Token lastToken = tokens.get(tokens.size() - 1);
    // then
    assertThat(lastToken.getType()).isEqualTo(Lexer.EOF);
    assertThat(lastToken.getChannel()).isEqualTo(Lexer.HIDDEN);
  }
}
