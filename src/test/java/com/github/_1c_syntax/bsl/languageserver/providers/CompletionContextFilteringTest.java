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

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.Availability;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CompletionContextFilteringTest extends AbstractServerContextAwareTest {
  private static final Path ROOT = Path.of("src/test/resources/metadata/commonModuleCalls");
  private static final String SERVER_CALLER = """
    &AtClient
    Procedure ClientMethod()
    EndProcedure
    &AtServerNoContext
    Procedure ServerMethod()
    EndProcedure
    &AtServerNoContext
    Procedure Caller()
      Локальная = 1;
      |
    EndProcedure
    """;

  @Autowired
  private CompletionProvider provider;
  @Autowired
  private TypeRegistry registry;
  @TempDir
  Path temporary;

  @BeforeEach
  void workspace() {
    initServerContext(ROOT);
  }

  @Test
  void hidesUnavailableLocalMethodsByDefaultAndKeepsVariablesAndKeywords() {
    assertThat(context.getLanguageServerConfiguration().getCompletionOptions().isHideUnavailableByContext()).isTrue();

    assertThat(form(SERVER_CALLER)).extracting(CompletionItem::getLabel)
      .contains("ServerMethod", "Локальная", "Если").doesNotContain("ClientMethod");
  }

  @Test
  void hidesContextualServerMethodFromAContextFreeCaller() {
    assertThat(form("""
      &AtServer
      Procedure Contextual()
      EndProcedure
      &AtServerNoContext
      Procedure Caller()
        |
      EndProcedure
      """)).extracting(CompletionItem::getLabel).contains("Caller").doesNotContain("Contextual");
  }

  @Test
  void permittedServerTransitionRemainsInClientCompletion() {
    assertThat(form("""
      &AtServerNoContext
      Procedure ServerMethod()
      EndProcedure
      &AtClient
      Procedure Caller()
        |
      EndProcedure
      """)).extracting(CompletionItem::getLabel).contains("ServerMethod", "СерверныйМодульВызовСервера")
      .doesNotContain("СерверныйМодуль");
  }

  @ParameterizedTest
  @ValueSource(strings = {"AtClient", "AtServerNoContext"})
  void commonModuleNamesUseMetadataEvenBeforeTheirFilesAreParsed(String directive) {
    initServerContext(ROOT, false);
    var items = form("&" + directive + "\nProcedure Caller()\n  |\nEndProcedure");
    var client = directive.equals("AtClient");

    assertThat(items).extracting(CompletionItem::getLabel)
      .contains(client ? "КлиентскийМодуль" : "СерверныйМодуль")
      .doesNotContain(client ? "СерверныйМодуль" : "КлиентскийМодуль");
  }

  @Test
  void preprocessorExcludesLocalNamesakeButKeepsTheAvailableGlobal() {
    var items = form("""
      #If ThinClient Then
      &AtClient
      Procedure Сообщить()
      EndProcedure
      #EndIf
      &AtClient
      Procedure Caller()
        #If WebClient Then
          Сообщ|
        #EndIf
      EndProcedure
      """);

    assertThat(items).filteredOn(item -> item.getLabel().equals("Сообщить")).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void filteringUsesTheSharedFileModePolicy(boolean fileMode) {
    context.getLanguageServerConfiguration().setSupportThickClientFileMode(fileMode);
    var items = form("""
      &AtClient
      Procedure Caller()
        |
      EndProcedure
      """);

    assertThat(items.stream().anyMatch(item -> item.getLabel().equals("СерверныйМодуль"))).isEqualTo(fileMode);
  }

  @Test
  void dotCompletionUsesTheServerSideOfARemoteCall() {
    TestUtils.getDocumentContext(ROOT.resolve("CommonModules/СерверныйМодульВызовСервера/Ext/Module.bsl").toUri(), """
      #If Server Then
      Procedure Available() Export
      EndProcedure
      #Else
      Procedure Excluded() Export
      EndProcedure
      #EndIf
      """, context);

    assertThat(form("""
      &AtClient
      Procedure Caller()
        СерверныйМодульВызовСервера.|
      EndProcedure
      """)).extracting(CompletionItem::getLabel).contains("Available").doesNotContain("Excluded");
  }

  @Test
  void platformMembersAreFilteredButUnknownAvailabilityIsPreserved() {
    registry.registerMemberOverride(registry.intern(TypeKind.PLATFORM, "ГлобальныйКонтекст"), () -> List.of(
      MemberDescriptor.method("КонтекстСервер", "", List.of()).withMetadata(metadata(Set.of(Availability.SERVER))),
      MemberDescriptor.property("КонтекстСвойство", TypeRef.UNKNOWN, "")
        .withMetadata(metadata(Set.of(Availability.SERVER))),
      MemberDescriptor.method("КонтекстНеизвестно", "", List.of())
    ), FileType.BSL);

    assertThat(form("""
      &AtClient
      Procedure Caller()
        Контекст|
      EndProcedure
      """)).extracting(CompletionItem::getLabel).contains("КонтекстНеизвестно")
      .doesNotContain("КонтекстСервер", "КонтекстСвойство");
  }

  @Test
  void constructorsUseTypeAvailability() {
    registry.registerTypeMetadata(registry.resolve("Массив").orElseThrow(),
      metadata(Set.of(Availability.SERVER)), FileType.BSL);

    assertThat(form("""
      &AtClient
      Procedure Caller()
        Value = New |
      EndProcedure
      """)).extracting(CompletionItem::getLabel).contains("Структура").doesNotContain("Массив");
  }

  @Test
  void partialAvailabilityIsPreservedAndContradictoryDeclarationIsHidden() {
    var items = complete(ROOT.resolve("CommonModules/КлиентСерверныйМодуль/Ext/Module.bsl").toUri(), """
      #If Client Then
      Procedure Partial()
      EndProcedure
      #EndIf
      #If Server And Not Server Then
      Procedure Impossible()
      EndProcedure
      #EndIf
      Procedure Caller()
        |
      EndProcedure
      """);

    assertThat(items).extracting(CompletionItem::getLabel).contains("Partial", "Caller").doesNotContain("Impossible");
    assertThat(items).filteredOn(item -> item.getLabel().equals("Partial")).singleElement()
      .satisfies(item -> assertThat(item.getSortText()).startsWith("1_"));
  }

  @Test
  void anAvailableReceiverAlternativeKeepsItsMember() {
    assertThat(form("""
      &AtClient
      Procedure Caller(Condition)
        If Condition Then
          Receiver = КлиентскийМодуль;
        Else
          Receiver = СерверныйМодуль;
        EndIf;
        Receiver.|
      EndProcedure
      """)).extracting(CompletionItem::getLabel).contains("Цель");
  }

  @ParameterizedTest
  @ValueSource(strings = {"bsl", "os"})
  void missingMetadataAndOneScriptDoNotHideSuggestions(String extension) {
    initServerContext();

    assertThat(complete(URI.create("file:///completion-context." + extension), SERVER_CALLER))
      .extracting(CompletionItem::getLabel).contains("ClientMethod", "ServerMethod");
  }

  @Test
  void unknownConditionDoesNotHideSuggestions() {
    assertThat(form(SERVER_CALLER.replace("|", "#If Windows Then\n  |\n  #EndIf")))
      .extracting(CompletionItem::getLabel).contains("ClientMethod", "ServerMethod");
  }

  @Test
  void changingJsonConfigurationAppliesToTheNextRequestWithoutReparsing() throws IOException {
    var document = TestUtils.getDocumentContext(ROOT.resolve("CommonForms/Тест/Ext/Form/Module.bsl").toUri(),
      SERVER_CALLER.replace("|", ""), context);
    var tree = document.getSymbolTree();
    var position = position(SERVER_CALLER);
    var configuration = context.getLanguageServerConfiguration();
    var file = temporary.resolve("completion.json");
    assertThat(complete(document, position)).extracting(CompletionItem::getLabel).doesNotContain("ClientMethod");

    Files.writeString(file, "{\"completion\":{\"hideUnavailableByContext\":false}}");
    configuration.update(file.toFile());

    assertThat(configuration.getCompletionOptions().isHideUnavailableByContext()).isFalse();
    assertThat(complete(document, position)).extracting(CompletionItem::getLabel).contains("ClientMethod");
    assertThat(complete(document, position)).filteredOn(item -> item.getLabel().equals("ClientMethod"))
      .singleElement().satisfies(item -> assertThat(item.getSortText()).startsWith("2_"));
    configuration.reset();
    assertThat(configuration.getCompletionOptions().isHideUnavailableByContext()).isTrue();
    assertThat(complete(document, position)).extracting(CompletionItem::getLabel).doesNotContain("ClientMethod");
    assertThat(document.getSymbolTree()).isSameAs(tree);
  }

  @Test
  void emptyDotCompletionDoesNotRequireAMutableList() {
    assertThat(form("&AtClient\nProcedure Caller()\n  Unknown.|\nEndProcedure")).isEmpty();
  }

  private List<CompletionItem> form(String source) {
    return complete(ROOT.resolve("CommonForms/Тест/Ext/Form/Module.bsl").toUri(), source);
  }

  private List<CompletionItem> complete(URI uri, String source) {
    return complete(TestUtils.getDocumentContext(uri, source.replace("|", ""), context), position(source));
  }

  private List<CompletionItem> complete(DocumentContext document, Position position) {
    return provider.getCompletion(document,
      new CompletionParams(new TextDocumentIdentifier(document.getUri().toString()), position)).getItems();
  }

  private static Position position(String source) {
    var before = source.substring(0, source.indexOf('|'));
    return new Position((int) before.chars().filter(value -> value == '\n').count(),
      before.length() - before.lastIndexOf('\n') - 1);
  }

  private static PlatformMetadata metadata(Set<Availability> availability) {
    return new PlatformMetadata("", "", List.of(), availability, null,
      BilingualString.EMPTY, BilingualString.EMPTY, List.of(), List.of());
  }
}
