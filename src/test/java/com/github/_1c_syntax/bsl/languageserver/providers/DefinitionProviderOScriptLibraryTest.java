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

import com.github._1c_syntax.bsl.languageserver.lsp.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DefinitionCapabilities;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@CleanupContextBeforeClassAndAfterClass
class DefinitionProviderOScriptLibraryTest extends AbstractServerContextAwareTest {

  @Autowired
  private DefinitionProvider definitionProvider;

  @Autowired
  private OScriptLibraryIndex index;

  @MockitoSpyBean
  private ClientCapabilitiesHolder clientCapabilitiesHolder;

  @BeforeEach
  void enableLinkSupport() {
    // Тесты проверяют targetUri ссылок, поэтому ожидают ответ в виде LocationLink[]:
    // заявляем клиентскую поддержку textDocument.definition.linkSupport.
    var capabilities = new ClientCapabilities();
    var textDocumentCapabilities = new TextDocumentClientCapabilities();
    textDocumentCapabilities.setDefinition(new DefinitionCapabilities(false, true));
    capabilities.setTextDocument(textDocumentCapabilities);
    when(clientCapabilitiesHolder.getCapabilities()).thenReturn(Optional.of(capabilities));
    definitionProvider.handleInitializeEvent();
  }

  private static List<? extends LocationLink> locationLinks(
    Either<List<? extends Location>, List<? extends LocationLink>> definitions
  ) {
    assertThat(definitions.isRight()).isTrue();
    return definitions.getRight();
  }

  @Test
  void definitionOfLibraryClassInNewExpressionPointsToClassFile() {
    initLib();

    var content = "#Использовать mylib\nX = Новый MyClass(\"имя\");\n";
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    var params = new DefinitionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    int lineStart = content.indexOf('\n') + 1;
    int col = content.indexOf("MyClass") - lineStart;
    params.setPosition(new Position(1, col + 2));

    var definitions = locationLinks(definitionProvider.getDefinition(dc, params));

    assertThat(definitions)
      .as("go-to-definition на классе MyClass в выражении Новый должен вести в .os-файл")
      .isNotEmpty();
    assertThat(definitions.get(0).getTargetUri()).contains("MyClass.os");
  }

  @Test
  void definitionOfMethodOnLibraryClassInstancePointsToClassFile() {
    initLib();

    var content = "#Использовать mylib\nX = Новый MyClass(\"имя\");\nX.ПолучитьСтроку(\"п\");\n";
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    var params = new DefinitionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    int line2Start = content.indexOf("X.ПолучитьСтроку");
    // Курсор на ПолучитьСтроку (3-я строка, индекс 2)
    int col = "X.".length();
    params.setPosition(new Position(2, col + 2));

    var definitions = locationLinks(definitionProvider.getDefinition(dc, params));

    assertThat(definitions)
      .as("go-to-definition на методе экземпляра library-класса должен вести в .os-файл")
      .isNotEmpty();
    assertThat(definitions.get(0).getTargetUri()).contains("MyClass.os");
  }

  @Test
  void definitionOfLibraryModuleMethodPointsToLibraryFile() {
    initLib();

    // #Использовать mylib подключает библиотеку; MyModule.ВывестиСообщение должен
    // резолвиться в .os-файл библиотеки.
    var content = "#Использовать mylib\nMyModule.ВывестиСообщение(\"Текст\");\n";
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    var params = new DefinitionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    // Курсор внутри имени метода ВывестиСообщение
    var methodCol = content.indexOf("ВывестиСообщение");
    // Содержимое начинается со строки 1 (вторая); приведём в координаты
    int lineBreak = content.indexOf('\n');
    int colInLine = methodCol - (lineBreak + 1);
    params.setPosition(new Position(1, colInLine + 2));

    var definitions = locationLinks(definitionProvider.getDefinition(dc, params));

    assertThat(definitions)
      .as("go-to-definition на методе MyModule.ВывестиСообщение должен вести в файл библиотеки")
      .isNotEmpty();
    assertThat(definitions.get(0).getTargetUri()).contains("MyModule.os");
  }

  @Test
  void definitionOfLibraryModuleNameItselfPointsToLibraryFile() {
    initLib();

    // Курсор на самом identifier модуля (внутри обычного вызова Модуль.Метод()).
    var content = "#Использовать mylib\nMyModule.ВывестиСообщение(\"Текст\");\n";
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    var params = new DefinitionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(1, 2));

    var definitions = locationLinks(definitionProvider.getDefinition(dc, params));

    assertThat(definitions)
      .as("go-to-definition на имени модуля библиотеки должен вести в файл модуля")
      .isNotEmpty();
    assertThat(definitions.get(0).getTargetUri()).contains("MyModule.os");
  }

  @Test
  void definitionOfLibraryModuleMethodWhenSameFileExportsClass() {
    var fixtureRoot = Path.of("src/test/resources/oscript-libraries/dualmod").toAbsolutePath();
    initServerContext(fixtureRoot, false);
    index.reindex(context);

    var content = "#Использовать dualmod\nВременныеФайлы.НовоеИмяФайла();\n";
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    var params = new DefinitionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    int colMethod = content.indexOf("НовоеИмяФайла") - content.indexOf('\n') - 1;
    params.setPosition(new Position(1, colMethod + 2));

    var definitions = locationLinks(definitionProvider.getDefinition(dc, params));

    assertThat(definitions)
      .as("go-to-def на методе модуля (когда тот же файл регистрирует и класс) должен вести в .os-файл")
      .isNotEmpty();
    // Сравниваем по ASCII-форме: Path.toUri()/Absolute.uri на Windows может
    // вернуть URI с литеральной кириллицей вместо percent-encoded (JDK quirk),
    // что давало OS-зависимый зелёный/красный тест.
    assertThat(URI.create(definitions.get(0).getTargetUri()).toASCIIString())
      .contains("%D0%92%D1%80%D0%B5%D0%BC%D0%B5%D0%BD%D0%BD%D1%8B%D0%B5%D0%A4%D0%B0%D0%B9%D0%BB%D1%8B.os");
  }

  @Test
  void definitionOfLibraryModuleNameWhenSameFileExportsClass() {
    var fixtureRoot = Path.of("src/test/resources/oscript-libraries/dualmod").toAbsolutePath();
    initServerContext(fixtureRoot, false);
    index.reindex(context);

    // Курсор на самом identifier модуля (внутри вызова Модуль.Метод()).
    var content = "#Использовать dualmod\nВременныеФайлы.НовоеИмяФайла();\n";
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    var params = new DefinitionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(1, 2));

    var definitions = locationLinks(definitionProvider.getDefinition(dc, params));

    assertThat(definitions)
      .as("go-to-def на имени модуля библиотеки (модуль+класс с одного файла) должен вести в .os-файл")
      .isNotEmpty();
    // Сравниваем по ASCII-форме: Path.toUri()/Absolute.uri на Windows может
    // вернуть URI с литеральной кириллицей вместо percent-encoded (JDK quirk),
    // что давало OS-зависимый зелёный/красный тест.
    assertThat(URI.create(definitions.get(0).getTargetUri()).toASCIIString())
      .contains("%D0%92%D1%80%D0%B5%D0%BC%D0%B5%D0%BD%D0%BD%D1%8B%D0%B5%D0%A4%D0%B0%D0%B9%D0%BB%D1%8B.os");
  }

  private void initLib() {
    var fixtureRoot = Path.of("src/test/resources/oscript-libraries/mylib").toAbsolutePath();
    initServerContext(fixtureRoot, false);
    index.reindex(context);
  }
}
