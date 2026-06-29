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
package com.github._1c_syntax.bsl.languageserver.types.oscript;

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.EntryKind;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.LibraryEntry;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class OScriptLibraryIndexTest extends AbstractServerContextAwareTest {

  @Autowired
  private OScriptLibraryIndex index;

  @Autowired
  private TypeRegistry typeRegistry;

  private static java.util.List<String> entryNames(OScriptLibraryIndex index, EntryKind kind) {
    return index.findEntries(kind).stream().map(LibraryEntry::qualifiedName).toList();
  }

  @Test
  void indexesModulesAndClassesFromFixture() {
    var fixtureRoot = Path.of("src/test/resources/oscript-libraries/mylib").toAbsolutePath();
    initServerContext(fixtureRoot, false);

    index.reindex(context);

    // Module name registered as library entry, resolves to user type
    assertThat(entryNames(index, EntryKind.MODULE)).contains("MyModule");
    var moduleRef = typeRegistry.resolve("MyModule");
    assertThat(moduleRef).isPresent();
    assertThat(moduleRef.get().kind()).isEqualTo(TypeKind.USER);
    var moduleMembers = typeRegistry.getMembers(moduleRef.get(), FileType.OS);
    assertThat(moduleMembers).extracting(m -> m.name())
      .contains("ВывестиСообщение", "СформироватьСтроку", "СтатусМодуля");

    // Class name registered with constructor signature (через TypeRegistry)
    assertThat(entryNames(index, EntryKind.CLASS)).contains("MyClass");
    var classRef = typeRegistry.resolve("MyClass");
    assertThat(classRef).isPresent();
    var ctor = typeRegistry.getConstructors(classRef.get(), FileType.OS);
    assertThat(ctor).hasSize(1);
    assertThat(ctor.get(0).parameters()).extracting(p -> p.name()).containsExactly("Имя");
    assertThat(ctor.get(0).returnType()).isEqualTo(classRef.get());

    var classMembers = typeRegistry.getMembers(classRef.get(), FileType.OS);
    assertThat(classMembers).extracting(m -> m.name()).contains("ПолучитьСтроку", "СтатусМодуля");
    assertThat(classMembers).filteredOn(m -> m.name().equals("ПолучитьСтроку"))
      .first().extracting(m -> m.kind()).isEqualTo(MemberKind.METHOD);
  }

  @Test
  void reindexIsIdempotent() {
    var fixtureRoot = Path.of("src/test/resources/oscript-libraries/mylib").toAbsolutePath();
    initServerContext(fixtureRoot, false);

    index.reindex(context);
    index.reindex(context);

    assertThat(entryNames(index, EntryKind.MODULE)).filteredOn(n -> n.equals("MyModule")).hasSize(1);
    assertThat(entryNames(index, EntryKind.CLASS)).filteredOn(n -> n.equals("MyClass")).hasSize(1);
  }

  @Test
  void registersOScriptModuleTypeForLibraryFiles() {
    var fixtureRoot = Path.of("src/test/resources/oscript-libraries/mylib").toAbsolutePath();
    initServerContext(fixtureRoot, false);

    index.reindex(context);

    var moduleUri = Absolute.uri(fixtureRoot.resolve("src/MyModule.os").toUri());
    var classUri = Absolute.uri(fixtureRoot.resolve("src/MyClass.os").toUri());

    assertThat(context.getDocument(moduleUri)).isNotNull();
    assertThat(context.getDocument(moduleUri).getModuleType()).isEqualTo(ModuleType.OScriptModule);
    assertThat(context.getDocument(classUri)).isNotNull();
    assertThat(context.getDocument(classUri).getModuleType()).isEqualTo(ModuleType.OScriptClass);
  }

  @Test
  void refreshesMembersOnDocumentContentChange() {
    var fixtureRoot = Path.of("src/test/resources/oscript-libraries/mylib").toAbsolutePath();
    initServerContext(fixtureRoot, false);

    index.reindex(context);

    var moduleRef = typeRegistry.resolve("MyModule").orElseThrow();
    assertThat(typeRegistry.getMembers(moduleRef, FileType.OS)).extracting(m -> m.name())
      .contains("ВывестиСообщение");

    var moduleUri = Absolute.uri(fixtureRoot.resolve("src/MyModule.os").toUri());
    var dc = context.getDocument(moduleUri);
    assertThat(dc).isNotNull();

    var newContent = ""
      + "Процедура НоваяПроцедура() Экспорт\n"
      + "КонецПроцедуры\n";
    context.rebuildDocument(dc, newContent, dc.getVersion() + 1);

    assertThat(typeRegistry.getMembers(moduleRef, FileType.OS)).extracting(m -> m.name())
      .contains("НоваяПроцедура")
      .doesNotContain("ВывестиСообщение");
  }

  @Test
  void removesEntriesOnDocumentRemoved() {
    var fixtureRoot = Path.of("src/test/resources/oscript-libraries/mylib").toAbsolutePath();
    initServerContext(fixtureRoot, false);

    index.reindex(context);

    var moduleUri = Absolute.uri(fixtureRoot.resolve("src/MyModule.os").toUri());
    assertThat(entryNames(index, EntryKind.MODULE)).contains("MyModule");

    context.removeDocument(moduleUri);

    assertThat(index.findByName("MyModule")).isEmpty();
    assertThat(typeRegistry.resolve("MyModule")).isEmpty();
  }

  @Test
  void findByNameEmptyForBlankInput() {
    // when / then — без reindex'а
    assertThat(index.findByName(null)).isEmpty();
    assertThat(index.findByName("")).isEmpty();
    assertThat(index.findByName("   ")).isEmpty();
  }

  @Test
  void findClassAndModuleUriDistinguishedByKind() {
    // given
    var fixtureRoot = Path.of("src/test/resources/oscript-libraries/mylib").toAbsolutePath();
    initServerContext(fixtureRoot, false);
    index.reindex(context);

    // when / then
    assertThat(index.findClassUri("MyClass")).isPresent();
    assertThat(index.findModuleUri("MyClass"))
      .as("findModuleUri не должен находить класс")
      .isEmpty();

    assertThat(index.findModuleUri("MyModule")).isPresent();
    assertThat(index.findClassUri("MyModule"))
      .as("findClassUri не должен находить модуль")
      .isEmpty();
  }

  @Test
  void findUriResolvesByQualifiedName() {
    // given
    var fixtureRoot = Path.of("src/test/resources/oscript-libraries/mylib").toAbsolutePath();
    initServerContext(fixtureRoot, false);
    index.reindex(context);

    // when / then
    assertThat(index.findUri("MyModule")).isPresent();
    assertThat(index.findUri("ОченьСомнительныйИмяКлассаXYZ")).isEmpty();
  }

  @Test
  void allEntriesReturnsClassesAndModulesTogether() {
    // given
    var fixtureRoot = Path.of("src/test/resources/oscript-libraries/mylib").toAbsolutePath();
    initServerContext(fixtureRoot, false);
    index.reindex(context);

    // when
    var all = index.allEntries();

    // then
    assertThat(all).extracting(LibraryEntry::qualifiedName)
      .contains("MyModule", "MyClass");
  }

  @Test
  void classNamesUsesQualifiedNameForLibraryClass() {
    // given — у класса RenamedClass qualifiedName из lib.config отличается от basename файла.
    var fixtureRoot = Path.of("src/test/resources/oscript-libraries/mylib").toAbsolutePath();
    initServerContext(fixtureRoot, false);
    index.reindex(context);
    var renamed = context.getDocument(Absolute.uri(fixtureRoot.resolve("src/mainclass.os").toUri()));

    // when / then — берётся qualifiedName, а не basename.
    assertThat(index.classNames(renamed)).containsExactly("RenamedClass");
    assertThat(index.isLibraryClass(renamed)).isTrue();
  }

  @Test
  void classNamesAreEmptyForNonLibraryFile() {
    // given — обычный .os, не зарегистрированный в lib.config.
    initServerContext();
    var plain = TestUtils.getDocumentContext(
      TestUtils.FAKE_OSCRIPT_DOCUMENT_URI,
      "Процедура ПриСозданииОбъекта()\nКонецПроцедуры\n", context);

    // when / then — имён нет, library-классом не считается.
    assertThat(index.classNames(plain)).isEmpty();
    assertThat(index.isLibraryClass(plain)).isFalse();
  }

  @Test
  void findEntriesByUriReturnsRolesForLibraryFile() {
    // given
    var fixtureRoot = Path.of("src/test/resources/oscript-libraries/mylib").toAbsolutePath();
    initServerContext(fixtureRoot, false);
    index.reindex(context);
    var moduleUri = Absolute.uri(fixtureRoot.resolve("src/MyModule.os").toUri());

    // when
    var entries = index.findEntriesByUri(moduleUri);

    // then
    assertThat(entries).isNotEmpty();
    assertThat(entries).extracting(LibraryEntry::qualifiedName).contains("MyModule");
  }

  @Test
  void findByUriUnknownReturnsEmpty() {
    // given — uri, который не зарегистрирован.
    var unknownUri = java.net.URI.create("file:///fake/unknown.os");

    // when / then
    assertThat(index.findByUri(unknownUri)).isEmpty();
  }

  @Test
  void findEntriesByUriUnknownReturnsEmptyList() {
    // given
    var unknownUri = java.net.URI.create("file:///fake/unknown.os");

    // when / then
    assertThat(index.findEntriesByUri(unknownUri)).isEmpty();
  }

  @Test
  void handleWorkspaceAddedForUnknownWorkspaceIsNoop() {
    // given — событие о workspace, которого нет у провайдера (контекст не зарезолвится по URI).
    var event = new com.github._1c_syntax.bsl.languageserver.events.WorkspaceAddedEvent(
      this, java.net.URI.create("file:///fake/workspace"));

    // when — handleWorkspaceAdded не находит ServerContext по URI и должен корректно отработать.
    index.handleWorkspaceAdded(event);

    // then — индекс не сломался.
    assertThat(index.allEntries()).isNotNull();
  }
}
