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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
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
  private LanguageServerConfiguration configuration;

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

    TextEdit textEdit = textEdits.getFirst();
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

    TextEdit textEdit = textEdits.getFirst();
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

    TextEdit textEdit = textEdits.getFirst();
    assertThat(textEdit.getNewText()).isEqualTo(formattedFileContent);
  }

  @Test
  void testFormatRuKeywordsWithoutUpperCase() throws IOException {
    var originalFile = new File("./src/test/resources/providers/formatKeywordsRu.bsl");
    var formattedFile = new File("./src/test/resources/providers/format_formattedWithoutUpperCaseKeywordsRu.bsl");

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

    // Configure for this workspace
    configuration.update(new File("./src/test/resources/.bsl-language-server-not-uppercase-format.json"));

    // when
    List<TextEdit> textEdits = formatProvider.getFormatting(params, documentContext);

    // then
    assertThat(textEdits).hasSize(1);

    TextEdit textEdit = textEdits.getFirst();
    assertThat(textEdit.getNewText()).isEqualTo(formattedFileContent);
  }

  @Test
  void testDisabledKeywordsFormatting() throws IOException {
    var originalFile = new File("./src/test/resources/providers/formatKeywordsRu.bsl");

    // given
    DocumentFormattingParams params = new DocumentFormattingParams();
    params.setTextDocument(getTextDocumentIdentifier());
    params.setOptions(new FormattingOptions(2, true));

    String fileContent = FileUtils.readFileToString(originalFile, StandardCharsets.UTF_8);

    var documentContext = TestUtils.getDocumentContext(
      URI.create(params.getTextDocument().getUri()),
      fileContent
    );

    // Configure for this workspace
    configuration.update(new File("./src/test/resources/.bsl-language-server-format-keywords-off.json"));

    // when
    List<TextEdit> textEdits = formatProvider.getFormatting(params, documentContext);

    // then
    assertThat(textEdits).hasSize(1);

    TextEdit textEdit = textEdits.getFirst();
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

    // Set language in per-workspace configuration
    configuration.setLanguage(Language.EN);

    // when
    List<TextEdit> textEdits = formatProvider.getFormatting(params, documentContext);

    // then
    assertThat(textEdits).hasSize(1);

    TextEdit textEdit = textEdits.getFirst();
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

    TextEdit textEdit = textEdits.getFirst();
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
    assertThat(textEdits.getFirst().getNewText()).isEqualTo("Возврат -1 > -2");

  }

  @Test
  void testOnTypeFormattingEnterNormalizesPreviousLine() {
    // given: пользователь нажал Enter после `если х=1 тогда`
    String fileContent = "если х=1 тогда\n\n";
    var params = onTypeParams("\n", 1, 0);

    var documentContext = TestUtils.getDocumentContext(
      URI.create(params.getTextDocument().getUri()),
      fileContent
    );

    // when
    List<TextEdit> textEdits = formatProvider.getOnTypeFormatting(params, documentContext);

    // then
    assertThat(textEdits).hasSize(1);
    TextEdit edit = textEdits.getFirst();
    assertThat(edit.getRange()).isEqualTo(new Range(new Position(0, 0), new Position(1, 0)));
    assertThat(edit.getNewText()).isEqualTo("Если х = 1 Тогда\n");
  }

  @Test
  void testOnTypeFormattingEnterPreservesIndent() {
    // given: внутри процедуры пользователь набрал кривую строку и нажал Enter
    String fileContent = "Процедура П()\n    возврат;\nКонецПроцедуры\n";
    // Enter был нажат в конце второй строки (LSP-line 1), курсор перешёл на line 2
    var params = onTypeParams("\n", 2, 0);

    var documentContext = TestUtils.getDocumentContext(
      URI.create(params.getTextDocument().getUri()),
      fileContent
    );

    // when
    List<TextEdit> textEdits = formatProvider.getOnTypeFormatting(params, documentContext);

    // then
    assertThat(textEdits).hasSize(1);
    TextEdit edit = textEdits.getFirst();
    assertThat(edit.getRange()).isEqualTo(new Range(new Position(1, 0), new Position(2, 0)));
    assertThat(edit.getNewText()).isEqualTo("    Возврат;\n");
  }

  @Test
  void testOnTypeFormattingEnterOnEmptyPreviousLineReturnsNoEdits() {
    // given
    String fileContent = "\n\n";
    var params = onTypeParams("\n", 1, 0);

    var documentContext = TestUtils.getDocumentContext(
      URI.create(params.getTextDocument().getUri()),
      fileContent
    );

    // when
    List<TextEdit> textEdits = formatProvider.getOnTypeFormatting(params, documentContext);

    // then
    assertThat(textEdits).isEmpty();
  }

  @Test
  void testOnTypeFormattingEnterAtFirstLineReturnsNoEdits() {
    // given: предыдущей строки не существует
    String fileContent = "Процедура П()\nКонецПроцедуры\n";
    var params = onTypeParams("\n", 0, 0);

    var documentContext = TestUtils.getDocumentContext(
      URI.create(params.getTextDocument().getUri()),
      fileContent
    );

    // when
    List<TextEdit> textEdits = formatProvider.getOnTypeFormatting(params, documentContext);

    // then
    assertThat(textEdits).isEmpty();
  }

  @Test
  void testOnTypeFormattingSemicolonFormatsCurrentLineUpToCursor() {
    // given: пользователь набрал `х=1;` — курсор сразу после `;` (column 4)
    String fileContent = "х=1;";
    var params = onTypeParams(";", 0, 4);

    var documentContext = TestUtils.getDocumentContext(
      URI.create(params.getTextDocument().getUri()),
      fileContent
    );

    // when
    List<TextEdit> textEdits = formatProvider.getOnTypeFormatting(params, documentContext);

    // then
    assertThat(textEdits).hasSize(1);
    TextEdit edit = textEdits.getFirst();
    assertThat(edit.getRange()).isEqualTo(new Range(new Position(0, 0), new Position(0, 4)));
    assertThat(edit.getNewText()).isEqualTo("х = 1;");
  }

  @Test
  void testOnTypeFormattingUnknownTriggerReturnsNoEdits() {
    // given
    String fileContent = "х=1\n";
    var params = onTypeParams(".", 0, 3);

    var documentContext = TestUtils.getDocumentContext(
      URI.create(params.getTextDocument().getUri()),
      fileContent
    );

    // when
    List<TextEdit> textEdits = formatProvider.getOnTypeFormatting(params, documentContext);

    // then
    assertThat(textEdits).isEmpty();
  }

  private DocumentOnTypeFormattingParams onTypeParams(String ch, int line, int character) {
    var params = new DocumentOnTypeFormattingParams();
    params.setTextDocument(getTextDocumentIdentifier());
    params.setOptions(new FormattingOptions(4, true));
    params.setCh(ch);
    params.setPosition(new Position(line, character));
    return params;
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
