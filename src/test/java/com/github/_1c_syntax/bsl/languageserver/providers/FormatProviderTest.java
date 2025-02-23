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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.StringJoiner;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class FormatProviderTest {

  @Autowired
  private FormatProvider formatProvider;
  @Autowired
  private LanguageServerConfiguration languageServerConfiguration;

  @Test
  void testRangeFormat() throws IOException {
    // given
    int startLine = 4;
    int endLine = 25;

    DocumentRangeFormattingParams params = new DocumentRangeFormattingParams();
    params.setTextDocument(getTextDocumentIdentifier());
    params.setRange(Ranges.create(startLine, 0, endLine, 0));
    params.setOptions(new FormattingOptions(4, true));

    String fileContent = FileUtils.readFileToString(getTestFile(), StandardCharsets.UTF_8);
    String formattedFileContent = FileUtils.readFileToString(getFormattedTestFile(), StandardCharsets.UTF_8);
    String[] strings = formattedFileContent.split("\n");
    StringJoiner joiner = new StringJoiner("\n");
    for (int i = 0; i < strings.length; i++) {
      if (i < startLine || i > endLine) {
        continue;
      }
      joiner.add(strings[i]);
    }

    formattedFileContent = joiner.toString();

    var documentContext = TestUtils.getDocumentContext(
      URI.create(params.getTextDocument().getUri()),
      fileContent
    );

    // when
    List<TextEdit> textEdits = formatProvider.getRangeFormatting(params, documentContext);

    // then
    assertThat(textEdits).hasSize(1);

    TextEdit textEdit = textEdits.get(0);
    assertThat(textEdit.getNewText()).isEqualTo(formattedFileContent);
  }

  @Test
  void testFormat() throws IOException {
    // given
    DocumentFormattingParams params = new DocumentFormattingParams();
    params.setTextDocument(getTextDocumentIdentifier());
    params.setOptions(new FormattingOptions(4, true));

    String fileContent = FileUtils.readFileToString(getTestFile(), StandardCharsets.UTF_8);
    String formattedFileContent = FileUtils.readFileToString(getFormattedTestFile(), StandardCharsets.UTF_8);

    var documentContext = TestUtils.getDocumentContext(
      URI.create(params.getTextDocument().getUri()),
      fileContent
    );

    // when
    List<TextEdit> textEdits = formatProvider.getFormatting(params, documentContext);

    // then
    assertThat(textEdits).hasSize(1);

    TextEdit textEdit = textEdits.get(0);
    assertThat(textEdit.getNewText()).isEqualTo(formattedFileContent);

  }

  @Test
  void testFormatRuKeywords() throws IOException {
    var originalFile = new File("./src/test/resources/providers/formatKeywordsRu.bsl");
    var formattedFile = new File("./src/test/resources/providers/format_formattedKeywordsRu.bsl");
    // given
    DocumentFormattingParams params = new DocumentFormattingParams();
    params.setTextDocument(getTextDocumentIdentifier());
    params.setOptions(new FormattingOptions(2, true));

    String fileContent = FileUtils.readFileToString(originalFile, StandardCharsets.UTF_8);
    String formattedFileContent = FileUtils.readFileToString(formattedFile, StandardCharsets.UTF_8);

    var documentContext = TestUtils.getDocumentContext(
      URI.create(params.getTextDocument().getUri()),
      fileContent
    );

    // when
    List<TextEdit> textEdits = formatProvider.getFormatting(params, documentContext);

    // then
    assertThat(textEdits).hasSize(1);

    TextEdit textEdit = textEdits.get(0);
    assertThat(textEdit.getNewText()).isEqualTo(formattedFileContent);
  }

  @Test
  void testFormatRuKeywordsWithoutUpperCase() throws IOException {
    var originalFile = new File("./src/test/resources/providers/formatKeywordsRu.bsl");
    var formattedFile = new File("./src/test/resources/providers/format_formattedWithoutUpperCaseKeywordsRu.bsl");
    languageServerConfiguration.update(new File("./src/test/resources/.bsl-language-server-not-uppercase-format.json"));
    // given
    DocumentFormattingParams params = new DocumentFormattingParams();
    params.setTextDocument(getTextDocumentIdentifier());
    params.setOptions(new FormattingOptions(2, true));

    String fileContent = FileUtils.readFileToString(originalFile, StandardCharsets.UTF_8);
    String formattedFileContent = FileUtils.readFileToString(formattedFile, StandardCharsets.UTF_8);

    var documentContext = TestUtils.getDocumentContext(
      URI.create(params.getTextDocument().getUri()),
      fileContent
    );

    // when
    List<TextEdit> textEdits = formatProvider.getFormatting(params, documentContext);

    // then
    assertThat(textEdits).hasSize(1);

    TextEdit textEdit = textEdits.get(0);
    assertThat(textEdit.getNewText()).isEqualTo(formattedFileContent);
  }

  @Test
  void testDisabledKeywordsFormatting() throws IOException {
    var originalFile = new File("./src/test/resources/providers/formatKeywordsRu.bsl");
    languageServerConfiguration.update(new File("./src/test/resources/.bsl-language-server-format-keywords-off.json"));
    // given
    DocumentFormattingParams params = new DocumentFormattingParams();
    params.setTextDocument(getTextDocumentIdentifier());
    params.setOptions(new FormattingOptions(2, true));

    String fileContent = FileUtils.readFileToString(originalFile, StandardCharsets.UTF_8);

    var documentContext = TestUtils.getDocumentContext(
      URI.create(params.getTextDocument().getUri()),
      fileContent
    );

    // when
    List<TextEdit> textEdits = formatProvider.getFormatting(params, documentContext);

    // then
    assertThat(textEdits).hasSize(1);

    TextEdit textEdit = textEdits.get(0);
    assertThat(textEdit.getNewText()).isEqualTo(fileContent);
  }

  @Test
  void testFormatEngKeywords() throws IOException {
    var originalFile = new File("./src/test/resources/providers/formatKeywordsEng.bsl");
    var formattedFile = new File("./src/test/resources/providers/format_formattedKeywordsEng.bsl");
    // given
    DocumentFormattingParams params = new DocumentFormattingParams();
    params.setTextDocument(getTextDocumentIdentifier());
    params.setOptions(new FormattingOptions(2, true));

    String fileContent = FileUtils.readFileToString(originalFile, StandardCharsets.UTF_8);
    String formattedFileContent = FileUtils.readFileToString(formattedFile, StandardCharsets.UTF_8);
    var documentContext = TestUtils.getDocumentContext(
      URI.create(params.getTextDocument().getUri()),
      fileContent
    );

    var configuration = new LanguageServerConfiguration();
    configuration.setLanguage(Language.EN);
    documentContext.setConfiguration(configuration);

    // when
    List<TextEdit> textEdits = formatProvider.getFormatting(params, documentContext);

    // then
    assertThat(textEdits).hasSize(1);

    TextEdit textEdit = textEdits.get(0);
    assertThat(textEdit.getNewText()).isEqualTo(formattedFileContent);
  }

  @Test
  void testFormatFluent() throws IOException {
    var originalFile = new File("./src/test/resources/providers/formatFluent.bsl");
    var formattedFile = new File("./src/test/resources/providers/format_formattedFluent.bsl");
    // given
    DocumentFormattingParams params = new DocumentFormattingParams();
    params.setTextDocument(getTextDocumentIdentifier());
    params.setOptions(new FormattingOptions(2, true));

    String fileContent = FileUtils.readFileToString(originalFile, StandardCharsets.UTF_8);
    String formattedFileContent = FileUtils.readFileToString(formattedFile, StandardCharsets.UTF_8);
    var documentContext = TestUtils.getDocumentContext(
      URI.create(params.getTextDocument().getUri()),
      fileContent
    );

    // when
    List<TextEdit> textEdits = formatProvider.getFormatting(params, documentContext);

    // then
    assertThat(textEdits).hasSize(1);

    TextEdit textEdit = textEdits.get(0);
    assertThat(textEdit.getNewText()).isEqualTo(formattedFileContent);
  }

  @Test
  void testFormatUnaryMinus() {

    // given
    DocumentFormattingParams params = new DocumentFormattingParams();
    params.setTextDocument(getTextDocumentIdentifier());
    params.setOptions(new FormattingOptions(4, true));

    String fileContent = "Возврат-1>-2";
    var documentContext = TestUtils.getDocumentContext(
      URI.create(params.getTextDocument().getUri()),
      fileContent
    );

    // when
    List<TextEdit> textEdits = formatProvider.getFormatting(params, documentContext);

    // then
    assertThat(textEdits).hasSize(1);
    assertThat(textEdits.get(0).getNewText()).isEqualTo("Возврат -1 > -2");

  }

  private File getTestFile() {
    return new File("./src/test/resources/providers/format.bsl");
  }

  private File getFormattedTestFile() {
    return new File("./src/test/resources/providers/format_formatted.bsl");
  }

  private TextDocumentIdentifier getTextDocumentIdentifier() {
    // TODO: Переделать на TestUtils.getTextDocumentIdentifier();
    File file = getTestFile();
    String uri = file.toURI().toString();

    return new TextDocumentIdentifier(uri);
  }
}
