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
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompletionMetadataNamesTest extends AbstractServerContextAwareTest {
  @Autowired
  private CompletionProvider provider;
  @TempDir
  Path workspace;

  @ParameterizedTest
  @EnumSource(Language.class)
  void ordinaryCompletionOffersCollectionsWithoutWholeObjectPaths(Language language) throws IOException {
    prepare(language);
    var catalogs = language == Language.RU ? "Справочники" : "Catalogs";
    var other = language == Language.RU ? "Catalogs" : "Справочники";
    for (var prefix : List.of("", catalogs.substring(0, 3))) {
      var items = complete(prefix + "|", language);

      assertThat(items).extracting(CompletionItem::getLabel).contains(catalogs).doesNotContain(other,
        "Справочники.Справочник1", "Catalogs.Справочник1", "Документы.Документ1", "Documents.Документ1");
      assertThat(items).filteredOn(item -> item.getKind() == CompletionItemKind.Module)
        .extracting(CompletionItem::getLabel).noneMatch(name -> name.contains("."));
    }
  }

  @ParameterizedTest
  @CsvSource({"RU, Справочники", "RU, Catalogs", "EN, Catalogs", "EN, Справочники"})
  void collectionMembersKeepTheirMetadataNamesForEitherAlias(Language language, String collection) throws IOException {
    prepare(language);

    assertThat(complete(collection + ".|", language)).extracting(CompletionItem::getLabel)
      .contains("Справочник1", "СправочникСМенеджером")
      .doesNotContain("Справочники.Справочник1", "Catalogs.Справочник1");
    assertThat(complete(collection + ".Справочник1.|", language)).extracting(CompletionItem::getLabel)
      .contains("ПредопределённыйЭлемент1", "ПредопределённыйЭлемент2");
  }

  @ParameterizedTest
  @EnumSource(Language.class)
  void constructorsUseTheRegisteredLanguageAndKeepOneAlias(Language language) throws IOException {
    prepare(language);
    var expected = language == Language.RU ? "Массив" : "Array";
    var other = language == Language.RU ? "Array" : "Массив";
    var items = complete("Value = New |", language);

    assertThat(items).extracting(CompletionItem::getLabel).contains(expected).doesNotContain(other);
    assertThat(items).filteredOn(item -> item.getLabel().equals(expected)).hasSize(1);
  }

  @ParameterizedTest
  @EnumSource(Language.class)
  void metadataAttributesAndTabularSectionsKeepTheirNames(Language language) throws IOException {
    prepare(language);
    var declaration = "Value = Документы.Документ1.СоздатьДокумент();\n";

    assertThat(complete(declaration + "Value.|", language)).extracting(CompletionItem::getLabel)
      .contains("ОбщийРеквизит1", "ТабличнаяЧасть1");
    assertThat(complete(declaration + "Value.ТабличнаяЧасть1.Добавить().|", language))
      .extracting(CompletionItem::getLabel).contains("Реквизит1", "Реквизит2");
  }

  private void prepare(Language language) throws IOException {
    FileUtils.copyDirectory(Path.of(TestUtils.PATH_TO_METADATA).toFile(), workspace.toFile());
    var configuration = workspace.resolve("Configuration.xml");
    Files.writeString(configuration, Files.readString(configuration).replace(
      "<ScriptVariant>Russian</ScriptVariant>",
      "<ScriptVariant>" + (language == Language.RU ? "Russian" : "English") + "</ScriptVariant>"));
    initServerContext(workspace);
    context.getLanguageServerConfiguration().setLanguage(language == Language.RU ? Language.EN : Language.RU);
  }

  private List<CompletionItem> complete(String markedLine, Language language) {
    var source = "Procedure Example()\n" + markedLine + "\nEndProcedure";
    var document = TestUtils.getDocumentContext(workspace.resolve("completion.bsl").toUri(),
      source.replace("|", ""), context);
    assertThat(document.getScriptVariantLanguage()).isEqualTo(language);
    var before = source.substring(0, source.indexOf('|'));
    var position = new Position((int) before.chars().filter(ch -> ch == '\n').count(),
      before.length() - before.lastIndexOf('\n') - 1);
    return provider.getCompletion(document, new CompletionParams(
      new TextDocumentIdentifier(document.getUri().toString()), position)).getItems();
  }
}
