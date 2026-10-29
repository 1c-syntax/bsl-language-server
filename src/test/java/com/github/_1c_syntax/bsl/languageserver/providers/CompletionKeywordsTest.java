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
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CompletionKeywordsTest extends AbstractServerContextAwareTest {
  @Autowired
  private CompletionProvider provider;
  @TempDir
  Path workspace;

  @BeforeEach
  void prepare() {
    initServerContext();
  }

  @ParameterizedTest
  @CsvSource({"BSL, RU", "BSL, EN", "OS, RU", "OS, EN"})
  void languageAndRealKeywordsAreSelectedTogether(FileType fileType, Language language) {
    context.getLanguageServerConfiguration().setLanguage(language);
    var words = complete("<caret>", fileType);

    assertThat(words).doesNotContain("DefVal", "DefaultValue", "Declarations", "By",
      "ДефЗнач", "ЗначениеПоУмолчанию", "Объявления", "ElseIf")
      .doesNotHaveDuplicates().contains("Null").doesNotContain("NULL");
    if (language == Language.RU) {
      assertThat(words).contains("Если", "Для", "Истина", "Процедура")
        .doesNotContain("If", "For", "True", "Procedure");
    } else {
      assertThat(words).contains("If", "For", "True", "Procedure")
        .doesNotContain("Если", "Для", "Истина", "Процедура");
    }
  }

  @ParameterizedTest
  @CsvSource({"BSL, RU", "BSL, EN", "OS, RU", "OS, EN"})
  void asynchronousKeywordsFollowRuntimeSupport(FileType fileType, Language language) {
    context.getLanguageServerConfiguration().setLanguage(language);
    var async = language == Language.RU ? "Асинх" : "Async";
    var await = language == Language.RU ? "Ждать" : "Await";

    assertThat(complete("<caret>", fileType).contains(async)).isEqualTo(fileType == FileType.BSL);
    assertThat(complete("Async Procedure P()\n<caret>\nEndProcedure", fileType).contains(await))
      .isEqualTo(fileType == FileType.BSL);
    assertThat(complete("Async Function F()\n<caret>\nEndFunction", fileType).contains(await))
      .isEqualTo(fileType == FileType.BSL);
  }

  @ParameterizedTest
  @EnumSource(Language.class)
  void projectScriptLanguageTakesPrecedenceOverInterfaceLanguage(Language language) throws IOException {
    FileUtils.copyDirectory(Path.of("src/test/resources/metadata/commonModuleCalls").toFile(), workspace.toFile());
    var configuration = workspace.resolve("Configuration.xml");
    Files.writeString(configuration, Files.readString(configuration).replace("<ScriptVariant>Russian</ScriptVariant>",
      "<ScriptVariant>" + (language == Language.RU ? "Russian" : "English") + "</ScriptVariant>"));
    initServerContext(workspace);
    context.getLanguageServerConfiguration().setLanguage(language == Language.RU ? Language.EN : Language.RU);

    var words = complete("<caret>", FileType.BSL);
    assertThat(words).contains(language == Language.RU ? "Если" : "If")
      .doesNotContain(language == Language.RU ? "If" : "Если");
  }

  @ParameterizedTest
  @MethodSource("positions")
  void onlyContextualWordsForTheCurrentConstructAreOffered(String source, List<String> present, List<String> absent) {
    context.getLanguageServerConfiguration().setLanguage(Language.EN);
    var words = complete(source, FileType.BSL);

    assertThat(words).containsAll(present).doesNotContainAnyElementsOf(absent);
  }

  private static Stream<Arguments> positions() {
    return Stream.of(
      point("<caret>", List.of("Procedure", "Function", "Async"),
        List.of("Continue", "Break", "EndDo", "EndIf", "Else", "ElsIf", "Then", "Do", "To", "In", "Each",
          "Except", "EndTry", "EndProcedure", "EndFunction", "Val", "Export", "Await")),
      point("Procedure P()\n<caret>\nEndProcedure", List.of("EndProcedure", "If"),
        List.of("Procedure", "Function", "Async", "EndFunction", "Continue", "Await")),
      point("Function F()\n<caret>\nEndFunction", List.of("EndFunction", "Return"), List.of("EndProcedure")),
      point("While True Do\n<caret>\nEndDo", List.of("Break", "Continue", "EndDo"), List.of("Do", "Procedure")),
      point("For i = 1 To 5 Do\n<caret>\nEndDo", List.of("Break", "Continue", "EndDo"), List.of("To", "Do")),
      point("For Each item In items Do\n<caret>\nEndDo", List.of("Continue", "Break"), List.of("In", "Each", "To")),
      point("While True Do\nIf True Then\n<caret>\nEndIf\nEndDo", List.of("Continue", "Break", "EndIf"), List.of("EndDo")),
      point("While True Do\nIf True <caret>\nEndIf\nEndDo", List.of("Then"), List.of("Continue", "Break", "EndDo")),
      point("While True Do\nCall(<caret>);\nEndDo", List.of("True", "New"), List.of("Continue", "Break", "EndDo", "If")),
      point("If Call(True <caret>) Then\nEndIf", List.of("And", "Or"), List.of("Then", "Else", "EndIf")),
      point("Procedure P()\nCall(<caret>);\nEndProcedure", List.of("True", "New"), List.of("EndProcedure", "Export")),
      point("While True Do\nIf True Then\nEndIf;\n<caret>\nEndDo", List.of("Continue", "EndDo"), List.of("Else", "EndIf")),
      point("While True Do\nWhile False Do\nEndDo;\n<caret>\nEndDo", List.of("Continue", "Break"), List.of("Else")),
      point("While True Do\nEndDo;\n<caret>", List.of("If"), List.of("Continue", "Break", "EndDo")),
      point("While True Do\nEndD<caret>", List.of("EndDo"), List.of("Continue")),
      point("While True Do\nEndDo<caret>", List.of("EndDo"), List.of("Continue")),
      point("Procedure P()\nEndProcedure<caret>", List.of("EndProcedure"), List.of("Procedure")),
      point("Procedure P()\nWhile True Do\n<caret>", List.of("Continue", "EndDo"), List.of("EndProcedure")),
      point("Procedure P()\nWhile True Do\nEndProcedure\nProcedure Q()\n<caret>\nEndProcedure",
        List.of("EndProcedure"), List.of("Continue", "Break", "EndDo")),
      point("If True <caret>", List.of("Then"), List.of("Else", "EndIf")),
      point("If True Then\n<caret>\nEndIf", List.of("Else", "ElsIf", "EndIf"), List.of("Then")),
      point("If True Then\nElse\n<caret>\nEndIf", List.of("EndIf"), List.of("Else", "ElsIf")),
      point("If True Then\nElsIf False <caret>", List.of("Then"), List.of("Else", "EndIf")),
      point("Try\n<caret>\nExcept\nEndTry", List.of("Except"), List.of("EndTry")),
      point("Try\nExcept\n<caret>\nEndTry", List.of("EndTry", "Raise"), List.of("Except")),
      point("For <caret>", List.of("Each"), List.of("To", "In", "Do", "Continue")),
      point("For i = 1 <caret>", List.of("To"), List.of("By", "Each", "In", "Do", "Continue")),
      point("For i = 1 To 5 <caret>", List.of("Do"), List.of("To", "In", "Each", "Continue")),
      point("For Each item <caret>", List.of("In"), List.of("To", "Each", "Do")),
      point("For Each item In items <caret>", List.of("Do"), List.of("To", "In", "Each")),
      point("While True <caret>", List.of("Do"), List.of("Continue", "Break", "EndDo")),
      point("Async Procedure P()\n<caret>\nEndProcedure", List.of("Await"), List.of("Async")),
      point("Async Function F()\n<caret>\nEndFunction", List.of("Await"), List.of("Async")),
      point("Procedure P(<caret>)\nEndProcedure", List.of("Val"), List.of("If", "True", "Export", "Await")),
      point("Procedure P(Value = <caret>)\nEndProcedure", List.of("True", "False", "Undefined"), List.of("Val", "If")),
      point("Procedure P() <caret>\nEndProcedure", List.of("Export"), List.of("Val")),
      point("Var Name <caret>", List.of("Export"), List.of("Val")),
      point("Procedure P()\nVar Name <caret>\nEndProcedure", List.of("If"), List.of("Export")),
      point("// While True Do\nValue = \"If True Then\";\nObject.If = True;\n<caret>",
        List.of("If"), List.of("Continue", "Break", "Else", "EndIf")),
      point("#If Server Then\nWhile True Do\n#EndIf\n<caret>\nEndDo", List.of("Continue"), List.of("Else"))
    );
  }

  private static Arguments point(String source, List<String> present, List<String> absent) {
    return Arguments.of(source, present, absent);
  }

  @ParameterizedTest
  @ValueSource(strings = {"// Con<caret>", "Text = \"Con<caret>\";", "Text = \"\n|While True Do\n|Con<caret>\";",
    "#If Ser<caret> Then\n#EndIf", "&AtCli<caret>"})
  void keywordsAreNotInsertedIntoCommentsStringsOrDirectives(String source) {
    assertThat(complete(source, FileType.BSL)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"BSL, RU, Продол, Продолжить", "BSL, EN, Cont, Continue",
    "OS, RU, Продол, Продолжить", "OS, EN, Cont, Continue", "BSL, EN, Continue, Continue"})
  void partiallyOrFullyTypedKeywordDoesNotCloseItsOwnContext(FileType fileType, Language language,
                                                              String prefix, String expected) {
    context.getLanguageServerConfiguration().setLanguage(language);
    assertThat(complete("While True Do\n" + prefix + "<caret>\nEndDo", fileType)).contains(expected);
  }

  private List<String> complete(String source, FileType fileType) {
    var before = source.substring(0, source.indexOf("<caret>"));
    var position = new Position((int) before.chars().filter(ch -> ch == '\n').count(),
      before.length() - before.lastIndexOf('\n') - 1);
    var document = TestUtils.getDocumentContext(URI.create("file:///keywords." + (fileType == FileType.BSL ? "bsl" : "os")),
      source.replace("<caret>", ""), context);
    return provider.getCompletion(document, new CompletionParams(new TextDocumentIdentifier(document.getUri().toString()),
      position)).getItems().stream().filter(item -> item.getKind() == CompletionItemKind.Keyword)
      .map(CompletionItem::getLabel).toList();
  }
}
