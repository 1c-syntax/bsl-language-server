/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com>
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

import static org.assertj.core.api.Java6Assertions.assertThat;

class FormatProviderTest {

  @Test
  void testFormat() throws IOException {
    DocumentRangeFormattingParams params = new DocumentRangeFormattingParams();
    params.setTextDocument(getTextDocumentIdentifier());
    params.setRange(RangeHelper.newRange(2, 0, 6, 0));
    params.setOptions(new FormattingOptions(4, true));

    String fileContent = FileUtils.readFileToString(getTestFile(), StandardCharsets.UTF_8);
    DocumentContext documentContext = new DocumentContext(params.getTextDocument().getUri(), fileContent);

    List<TextEdit> textEdits = FormatProvider.getRangeFormatting(params, documentContext);

    assertThat(textEdits).hasSize(1);

    TextEdit textEdit = textEdits.get(0);
    assertThat(textEdit.getNewText()).isEqualTo(
      "    Если Истина Тогда\n" +
      "        // Комментарий\n" +
      "        Возврат;\n" +
      "    КонецЕсли;\n"
    );
  }

  private File getTestFile() {
    return new File("./src/test/resources/providers/format.bsl");
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
