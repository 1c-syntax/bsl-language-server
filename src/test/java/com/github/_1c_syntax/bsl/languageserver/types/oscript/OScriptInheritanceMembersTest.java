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
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class OScriptInheritanceMembersTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeRegistry typeRegistry;

  @Autowired
  private OScriptLibraryIndex index;

  @Test
  void childClassInheritsExportedMembersTransitively() {
    var members = membersOf("ДочернийКласс");

    // Собственный + унаследованные (через 2 уровня) экспортные члены.
    assertThat(members)
      .extracting(MemberDescriptor::name)
      .contains("ДочернийМетод", "ПромежуточныйМетод", "БазовыйМетод", "БазовоеСвойство");
  }

  @Test
  void intermediateClassInheritsOnlyBaseMembers() {
    var members = membersOf("ПромежуточныйКласс");

    assertThat(members)
      .extracting(MemberDescriptor::name)
      .contains("ПромежуточныйМетод", "БазовыйМетод", "БазовоеСвойство")
      .doesNotContain("ДочернийМетод");
  }

  @Test
  void nonExportedParentMembersAreNotInherited() {
    var members = membersOf("ДочернийКласс");

    assertThat(members)
      .extracting(MemberDescriptor::name)
      .doesNotContain("ВнутреннийМетодБазы");
  }

  @Test
  void baseClassExposesOwnMembersAndNoInheritedOnes() {
    var members = membersOf("БазовыйКласс");

    assertThat(members)
      .extracting(MemberDescriptor::name)
      .contains("БазовыйМетод", "БазовоеСвойство")
      .doesNotContain("ПромежуточныйМетод", "ДочернийМетод");
  }

  @Test
  void overriddenMemberWinsOverInherited() {
    var members = membersOf("ДочернийКласс");

    var baseMethod = members.stream()
      .filter(member -> member.name().equals("БазовыйМетод"))
      .toList();

    // Дедупликация по имени: метод представлен один раз...
    assertThat(baseMethod).hasSize(1);
    // ...и это собственное переопределение потомка, а не унаследованная версия.
    assertThat(baseMethod.get(0).description()).contains("Переопределение");
  }

  private Collection<MemberDescriptor> membersOf(String className) {
    var fixtureRoot = Path.of("src/test/resources/oscript-libraries/extends-lib").toAbsolutePath();
    initServerContext(fixtureRoot, false);
    index.reindex(context);

    TypeRef ref = typeRegistry.resolve(className, FileType.OS).orElseThrow();
    return typeRegistry.getMembers(ref, FileType.OS);
  }
}
