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
import com.github._1c_syntax.utils.Absolute;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.DocumentRangesFormattingParams;
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
  void testRangesFormat() throws IOException {
    // given: два независимых диапазона в одном запросе rangesFormatting (LSP 3.18).
    // Эталоном для каждого диапазона служит результат одиночного rangeFormatting того же
    // диапазона: rangesFormatting обязан форматировать каждый диапазон независимо и теми же
    // протокольными опциями.
    var options = new FormattingOptions(4, true);
    Range firstRange = Ranges.create(4, 0, 10, 0);
    Range secondRange = Ranges.create(12, 0, 23, 0);

    String fileContent = FileUtils.readFileToString(getTestFile(), StandardCharsets.UTF_8);

    var documentContext = TestUtils.getDocumentContext(
      Absolute.uri(getTextDocumentIdentifier().getUri()),
      fileContent
    );

    var firstSingle = new DocumentRangeFormattingParams();
    firstSingle.setTextDocument(getTextDocumentIdentifier());
    firstSingle.setRange(firstRange);
    firstSingle.setOptions(options);
    String expectedFirstRange = formatProvider.getRangeFormatting(firstSingle, documentContext)
      .getFirst().getNewText();

    var secondSingle = new DocumentRangeFormattingParams();
    secondSingle.setTextDocument(getTextDocumentIdentifier());
    secondSingle.setRange(secondRange);
    secondSingle.setOptions(options);
    String expectedSecondRange = formatProvider.getRangeFormatting(secondSingle, documentContext)
      .getFirst().getNewText();

    var params = new DocumentRangesFormattingParams();
    params.setTextDocument(getTextDocumentIdentifier());
    params.setRanges(List.of(firstRange, secondRange));
    params.setOptions(options);

    // when
    List<TextEdit> textEdits = formatProvider.getRangesFormatting(params, documentContext);

    // then: по одной правке на каждый диапазон, и каждый отформатирован независимо
    assertThat(textEdits).hasSize(2);
    assertThat(textEdits.get(0).getNewText()).isEqualTo(expectedFirstRange);
    assertThat(textEdits.get(1).getNewText()).isEqualTo(expectedSecondRange);
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
  void testFormatTrimsTrailingWhitespace() throws IOException {
    // given
    DocumentFormattingParams params = new DocumentFormattingParams();
    params.setTextDocument(getTextDocumentIdentifier());
    var options = new FormattingOptions(4, true);
    options.setTrimTrailingWhitespace(true);
    params.setOptions(options);

    String fileContent = FileUtils.readFileToString(getTestFile(), StandardCharsets.UTF_8);

    var documentContext = TestUtils.getDocumentContext(
      Absolute.uri(params.getTextDocument().getUri()),
      fileContent
    );

    // when
    List<TextEdit> textEdits = formatProvider.getFormatting(params, documentContext);

    // then
    assertThat(textEdits).hasSize(1);

    TextEdit textEdit = textEdits.getFirst();
    String[] resultLines = textEdit.getNewText().split("\n", -1);
    for (String line : resultLines) {
      assertThat(line)
        .as("line must not have trailing whitespace: <%s>", line)
        .isEqualTo(line.stripTrailing());
    }
  }

  @Test
  void testFormatInsertsFinalNewline() {
    // given
    String fileContent = "А = 1;";
    DocumentFormattingParams params = new DocumentFormattingParams();
    params.setTextDocument(getTextDocumentIdentifier());
    var options = new FormattingOptions(4, true);
    options.setInsertFinalNewline(true);
    params.setOptions(options);

    var documentContext = TestUtils.getDocumentContext(
      Absolute.uri(params.getTextDocument().getUri()),
      fileContent
    );

    // when
    List<TextEdit> textEdits = formatProvider.getFormatting(params, documentContext);

    // then
    assertThat(textEdits).hasSize(1);

    TextEdit textEdit = textEdits.getFirst();
    assertThat(textEdit.getNewText()).endsWith("\n");
    assertThat(textEdit.getNewText()).doesNotEndWith("\n\n");
  }

  @Test
  void testFormatTrimsFinalNewlinesKeepsSingleNewline() {
    // given: документ с лишними пустыми строками в конце и обе хвостовые опции взведены
    String fileContent = "А = 1;\n\n\n\n";
    DocumentFormattingParams params = new DocumentFormattingParams();
    params.setTextDocument(getTextDocumentIdentifier());
    var options = new FormattingOptions(4, true);
    options.setInsertFinalNewline(true);
    options.setTrimFinalNewlines(true);
    params.setOptions(options);

    var documentContext = TestUtils.getDocumentContext(
      Absolute.uri(params.getTextDocument().getUri()),
      fileContent
    );

    // when
    List<TextEdit> textEdits = formatProvider.getFormatting(params, documentContext);

    // then: на хвосте ровно один перевод строки, без лишних пустых строк
    assertThat(textEdits).hasSize(1);

    TextEdit textEdit = textEdits.getFirst();
    assertThat(textEdit.getNewText()).endsWith("\n");
    assertThat(textEdit.getNewText()).doesNotEndWith("\n\n");
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
    assertThat(edit.getRange()).isEqualTo(new Range(new Position(0, 0), new Position(0, 14)));
    assertThat(edit.getNewText()).isEqualTo("Если х = 1 Тогда");
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
    assertThat(edit.getRange()).isEqualTo(new Range(new Position(1, 0), new Position(1, 12)));
    assertThat(edit.getNewText()).isEqualTo("    Возврат;");
  }

  @Test
  void testOnTypeFormattingEnterPreservesTrailingNewline() {
    // регрессия PR #3908: edit покрывал только что набранный перевод строки,
    // и при отсутствии хвостового переноса в newText editor удалял новую строку.
    String fileContent = "А = 1;\n";
    var params = onTypeParams("\n", 1, 0);

    var documentContext = TestUtils.getDocumentContext(
      URI.create(params.getTextDocument().getUri()),
      fileContent
    );

    List<TextEdit> textEdits = formatProvider.getOnTypeFormatting(params, documentContext);

    // диапазон правки не должен переходить на следующую строку
    assertThat(textEdits).hasSize(1);
    TextEdit edit = textEdits.getFirst();
    assertThat(edit.getRange().getEnd().getLine()).isZero();
    // граница диапазона — конец исходной строки "А = 1;" (6 UTF-16 единиц)
    assertThat(edit.getRange().getEnd().getCharacter()).isEqualTo(6);
    assertThat(edit.getNewText()).doesNotEndWith("\n");
    assertThat(edit.getNewText()).doesNotEndWith("\r");
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
  void testOnTypeFormattingSemicolonExcludesTokensExtendingPastCursor() {
    // given: пользователь набрал `;` внутри строкового литерала: было `"abc"`, стало `"a;bc"`.
    // STRING-токен начинается на col 0, длина 6 — выходит за курсор (col 3).
    // Такой токен не должен попадать в правку, иначе хвост литерала задублируется.
    String fileContent = "\"a;bc\"";
    var params = onTypeParams(";", 0, 3);

    var documentContext = TestUtils.getDocumentContext(
      URI.create(params.getTextDocument().getUri()),
      fileContent
    );

    // when
    List<TextEdit> textEdits = formatProvider.getOnTypeFormatting(params, documentContext);

    // then: единственный токен строки выходит за курсор и отфильтрован, форматировать нечего
    assertThat(textEdits).isEmpty();
  }

  @Test
  void testOnTypeFormattingSemicolonClampsCursorPastLineEnd() {
    // given: рассинхрон клиента (LSP4IJ) — серверная копия строки ещё без набранной `;`,
    // поэтому курсор (col 19) оказывается правее фактического конца строки (18 символов).
    // Раньше ветка `;` не клампила позицию, диапазон правки уезжал за конец строки и при
    // применении затирал только что набранную `;`.
    String fileContent = "f = Новый Массив()";
    var params = onTypeParams(";", 0, 19);

    var documentContext = TestUtils.getDocumentContext(
      Absolute.uri(params.getTextDocument().getUri()),
      fileContent
    );

    // when
    List<TextEdit> textEdits = formatProvider.getOnTypeFormatting(params, documentContext);

    // then: диапазон правки не выходит за конец синхронизированной строки и не содержит переноса
    assertThat(textEdits).hasSize(1);
    TextEdit edit = textEdits.getFirst();
    assertThat(edit.getRange().getEnd().getCharacter()).isEqualTo(18);
    assertThat(edit.getNewText()).doesNotContain("\n");
    assertThat(edit.getNewText()).isEqualTo("f = Новый Массив()");
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

  @Test
  void testOnTypeFormattingDisabledByConfig() {
    // given: настройка useOnTypeFormatting = false должна полностью отключать провайдер
    configuration.update(new File("./src/test/resources/.bsl-language-server-format-on-type-off.json"));

    String fileContent = "если х=1 тогда\n\n";
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
  void testOnTypeFormattingSemicolonPreservesLeadingTab() {
    // регрессия: `\tконецесли` + `;` ранее съедал ведущую табуляцию, потому что
    // первый токен — decrement-keyword и getNewText сбрасывал indentLevel в 0.
    // Теперь ведущий whitespace вне replace-диапазона и подменяется явно.
    String fileContent = "\tконецесли;";
    var params = onTypeParams(";", 0, 11);

    var documentContext = TestUtils.getDocumentContext(
      URI.create(params.getTextDocument().getUri()),
      fileContent
    );

    List<TextEdit> textEdits = formatProvider.getOnTypeFormatting(params, documentContext);

    assertThat(textEdits).hasSize(1);
    TextEdit edit = textEdits.getFirst();
    // Парного `Если` нет → fallback: сохраняем фактический отступ строки.
    assertThat(edit.getNewText()).isEqualTo("\tКонецЕсли;");
  }

  @Test
  void testOnTypeFormattingEnterAlignsClosingKeywordToOpener() {
    // фича: `КонецЕсли` с криво проставленным отступом выравнивается по `Если`.
    String fileContent = "Если Истина Тогда\n        КонецЕсли\n\n";
    var params = onTypeParams("\n", 2, 0);

    var documentContext = TestUtils.getDocumentContext(
      URI.create(params.getTextDocument().getUri()),
      fileContent
    );

    List<TextEdit> textEdits = formatProvider.getOnTypeFormatting(params, documentContext);

    assertThat(textEdits).hasSize(1);
    TextEdit edit = textEdits.getFirst();
    // Если на col 0 → КонецЕсли подтягивается к col 0
    assertThat(edit.getNewText()).isEqualTo("КонецЕсли");
  }

  @Test
  void testOnTypeFormattingEnterAlignsEndProcedureToProcedure() {
    String fileContent = "    Процедура П()\n  КонецПроцедуры\n\n";
    var params = onTypeParams("\n", 2, 0);

    var documentContext = TestUtils.getDocumentContext(
      URI.create(params.getTextDocument().getUri()),
      fileContent
    );

    List<TextEdit> textEdits = formatProvider.getOnTypeFormatting(params, documentContext);

    assertThat(textEdits).hasSize(1);
    TextEdit edit = textEdits.getFirst();
    // ведущий отступ `Процедура` = 4 пробела → `КонецПроцедуры` тоже 4 пробела
    assertThat(edit.getNewText()).isEqualTo("    КонецПроцедуры");
  }

  @Test
  void testOnTypeFormattingEnterAlignsElseToIf() {
    String fileContent = "    Если Истина Тогда\nИначе\n\n";
    var params = onTypeParams("\n", 2, 0);

    var documentContext = TestUtils.getDocumentContext(
      URI.create(params.getTextDocument().getUri()),
      fileContent
    );

    List<TextEdit> textEdits = formatProvider.getOnTypeFormatting(params, documentContext);

    assertThat(textEdits).hasSize(1);
    TextEdit edit = textEdits.getFirst();
    assertThat(edit.getNewText()).isEqualTo("    Иначе");
  }

  @Test
  void testOnTypeFormattingEnterAlignsClosingKeywordThroughNestedBlock() {
    // На LSP-line 4 кривой `КонецЕсли` — он закрывает ВНЕШНЕЕ `Если` на line 0,
    // несмотря на вложенный `Если/КонецЕсли` на line 1-3.
    String fileContent = """
      Если А Тогда
        Если Б Тогда
          Возврат;
        КонецЕсли;
              КонецЕсли

      """;
    var params = onTypeParams("\n", 5, 0);

    var documentContext = TestUtils.getDocumentContext(
      URI.create(params.getTextDocument().getUri()),
      fileContent
    );

    List<TextEdit> textEdits = formatProvider.getOnTypeFormatting(params, documentContext);

    assertThat(textEdits).hasSize(1);
    TextEdit edit = textEdits.getFirst();
    // внешнее `Если` на col 0 → закрывающий КонецЕсли тоже на col 0
    assertThat(edit.getNewText()).isEqualTo("КонецЕсли");
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
