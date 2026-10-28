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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.DocumentState;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.UnreachableCodeDiagnostic;
import com.github._1c_syntax.bsl.languageserver.diagnostics.infrastructure.DiagnosticObjectProvider;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.types.index.SymbolTypeIndex;
import com.github._1c_syntax.bsl.languageserver.inlayhints.VariableTypeInlayHintSupplier;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
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

import static org.assertj.core.api.Assertions.assertThat;

class ThickClientFileModeTest extends AbstractServerContextAwareTest {
  private static final Path ROOT = Path.of("src/test/resources/metadata/commonModuleCalls");
  private static final String FORM = """
    &НаКлиентеНаСервереБезКонтекста
    Функция Метод_()
      #Если Сервер Тогда
        Возврат 1;
      #Иначе
        Возврат "";
      #КонецЕсли
    КонецФункции
    &НаКлиенте
    Процедура Клиентская()
      Б = Метод_();
      В = КлиентСерверныйМодуль.Метод_();
    КонецПроцедуры
    """;

  @Autowired
  private TypeService types;
  @Autowired
  private CompilationContextResolver resolver;
  @Autowired
  private DiagnosticObjectProvider diagnostics;
  @Autowired
  private VariableTypeInlayHintSupplier hints;
  @Autowired
  private SymbolTypeIndex symbolTypes;
  @TempDir
  Path temp;

  @BeforeEach
  void workspace() {
    initServerContext(ROOT);
  }

  @Test
  void disabledByDefaultAndKeepsOtherDeploymentModes() {
    assertThat(new LanguageServerConfiguration().isSupportThickClientFileMode()).isFalse();
    var document = form(FORM);
    assertThat(resolver.moduleContexts(document)).noneMatch(ExecutionContext::isThickClientFileMode)
      .contains(ExecutionContext.THICK_CLIENT_MANAGED, ExecutionContext.SERVER);
    assertThat(resolver.supportedContexts(document)).contains(ExecutionContext.EXTERNAL_CONNECTION_FILE);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void localAndCommonModuleCallsFollowTheSameProjectMode(boolean enabled) {
    context.getLanguageServerConfiguration().setSupportThickClientFileMode(enabled);
    TestUtils.getDocumentContext(ROOT.resolve("CommonModules/КлиентСерверныйМодуль/Ext/Module.bsl").toUri(), """
      Функция Метод_() Экспорт
        #Если Сервер Тогда
          Возврат 1;
        #Иначе
          Возврат "";
        #КонецЕсли
      КонецФункции
      """, context);
    var document = form(FORM);
    var expected = enabled ? new String[]{"Строка", "Число"} : new String[]{"Строка"};

    assertThat(at(document, "Метод_();")).containsExactlyInAnyOrder(expected);
    assertThat(at(document, "КлиентСерверныйМодуль.Метод_();")).containsExactlyInAnyOrder(expected);
    var inlayHints = hints.getInlayHints(document, new InlayHintParams(
      new TextDocumentIdentifier(document.getUri().toString()), new Range(new Position(0, 0), new Position(13, 0))));
    // Обычные объединения типов не показываются коротким инлей-хинтом.
    assertThat(inlayHints).hasSize(enabled ? 0 : 2);
    assertThat(inlayHints).allSatisfy(hint ->
      assertThat(hint.getLabel().getRight().getFirst().getValue()).isEqualTo(": Строка"));
  }


  @Test
  void configurationReloadInvalidatesTypesWithoutEditingTheSource() throws IOException {
    var document = form(FORM);
    var tree = document.getSymbolTree();
    assertThat(at(document, "Метод_();")).containsExactly("Строка");
    var file = temp.resolve(".bsl-language-server.json");
    Files.writeString(file, "{\"supportThickClientFileMode\":true}");

    context.getLanguageServerConfiguration().update(file.toFile());

    assertThat(context.getLanguageServerConfiguration().isSupportThickClientFileMode()).isTrue();
    assertThat(document.getSymbolTree()).isSameAs(tree);
    assertThat(at(document, "Метод_();")).containsExactlyInAnyOrder("Строка", "Число");
    Files.writeString(file, "{\"supportThickClientFileMode\":false}");
    context.getLanguageServerConfiguration().update(file.toFile());
    assertThat(at(document, "Метод_();")).containsExactly("Строка");
  }


  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void oneScriptSymbolsRemainIndependentOfPlatformModes(boolean enabled) {
    context.getLanguageServerConfiguration().setSupportThickClientFileMode(enabled);
    var document = TestUtils.getDocumentContext(URI.create("file:///independent-symbols.os"), """
      #If Client And Server Then
        A = 1;
      #EndIf
      """, context);

    assertThat(diagnostics.get(UnreachableCodeDiagnostic.class).getDiagnostics(document)).isEmpty();
  }

  @Test
  void configurationReloadReindexesReleasedCallees() throws IOException {
    var project = temp.resolve("project");
    FileUtils.copyDirectory(ROOT.toFile(), project.toFile());
    var module = project.resolve("CommonModules/КлиентСерверныйМодуль/Ext/Module.bsl");
    Files.writeString(module, """
      Function Value() Export
        #If Client And Server Then
          Return 1;
        #Else
          Return "";
        #EndIf
      EndFunction
      """);
    initServerContext(project);
    var document = context.getDocumentUnsafe(module.toUri());
    assertThat(context.getDocumentState(document)).isEqualTo(DocumentState.WITHOUT_CONTENT);
    assertThat(indexedTypes(document)).containsExactly("Строка");
    var file = temp.resolve("reload.json");
    Files.writeString(file, "{\"supportThickClientFileMode\":true}");

    context.getLanguageServerConfiguration().update(file.toFile());

    assertThat(indexedTypes(document)).containsExactlyInAnyOrder("Строка", "Число");
    assertThat(context.getDocumentState(document)).isEqualTo(DocumentState.WITHOUT_CONTENT);
    Files.writeString(file, "{\"supportThickClientFileMode\":false}");
    context.getLanguageServerConfiguration().update(file.toFile());
    assertThat(indexedTypes(document)).containsExactly("Строка");
  }

  private List<String> indexedTypes(DocumentContext document) {
    return symbolTypes.getReturnTypes(document.getSymbolTreeNoLock().getMethodSymbol("Value").orElseThrow())
      .refs().stream().map(TypeRef::qualifiedName).toList();
  }

  private DocumentContext form(String source) {
    return TestUtils.getDocumentContext(ROOT.resolve("CommonForms/Тест/Ext/Form/Module.bsl").toUri(), source, context);
  }

  private List<String> at(DocumentContext document, String marker) {
    var source = document.getContent();
    var index = source.indexOf(marker);
    assertThat(index).isNotNegative();
    var position = new Position(source.substring(0, index).split("\n").length - 1,
      index - source.lastIndexOf('\n', index) - 1);
    return types.expressionTypesAt(document, position).refs().stream().map(TypeRef::qualifiedName).toList();
  }
}
