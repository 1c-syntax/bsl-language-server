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

import com.github._1c_syntax.bsl.languageserver.client.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.Availability;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.google.gson.JsonParser;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CompletionCapabilities;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemCapabilities;
import org.eclipse.lsp4j.CompletionItemResolveSupportCapabilities;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CompletionContextHintsTest extends AbstractServerContextAwareTest {
  private static final Path ROOT = Path.of("src/test/resources/metadata/commonModuleCalls");
  @TempDir
  private Path workspace;
  private Path projectRoot;
  @Autowired
  private CompletionProvider provider;
  @Autowired
  private ClientCapabilitiesHolder capabilities;
  @Autowired
  private TypeRegistry registry;
  @Autowired
  private JsonMapper jsonMapper;

  @BeforeEach
  void prepare() {
    useScriptLanguage(Language.RU);
  }

  @AfterEach
  void resetCapabilities() {
    capabilities.setCapabilities(null);
    provider.handleInitializeEvent();
  }

  @ParameterizedTest
  @CsvSource({"RU, RU, →С, Контексты компиляции", "RU, EN, →С, Контексты компиляции",
    "EN, RU, →S, Compilation contexts", "EN, EN, →S, Compilation contexts"})
  void labelsFollowScriptLanguageIndependentlyOfInterfaceLanguage(Language script, Language ui,
                                                                  String expected, String explanation) {
    useScriptLanguage(script);
    context.getLanguageServerConfiguration().setLanguage(ui);
    var target = item(form("""
      &AtServerNoContext
      Procedure Target(Value)
      EndProcedure
      &AtClient
      Procedure Caller()
        |
      EndProcedure
      """, script), "Target");

    assertThat(target.getLabelDetails().getDescription()).isEqualTo(expected);
    assertThat(target.getLabelDetails().getDetail()).isEqualTo("(Value)");
    assertThat(target.getDetail()).isNull();
    assertThat(documentation(target)).contains(explanation);
    assertThat(target.getLabel()).isEqualTo("Target");
    assertThat(target.getInsertText()).startsWith("Target(").doesNotContain("→", " · ");
  }

  @ParameterizedTest
  @CsvSource({"AtClient, →С", "AtServerNoContext,", "AtClientAtServerNoContext, →С?"})
  void transitionDependsOnTheCaller(String directive, String expected) {
    var items = form("""
      &AtServerNoContext
      Procedure Target()
      EndProcedure
      &%s
      Procedure Caller()
        |
      EndProcedure
      """.formatted(directive), Language.RU);
    assertThat(item(items, "Target").getLabelDetails().getDescription()).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"Client, →С", "Server,"})
  void preprocessorNarrowsTheCallPath(String condition, String expected) {
    var items = form("""
      &AtServerNoContext
      Procedure Target()
      EndProcedure
      &AtClientAtServerNoContext
      Procedure Caller()
      #If %s Then
        |
      #EndIf
      EndProcedure
      """.formatted(condition), Language.RU);
    assertThat(item(items, "Target").getLabelDetails().getDescription()).isEqualTo(expected);
  }

  @Test
  void clientAndSharedMethodsKeepTheirOwnSides() {
    var items = form("""
      &AtClient
      Procedure ClientMethod()
      EndProcedure
      &AtClientAtServerNoContext
      Procedure SharedMethod()
      EndProcedure
      &AtServerNoContext
      Procedure Caller()
        |
      EndProcedure
      """, Language.RU);

    assertThat(item(items, "ClientMethod").getLabelDetails().getDescription()).isNull();
    assertThat(documentation(item(items, "ClientMethod"))).contains("недоступен");
    assertThat(item(items, "SharedMethod").getLabelDetails().getDescription()).isNull();
  }

  @ParameterizedTest
  @CsvSource({"false, ThinClient, →С", "false, Client, →С", "false, Server,",
    "true, ThinClient, →С", "true, Client, →С?", "true, Server,"})
  void serverCallModuleHasNoTransitionInItsLocalFileVariant(boolean fileMode, String condition, String expected) {
    context.getLanguageServerConfiguration().setSupportThickClientFileMode(fileMode);
    module("СерверныйМодульВызовСервера", "Procedure Target() Export\nEndProcedure\n");
    var items = form("""
      &AtClientAtServerNoContext
      Procedure Caller()
      #If %s Then
        СерверныйМодульВызовСервера.|
      #EndIf
      EndProcedure
      """.formatted(condition), Language.RU);
    assertThat(item(items, "Target").getLabelDetails().getDescription()).isEqualTo(expected);
  }

  @Test
  void mergedFileContextIsNotARemoteCall() {
    context.getLanguageServerConfiguration().setSupportThickClientFileMode(true);
    module("СерверныйМодульВызовСервера", "Procedure Target() Export\nEndProcedure\n");
    var items = complete(projectRoot.resolve("CommonModules/КлиентСерверныйМодуль/Ext/Module.bsl").toUri(), """
      Procedure Caller()
      #If Client And Server Then
        СерверныйМодульВызовСервера.|
      #EndIf
      EndProcedure
      """, Language.RU);
    assertThat(item(items, "Target").getLabelDetails().getDescription()).isNull();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void runtimeAlternativesDoNotClaimAnUnconditionalServerCall(boolean reverse) {
    useScriptLanguage(Language.EN);
    module("СерверныйМодульВызовСервера", "Procedure Target() Export\nEndProcedure\n");
    module("КлиентскийМодуль", "Procedure Target() Export\nEndProcedure\n");
    var first = reverse ? "КлиентскийМодуль" : "СерверныйМодульВызовСервера";
    var second = reverse ? "СерверныйМодульВызовСервера" : "КлиентскийМодуль";
    var items = form("""
      &AtClient
      Procedure Caller(Condition)
      #If ThinClient Then
        If Condition Then
          Receiver = %s;
        Else
          Receiver = %s;
        EndIf;
        Receiver.|
      #EndIf
      EndProcedure
      """.formatted(first, second), Language.EN);

    assertThat(item(items, "Target").getLabelDetails().getDescription()).isEqualTo("→S?");
  }

  @Test
  void alternativeDeclarationsAreCombinedForQualifiedCompletion() {
    module("КлиентСерверныйМодуль", """
      #If Client Then
      Procedure Target() Export
      EndProcedure
      #Else
      Procedure Target() Export
      EndProcedure
      #EndIf
      """);
    var items = form("""
      &AtClient
      Procedure Caller()
        КлиентСерверныйМодуль.|
      EndProcedure
      """, Language.RU);
    assertThat(item(items, "Target").getLabelDetails().getDescription()).isNull();
  }

  @ParameterizedTest
  @EnumSource(Language.class)
  void inactiveReceiverBranchesDoNotAddServerArrows(Language language) {
    useScriptLanguage(language);
    module("СерверныйМодульВызовСервера", "Procedure Target() Export\nEndProcedure\n");
    module("КлиентскийМодуль", "Procedure Target() Export\nEndProcedure\n");
    var items = form("""
      &AtClientAtServerNoContext
      Procedure Caller()
      #If Server Then
        Receiver = СерверныйМодульВызовСервера;
      #Else
        Receiver = КлиентскийМодуль;
      #EndIf
      #If ThinClient Then
        Receiver.|
      #EndIf
      EndProcedure
      """, language);
    assertThat(item(items, "Target").getLabelDetails().getDescription()).isNull();
  }

  @Test
  void impossibleDeclarationsAreDistinctFromUnknownContexts() {
    var target = item(form("""
      #If Server And Not Server Then
      &AtServerNoContext
      Procedure Target()
      EndProcedure
      #EndIf
      &AtClient
      Procedure Caller()
        |
      EndProcedure
      """, Language.RU), "Target");
    assertThat(target.getLabelDetails().getDescription()).isNull();
    assertThat(documentation(target)).contains("нет доступного объявления", "Вызов недоступен");
  }

  @ParameterizedTest
  @EnumSource(Language.class)
  void fileOnlyDeclarationsAreNotMislabelledAsOrdinaryClientMethods(Language language) {
    useScriptLanguage(language);
    context.getLanguageServerConfiguration().setSupportThickClientFileMode(true);
    var items = complete(projectRoot.resolve("CommonModules/СерверныйМодуль/Ext/Module.bsl").toUri(), """
      #If Client And Server Then
      Procedure Target()
      EndProcedure
      #EndIf
      Procedure Caller()
        |
      EndProcedure
      """, language);
    assertThat(item(items, "Target").getLabelDetails().getDescription()).isNull();
    assertThat(documentation(item(items, "Target")))
      .contains(language == Language.EN ? "file mode" : "файловый режим");
  }

  @ParameterizedTest
  @CsvSource({"RU, map", "EN, map", "RU, json", "EN, json"})
  void returnTypeAndDocumentationSurviveLazyResolution(Language language, String format) {
    useScriptLanguage(language);
    module("СерверныйМодульВызовСервера", """
      // Loads the value.
      //
      // Returns:
      //  String - loaded value.
      Function Target(Value) Export
        Return "result";
      EndFunction
      """);
    var target = item(form("""
      &AtClient
      Procedure Caller()
      #If ThinClient Then
        СерверныйМодульВызовСервера.|
      #EndIf
      EndProcedure
      """, language), "Target");
    var description = target.getLabelDetails().getDescription();
    assertThat(target.getDetail()).isNull();
    assertThat(description).startsWith(language == Language.EN ? "→S · " : "→С · ");
    assertThat(description).endsWith(language == Language.EN ? "String" : "Строка");
    assertThat(target.getLabelDetails().getDetail()).contains("Value");

    target.setData(format.equals("map") ? jsonMapper.convertValue(target.getData(), Map.class)
      : JsonParser.parseString(jsonMapper.writeValueAsString(target.getData())));
    provider.resolveCompletionItem(target, provider.extractData(target));

    assertThat(target.getLabelDetails().getDescription()).isEqualTo(description);
    assertThat(target.getDetail()).isNull();
    var doc = target.getDocumentation().getRight().getValue();
    var contextTitle = language == Language.EN ? "Compilation contexts:" : "Контексты компиляции:";
    assertThat(doc).contains("Loads the value.", contextTitle).doesNotContain("**" + contextTitle);
    assertThat(doc.indexOf(contextTitle)).isGreaterThan(doc.indexOf("Loads the value."));
  }

  @ParameterizedTest
  @CsvSource({"RU, Проверить", "EN, Check"})
  void platformAliasesAndAvailabilityUseTheScriptLanguage(Language language, String name) {
    useScriptLanguage(language);
    var metadata = new PlatformMetadata("", "", List.of(), Set.of(Availability.THICK_CLIENT), null,
      BilingualString.EMPTY, BilingualString.EMPTY, List.of(), List.of());
    registry.registerMemberOverride(registry.resolve("Массив").orElseThrow(), () -> List.of(
      MemberDescriptor.method("Проверить").withBilingualName(new BilingualString("Проверить", "Check"))
        .withMetadata(metadata)), FileType.BSL);
    var target = item(form("""
      &AtClient
      Procedure Caller()
        Value = New Array;
        Value.|
      EndProcedure
      """, language), name);
    assertThat(target.getLabelDetails()).isNull();
    assertThat(target.getDetail()).isNull();
    assertThat(documentation(target)).contains(language == Language.EN ? "client" : "клиент");
  }

  @ParameterizedTest
  @CsvSource({"RU, КонтекстСвойство", "EN, ContextProperty"})
  void globalPropertiesUseTheirAliasAndPlatformAvailability(Language language, String name) {
    useScriptLanguage(language);
    var metadata = new PlatformMetadata("", "", List.of(), Set.of(Availability.SERVER), null,
      BilingualString.EMPTY, BilingualString.EMPTY, List.of(), List.of());
    var global = registry.intern(TypeKind.PLATFORM, "ГлобальныйКонтекст");
    registry.registerMemberOverride(global, () -> List.of(
      MemberDescriptor.property("КонтекстСвойство", TypeRef.UNKNOWN, "")
        .withBilingualName(new BilingualString("КонтекстСвойство", "ContextProperty")).withMetadata(metadata)), FileType.BSL);
    var target = item(form("&AtClient\nProcedure Caller()\n  |\nEndProcedure\n", language), name);

    assertThat(target.getSortText()).startsWith("2_");
    assertThat(target.getLabelDetails()).isNull();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void contextNotesFollowTheMethodDescriptionInBothDocumentationFormats(boolean markdown) {
    var client = capabilities.getCapabilities().orElseThrow();
    client.getTextDocument().getCompletion().getCompletionItem()
      .setDocumentationFormat(List.of(markdown ? MarkupKind.MARKDOWN : MarkupKind.PLAINTEXT));
    provider.handleInitializeEvent();
    var target = item(form("""
      // Описание прикладного метода.
      &AtServerNoContext
      Procedure Target()
      EndProcedure
      &AtClient
      Procedure Caller()
        |
      EndProcedure
      """, Language.RU), "Target");
    var value = documentation(target);

    assertThat(target.getDetail()).isNull();
    assertThat(target.getDocumentation().isRight()).isEqualTo(markdown);
    assertThat(value).startsWith("Описание прикладного метода.").contains("Контексты компиляции:")
      .doesNotContain("**Контексты компиляции");
    assertThat(value.indexOf("Контексты компиляции:")).isGreaterThan(value.indexOf("Описание прикладного метода."));
  }

  @ParameterizedTest
  @ValueSource(strings = {"bsl", "os"})
  void missingCompilationMetadataDoesNotInventHints(String extension) {
    initServerContext();
    context.getLanguageServerConfiguration().setLanguage(Language.EN);
    enableLabelDetails(true);
    var target = item(complete(URI.create("file:///context-hints." + extension), """
      Procedure Target()
      EndProcedure
      Procedure Caller()
        |
      EndProcedure
      """, Language.EN), "Target");
    assertThat(target.getLabelDetails().getDescription()).isNull();
    assertThat(target.getDetail()).isNull();
  }

  @Test
  void unknownPreprocessorConditionDoesNotInventAHint() {
    var target = item(form("""
      &AtServerNoContext
      Procedure Target()
      EndProcedure
      &AtClient
      Procedure Caller()
      #If Windows Then
        |
      #EndIf
      EndProcedure
      """, Language.RU), "Target");
    assertThat(target.getLabelDetails().getDescription()).isNull();
  }

  @Test
  void legacyClientKeepsItsSignatureAndReceivesTheContextSuffix() {
    useScriptLanguage(Language.EN);
    enableLabelDetails(false);
    var target = item(form("""
      &AtServerNoContext
      Procedure Target(Value)
      EndProcedure
      &AtClient
      Procedure Caller()
        |
      EndProcedure
      """, Language.EN), "Target");
    assertThat(target.getLabelDetails()).isNull();
    assertThat(target.getDetail()).isEqualTo("(Value) · →S");
  }

  @SneakyThrows
  private void useScriptLanguage(Language language) {
    if (context != null && context.getScriptVariantLanguage() == language) {
      return;
    }
    projectRoot = workspace.resolve(language.name());
    FileUtils.copyDirectory(ROOT.toFile(), projectRoot.toFile());
    var configurationFile = projectRoot.resolve("Configuration.xml");
    Files.writeString(configurationFile, Files.readString(configurationFile).replace(
      "<ScriptVariant>Russian</ScriptVariant>",
      "<ScriptVariant>" + (language == Language.EN ? "English" : "Russian") + "</ScriptVariant>"));
    initServerContext(projectRoot);
    context.getLanguageServerConfiguration().getCompletionOptions().setHideUnavailableByContext(false);
    enableLabelDetails(true);
  }

  private void enableLabelDetails(boolean enabled) {
    var item = new CompletionItemCapabilities();
    item.setLabelDetailsSupport(enabled);
    item.setSnippetSupport(true);
    item.setDocumentationFormat(List.of(MarkupKind.MARKDOWN));
    item.setResolveSupport(new CompletionItemResolveSupportCapabilities(List.of("documentation")));
    var completion = new CompletionCapabilities();
    completion.setCompletionItem(item);
    var text = new TextDocumentClientCapabilities();
    text.setCompletion(completion);
    var client = new ClientCapabilities();
    client.setTextDocument(text);
    capabilities.setCapabilities(client);
    provider.handleInitializeEvent();
  }

  private List<CompletionItem> form(String source, Language language) {
    return complete(projectRoot.resolve("CommonForms/Тест/Ext/Form/Module.bsl").toUri(), source, language);
  }

  private void module(String name, String source) {
    TestUtils.getDocumentContext(projectRoot.resolve("CommonModules/" + name + "/Ext/Module.bsl").toUri(), source, context);
  }

  private List<CompletionItem> complete(URI uri, String source, Language language) {
    var before = source.substring(0, source.indexOf('|'));
    var line = (int) before.chars().filter(value -> value == '\n').count();
    var character = before.length() - before.lastIndexOf('\n') - 1;
    var document = TestUtils.getDocumentContext(uri, source.replace("|", ""), context);
    assertThat(document.getScriptVariantLanguage()).isEqualTo(language);
    var params = new CompletionParams(new TextDocumentIdentifier(uri.toString()), new Position(line, character));
    return provider.getCompletion(document, params).getItems();
  }

  private static CompletionItem item(List<CompletionItem> items, String label) {
    return items.stream().filter(item -> item.getLabel().equals(label)).findFirst().orElseThrow();
  }

  private String documentation(CompletionItem item) {
    if (item.getData() != null) {
      provider.resolveCompletionItem(item, provider.extractData(item));
    }
    var documentation = item.getDocumentation();
    return documentation.isLeft() ? documentation.getLeft() : documentation.getRight().getValue();
  }
}
