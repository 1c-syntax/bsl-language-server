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
package com.github._1c_syntax.bsl.languageserver.hover;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentState;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Location;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MethodAvailabilityRendererTest extends AbstractServerContextAwareTest {
  private static final Path ROOT = Path.of("src/test/resources/metadata/commonModuleCalls");

  @Autowired
  private MethodAvailabilityRenderer renderer;

  @Autowired
  private CompilationContextResolver resolver;

  @Autowired
  private MethodSymbolMarkupContentBuilder builder;

  @Test
  void preprocessorAndMethodDirectiveNarrowCompilationContexts() {
    var method = form("""
      #Если ВебКлиент Тогда
      &НаКлиенте
      Процедура Цель()
      КонецПроцедуры
      #КонецЕсли
      """);

    assertThat(renderer.render(method)).isEqualTo("**Контексты компиляции:** веб-клиент");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void commonModuleFlagsRestrictTheDeclaredClientBranch(boolean fileMode) {
    var method = module("CommonModules/СерверныйМодуль/Ext/Module.bsl", """
      #Если Клиент Тогда
      Процедура Цель() Экспорт
      КонецПроцедуры
      #КонецЕсли
      """);

    context.getLanguageServerConfiguration().setSupportThickClientFileMode(fileMode);
    assertThat(resolver.declarationContexts(method).orElseThrow()).containsExactlyInAnyOrderElementsOf(fileMode
      ? Set.of(ExecutionContext.THICK_CLIENT_MANAGED_FILE, ExecutionContext.THICK_CLIENT_ORDINARY_FILE)
      : Set.of());
    assertThat(renderer.render(method).contains("файловый режим")).isEqualTo(fileMode);
    assertThat(renderer.render(method)).doesNotContain("тонкий клиент", "Серверный вызов");
  }

  @Test
  void sameNamedDeclarationsKeepTheirOwnPreprocessorBranches() {
    var method = form("""
      #Если ТонкийКлиент Тогда
      &НаКлиенте
      Процедура Цель()
      КонецПроцедуры
      #ИначеЕсли ВебКлиент Тогда
      &НаКлиенте
      Процедура Цель()
      КонецПроцедуры
      #КонецЕсли
      """);
    var methods = method.getOwner().getSymbolTree().getMethods();

    assertThat(methods).hasSize(2);
    assertThat(renderer.render(methods.getFirst())).isEqualTo("**Контексты компиляции:** тонкий клиент");
    assertThat(renderer.render(methods.getLast())).isEqualTo("**Контексты компиляции:** веб-клиент");
  }

  @Test
  void impossibleDeclarationIsDistinguishedFromUnknownContext() {
    var method = form("""
      #Если Сервер И НЕ Сервер Тогда
      &НаСервере
      Процедура Цель()
      КонецПроцедуры
      #КонецЕсли
      """);

    assertThat(renderer.render(method))
      .isEqualTo("**Контексты компиляции:** ни одного (объявление исключено из компиляции)");
  }

  @Test
  void unknownConditionDoesNotClaimAnUnconditionalAvailability() {
    var method = form("""
      #Если Windows Тогда
      &НаСервере
      Процедура Цель()
      КонецПроцедуры
      #КонецЕсли
      """);

    assertThat(renderer.render(method)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"bsl", "os"})
  void filesWithoutMetadataDoNotGainPlatformContexts(String extension) {
    initServerContext();
    var document = TestUtils.getDocumentContext(URI.create("file:///method-hover." + extension), """
      Процедура Цель()
      КонецПроцедуры
      """, context);

    assertThat(renderer.render(document.getSymbolTree().getMethodSymbol("Цель").orElseThrow())).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"НаСервере", "НаСервереБезКонтекста"})
  void formServerMethodsAdvertiseTheServerTransition(String directive) {
    var method = form("&" + directive + "\nПроцедура Цель()\nКонецПроцедуры\n");

    assertThat(renderer.render(method)).contains("Контексты компиляции", "сервер", "Серверный вызов");
    assertThat(resolver.serverCallContexts(method, resolver.declarationContexts(method).orElseThrow()))
      .contains(ExecutionContext.THIN_CLIENT, ExecutionContext.WEB_CLIENT)
      .doesNotContain(ExecutionContext.EXTERNAL_CONNECTION, ExecutionContext.THICK_CLIENT_MANAGED_FILE_CLIENT);
  }

  @ParameterizedTest
  @CsvSource({
    "СерверныйМодульВызовСервера, true, true",
    "СерверныйМодульВызовСервера, false, false",
    "СерверныйМодуль, true, false"
  })
  void commonModuleServerCallRequiresItsFlagAndAnExport(String moduleName, boolean exported, boolean expected) {
    var method = module("CommonModules/" + moduleName + "/Ext/Module.bsl",
      "Процедура Цель() " + (exported ? "Экспорт" : "") + "\nКонецПроцедуры\n");

    assertThat(renderer.render(method).contains("Серверный вызов")).isEqualTo(expected);
  }

  @Test
  void aServerCallFlagDoesNotOverrideTheDeclarationBranch() {
    var method = module("CommonModules/СерверныйМодульВызовСервера/Ext/Module.bsl", """
      #Если Клиент Тогда
      Процедура Цель() Экспорт
      КонецПроцедуры
      #КонецЕсли
      """);

    context.getLanguageServerConfiguration().setSupportThickClientFileMode(true);
    assertThat(renderer.render(method)).contains("файловый режим").doesNotContain("Серверный вызов");
  }

  @Test
  void aFileOnlyServerPartDoesNotAdvertiseAThinClientTransition() {
    var method = form("""
      #Если ТолстыйКлиентУправляемоеПриложение И Сервер Тогда
      &НаСервере
      Процедура Цель()
      КонецПроцедуры
      #КонецЕсли
      """);
    context.getLanguageServerConfiguration().setSupportThickClientFileMode(true);
    var contexts = resolver.declarationContexts(method).orElseThrow();

    assertThat(contexts).containsExactly(ExecutionContext.THICK_CLIENT_MANAGED_FILE_SERVER);
    assertThat(renderer.render(method)).contains("файловый режим, серверная часть").doesNotContain("тонкий клиент");
    assertThat(resolver.serverCallContexts(method, contexts))
      .containsExactly(ExecutionContext.THICK_CLIENT_MANAGED_FILE_CLIENT);
  }

  @Test
  void declarationConditionsRemainAvailableAfterAstIsUnloaded() {
    var method = form("""
      #Если ВебКлиент Тогда
      &НаКлиенте
      Процедура Цель()
      КонецПроцедуры
      #КонецЕсли
      """);
    var document = method.getOwner();
    var reference = Reference.of(document.getSymbolTree().getModule(), method,
      new Location(document.getUri().toString(), method.getSelectionRange()));
    var before = builder.getContent(reference).getValue();
    document.freezeComputedData();
    assertThat(context.tryClearDocument(document)).isTrue();

    assertThat(builder.getContent(reference).getValue()).isEqualTo(before).contains("Контексты компиляции:** веб-клиент");
    assertThat(context.getDocumentState(document)).isEqualTo(DocumentState.WITHOUT_CONTENT);
  }

  @Test
  void hoverDoesNotUseDiagnosticModeSelection() {
    var method = module("CommonModules/ОбщегоНазначения/Ext/Module.bsl", """
      #Если ТолстыйКлиентОбычноеПриложение Тогда
      Процедура Цель() Экспорт
      КонецПроцедуры
      #КонецЕсли
      """);

    assertThat(resolver.automaticContexts(method.getOwner())).doesNotContain(ExecutionContext.THICK_CLIENT_ORDINARY);
    assertThat(renderer.render(method)).contains("обычное приложение");
  }

  @Test
  void compilationContextsAreLocalizedInEnglish() {
    var method = form("""
      #Если ВебКлиент Тогда
      &НаКлиенте
      Процедура Цель()
      КонецПроцедуры
      #КонецЕсли
      """);
    context.getLanguageServerConfiguration().setLanguage(Language.EN);

    assertThat(renderer.render(method)).isEqualTo("**Compilation contexts:** web client");
  }

  @Test
  void serverTransitionsAndFileContextsAreLocalizedInEnglish() {
    var method = form("""
      &AtServerNoContext
      Procedure Цель()
      EndProcedure
      """);
    context.getLanguageServerConfiguration().setLanguage(Language.EN);
    context.getLanguageServerConfiguration().setSupportThickClientFileMode(true);

    assertThat(renderer.render(method))
      .contains("**Compilation contexts:** server", "file mode", "server part",
        "**Server call:** a transition to the server context is possible")
      .doesNotContain("Сервер", "сервер", "файловый");
  }

  private MethodSymbol form(String source) {
    return module("CommonForms/Тест/Ext/Form/Module.bsl", source);
  }

  private MethodSymbol module(String relative, String source) {
    initServerContext(ROOT);
    var document = TestUtils.getDocumentContext(ROOT.resolve(relative).toUri(), source, context);
    return document.getSymbolTree().getMethodSymbol("Цель").orElseThrow();
  }
}
