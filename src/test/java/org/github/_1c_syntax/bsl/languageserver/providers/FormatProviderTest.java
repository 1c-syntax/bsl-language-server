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
package org.github._1c_syntax.bsl.languageserver.providers;

import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextEdit;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.StringJoiner;

import static org.assertj.core.api.Java6Assertions.assertThat;

class FormatProviderTest {

  @Test
  void testRangeFormat() throws IOException {
    // given
    int startLine = 2;
    int endLine = 21;

    DocumentRangeFormattingParams params = new DocumentRangeFormattingParams();
    params.setTextDocument(getTextDocumentIdentifier());
    params.setRange(RangeHelper.newRange(startLine, 0, endLine, 0));
    params.setOptions(new FormattingOptions(4, true));

    String fileContent = FileUtils.readFileToString(getTestFile(), StandardCharsets.UTF_8);
    String formattedFileContent = FileUtils.readFileToString(getFormatedTestFile(), StandardCharsets.UTF_8);
    String[] strings = formattedFileContent.split("\n");
    StringJoiner joiner = new StringJoiner("\n");
    for (int i = 0; i < strings.length; i++) {
      if (i < startLine || i > endLine) {
        continue;
      }
      joiner.add(strings[i]);
    }

    formattedFileContent = joiner.toString();

    DocumentContext documentContext = new DocumentContext(params.getTextDocument().getUri(), fileContent);

    // when
    List<TextEdit> textEdits = FormatProvider.getRangeFormatting(params, documentContext);

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
    String formattedFileContent = FileUtils.readFileToString(getFormatedTestFile(), StandardCharsets.UTF_8);

    DocumentContext documentContext = new DocumentContext(params.getTextDocument().getUri(), fileContent);

    // when
    List<TextEdit> textEdits = FormatProvider.getFormatting(params, documentContext);

    // then
    assertThat(textEdits).hasSize(1);

    TextEdit textEdit = textEdits.get(0);
    assertThat(textEdit.getNewText()).isEqualTo(formattedFileContent);

  }

  private File getTestFile() {
    return new File("./src/test/resources/providers/format.bsl");
  }

  private File getFormatedTestFile() {
    return new File("./src/test/resources/providers/format_formatted.bsl");
  }

  private TextDocumentItem getTextDocumentItem() throws IOException {
    File file = getTestFile();
    String uri = file.toURI().toString();

    String fileContent = FileUtils.readFileToString(file, StandardCharsets.UTF_8);

    return new TextDocumentItem(uri, "bsl", 1, fileContent);
  }

  private TextDocumentIdentifier getTextDocumentIdentifier() {
    File file = getTestFile();
    String uri = file.toURI().toString();

    return new TextDocumentIdentifier(uri);
  }
}
