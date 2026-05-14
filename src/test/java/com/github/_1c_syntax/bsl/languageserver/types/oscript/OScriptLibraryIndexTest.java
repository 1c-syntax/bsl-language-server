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

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
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

  @Autowired
  private GlobalScopeProvider globalScopeProvider;

  @Test
  void indexesModulesAndClassesFromFixture() {
    var fixtureRoot = Path.of("src/test/resources/oscript-libraries/mylib").toAbsolutePath();
    initServerContext(fixtureRoot, false);

    index.reindex(context);

    // Module name registered as library namespace, resolves to user type
    assertThat(globalScopeProvider.getLibraryModules()).contains("MyModule");
    var moduleRef = globalScopeProvider.findLibraryModule("MyModule");
    assertThat(moduleRef).isPresent();
    assertThat(moduleRef.get().kind()).isEqualTo(TypeKind.USER);
    var moduleMembers = typeRegistry.getMembers(moduleRef.get());
    assertThat(moduleMembers).extracting(m -> m.name())
      .contains("ВывестиСообщение", "СформироватьСтроку", "СтатусМодуля");

    // Class name registered with constructor signature
    assertThat(globalScopeProvider.getLibraryClasses()).contains("MyClass");
    var ctor = globalScopeProvider.findLibraryClassConstructor("MyClass");
    assertThat(ctor).hasSize(1);
    assertThat(ctor.get(0).parameters()).extracting(p -> p.name()).containsExactly("Имя");
    var classRef = typeRegistry.resolve("MyClass");
    assertThat(classRef).isPresent();
    assertThat(ctor.get(0).returnType()).isEqualTo(classRef.get());

    var classMembers = typeRegistry.getMembers(classRef.get());
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

    assertThat(globalScopeProvider.getLibraryModules()).filteredOn(n -> n.equals("MyModule")).hasSize(1);
    assertThat(globalScopeProvider.getLibraryClasses()).filteredOn(n -> n.equals("MyClass")).hasSize(1);
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

    var moduleRef = globalScopeProvider.findLibraryModule("MyModule").orElseThrow();
    assertThat(typeRegistry.getMembers(moduleRef)).extracting(m -> m.name())
      .contains("ВывестиСообщение");

    var moduleUri = Absolute.uri(fixtureRoot.resolve("src/MyModule.os").toUri());
    var dc = context.getDocument(moduleUri);
    assertThat(dc).isNotNull();

    var newContent = ""
      + "Процедура НоваяПроцедура() Экспорт\n"
      + "КонецПроцедуры\n";
    context.rebuildDocument(dc, newContent, dc.getVersion() + 1);

    assertThat(typeRegistry.getMembers(moduleRef)).extracting(m -> m.name())
      .contains("НоваяПроцедура")
      .doesNotContain("ВывестиСообщение");
  }

  @Test
  void removesEntriesOnDocumentRemoved() {
    var fixtureRoot = Path.of("src/test/resources/oscript-libraries/mylib").toAbsolutePath();
    initServerContext(fixtureRoot, false);

    index.reindex(context);

    var moduleUri = Absolute.uri(fixtureRoot.resolve("src/MyModule.os").toUri());
    assertThat(globalScopeProvider.getLibraryModules()).contains("MyModule");

    context.removeDocument(moduleUri);

    assertThat(globalScopeProvider.findLibraryModule("MyModule")).isEmpty();
    assertThat(typeRegistry.resolve("MyModule")).isEmpty();
  }
}
