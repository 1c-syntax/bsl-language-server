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
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class CompletionProviderOScriptLibraryTest extends AbstractServerContextAwareTest {

  @Autowired
  private CompletionProvider completionProvider;

  @Autowired
  private OScriptLibraryIndex index;

  @Autowired
  private com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry typeRegistry;

  @Autowired
  private com.github._1c_syntax.bsl.languageserver.types.TypeService typeService;

  @Test
  void noDotCompletionSurfacesLibraryModuleAsModule() {
    initLib();

    var content = "#Использовать mylib\nMyMod";
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(1, "MyMod".length()));

    var items = completionProvider.getCompletion(dc, params).getItems();

    assertThat(items)
      .filteredOn(it -> "MyModule".equals(it.getLabel()))
      .singleElement()
      .extracting(CompletionItem::getKind)
      .isEqualTo(CompletionItemKind.Module);
  }

  @Test
  void noDotCompletionAfterNovyiSurfacesLibraryClassAsClass() {
    initLib();

    var content = "#Использовать mylib\nА = Новый MyCl";
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(1, "А = Новый MyCl".length()));

    var items = completionProvider.getCompletion(dc, params).getItems();

    assertThat(items)
      .filteredOn(it -> "MyClass".equals(it.getLabel()))
      .singleElement()
      .extracting(CompletionItem::getKind)
      .isEqualTo(CompletionItemKind.Class);
  }

  @Test
  void dotCompletionAfterLibraryModuleReturnsItsMembers() {
    initLib();

    var content = "MyModule.";
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(0, content.length()));

    var items = completionProvider.getCompletion(dc, params).getItems();

    assertThat(items)
      .extracting(CompletionItem::getLabel)
      .contains("ВывестиСообщение", "СформироватьСтроку", "СтатусМодуля");
  }

  @Test
  void noDotCompletionWithoutUseDirectivesHidesLibraryEntries() {
    initLib();

    var content = "MyMod";
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(0, content.length()));

    var items = completionProvider.getCompletion(dc, params).getItems();

    assertThat(items)
      .as("без #Использовать library-сущности должны быть скрыты")
      .filteredOn(it -> "MyModule".equals(it.getLabel()))
      .isEmpty();
  }

  @Test
  void noDotCompletionGatedByUseDirectivesShowsOnlyDeclaredLibs() {
    initLib();

    // Документ с #Использовать на другую (несуществующую) либу — наш MyModule
    // не должен попасть в список.
    var content = "#Использовать someother\nMyMod";
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(1, "MyMod".length()));

    var items = completionProvider.getCompletion(dc, params).getItems();

    assertThat(items)
      .filteredOn(it -> "MyModule".equals(it.getLabel()))
      .isEmpty();
  }

  @Test
  void noDotCompletionGatedByUseDirectivesShowsLibAfterImport() {
    initLib();

    var content = "#Использовать mylib\nMyMod";
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(1, "MyMod".length()));

    var items = completionProvider.getCompletion(dc, params).getItems();

    assertThat(items)
      .filteredOn(it -> "MyModule".equals(it.getLabel()))
      .singleElement()
      .extracting(CompletionItem::getKind)
      .isEqualTo(CompletionItemKind.Module);
  }

  @Test
  void dotCompletionOnInstanceOfLibraryClassReturnsItsMembers() {
    initLib();

    var content = "#Использовать mylib\nX = Новый MyClass;\nX.ПолучитьСтроку();\n";
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(2, 2));

    var items = completionProvider.getCompletion(dc, params).getItems();

    assertThat(items)
      .as("после `X = Новый MyClass; X.` должны быть видны члены MyClass")
      .extracting(CompletionItem::getLabel)
      .contains("ПолучитьСтроку", "СтатусМодуля");
  }

  @Test
  void dotCompletionOnVariableNamedAsLibraryClassReturnsItsMembers() {
    // Воспроизведение: УправлениеКонфигуратором = Новый УправлениеКонфигуратором();
    // УправлениеКонфигуратором.<TAB> — автокомплит не работает, когда имя переменной
    // совпадает с именем библиотечного класса.
    initLib();

    var content = "#Использовать mylib\nMyClass = Новый MyClass();\nMyClass.\n";
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(2, "MyClass.".length()));

    var items = completionProvider.getCompletion(dc, params).getItems();

    assertThat(items)
      .as("после `MyClass = Новый MyClass(); MyClass.` должны быть видны члены MyClass")
      .extracting(CompletionItem::getLabel)
      .contains("ПолучитьСтроку", "СтатусМодуля");
  }

  @Test
  void dotCompletionOnDanglingDotInsideProcedureReturnsClassMembers() {
    // Тот же сценарий висячей точки, что и dotCompletionOnVariableNamedAsLibraryClassReturnsItsMembers,
    // но переменная объявлена внутри Процедура ... КонецПроцедуры. Цель — проверить,
    // починила бы обёртка в процедуру баг dangling-dot (т.е. имеет ли значение
    // top-level vs procedure-body для error-recovery парсера).
    initLib();

    var content = ""
      + "#Использовать mylib\n"
      + "Процедура Тест()\n"
      + "  MyClass = Новый MyClass();\n"
      + "  MyClass.\n"
      + "КонецПроцедуры\n";
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(3, "  MyClass.".length()));

    var items = completionProvider.getCompletion(dc, params).getItems();

    assertThat(items)
      .as("после `MyClass = Новый MyClass(); MyClass.` внутри Процедуры — должны быть видны члены MyClass")
      .extracting(CompletionItem::getLabel)
      .contains("ПолучитьСтроку", "СтатусМодуля");
  }

  @Test
  void dotCompletionOnInstanceOfLibraryClassWithRenamedFile() {
    // Воспроизведение проблемы из winow/v8runner:
    // в lib.config имя класса (RenamedClass / УправлениеКонфигуратором)
    // не совпадает с basename исходного файла (mainclass.os / v8runner.os),
    // что ломает резолв членов после `Перем = Новый <Класс>(); Перем.`.
    initLib();

    var content = "#Использовать mylib\nЭкземпляр = Новый RenamedClass();\nЭкземпляр.ПолучитьСтроку();\n";
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(2, "Экземпляр.".length()));

    var items = completionProvider.getCompletion(dc, params).getItems();

    assertThat(items)
      .as("после `Экземпляр = Новый RenamedClass(); Экземпляр.` должны быть видны члены класса (файл mainclass.os)")
      .extracting(CompletionItem::getLabel)
      .contains("ПолучитьСтроку", "СтатусМодуля");
  }

  @Test
  void noDotCompletionAfterNovyiSurfacesSamePackageClassWithoutUseDirective() {
    // given
    initLib();
    // редактируется файл, который сам принадлежит библиотеке mylib; #Использовать нет
    var moduleUri = Path.of("src/test/resources/oscript-libraries/mylib/src/MyModule.os")
      .toAbsolutePath().toUri();
    var content = "А = Новый MyCl";
    var dc = TestUtils.getDocumentContext(moduleUri, content, context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(0, content.length()));

    // when
    var items = completionProvider.getCompletion(dc, params).getItems();

    // then
    assertThat(items)
      .as("класс-сосед по тому же корню библиотеки виден после `Новый` без #Использовать")
      .filteredOn(it -> "MyClass".equals(it.getLabel()))
      .singleElement()
      .extracting(CompletionItem::getKind)
      .isEqualTo(CompletionItemKind.Class);
  }

  @Test
  void noDotCompletionSurfacesSamePackageModuleWithoutUseDirective() {
    // given
    initLib();
    var classUri = Path.of("src/test/resources/oscript-libraries/mylib/src/MyClass.os")
      .toAbsolutePath().toUri();
    var content = "MyMod";
    var dc = TestUtils.getDocumentContext(classUri, content, context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(0, content.length()));

    // when
    var items = completionProvider.getCompletion(dc, params).getItems();

    // then
    assertThat(items)
      .as("модуль-сосед по тому же корню библиотеки виден без #Использовать")
      .filteredOn(it -> "MyModule".equals(it.getLabel()))
      .singleElement()
      .extracting(CompletionItem::getKind)
      .isEqualTo(CompletionItemKind.Module);
  }

  @Test
  void noDotCompletionAfterNovyiSurfacesImplicitSamePackageClassWithoutUseDirective() {
    // given
    var fixtureRoot = Path.of("src/test/resources/oscript-libraries/implicit-test").toAbsolutePath();
    initServerContext(fixtureRoot, false);
    index.reindex(context);
    // редактируется публичный класс библиотеки; AutoFound — implicit-сосед,
    // флаг показа implicit выключен (default) — но из своего пакета он виден
    var publicUri = fixtureRoot.resolve("src/PublicHello.os").toUri();
    var content = "А = Новый AutoF";
    var dc = TestUtils.getDocumentContext(publicUri, content, context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(0, content.length()));

    // when
    var items = completionProvider.getCompletion(dc, params).getItems();

    // then
    assertThat(items)
      .as("implicit-класс-сосед виден после `Новый` из своего пакета даже при выключенном флаге")
      .extracting(CompletionItem::getLabel)
      .contains("AutoFound");
  }

  private void initLib() {
    var fixtureRoot = Path.of("src/test/resources/oscript-libraries/mylib").toAbsolutePath();
    initServerContext(fixtureRoot, false);
    index.reindex(context);
  }
}
