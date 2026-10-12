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
package com.github._1c_syntax.bsl.languageserver.types.registry;

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceBeanScope;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.util.TestApplicationContext;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Memo членов {@link TypeRegistry#GLOBAL_CONTEXT} живёт своей эпохой: регистрация членов
 * других типов его не сбрасывает, а всё, из чего собраны глобальные члены, — сбрасывает.
 */
@SpringBootTest
class GlobalContextMembersCacheTest {

  @Autowired
  private WorkspaceBeanScope workspaceScope;

  @TempDir
  private Path workspaceDir;

  private URI workspaceUri;
  private AutoCloseable workspace;
  private TypeRegistry typeRegistry;

  @BeforeEach
  void setUp() {
    workspaceUri = Absolute.uri(workspaceDir.toUri());
    workspace = WorkspaceContextHolder.forUri(workspaceUri, "global-context-members-cache");
    typeRegistry = TestApplicationContext.getBean(TypeRegistry.class);
    typeRegistry.ensureInitialized();
  }

  @AfterEach
  void tearDown() throws Exception {
    workspace.close();
    WorkspaceContextHolder.clear();
    workspaceScope.removeWorkspace(workspaceUri);
  }

  @Test
  void membersOfOtherTypeDoNotResetGlobalContextMembers() {
    // given
    var before = typeRegistry.getMembers(TypeRegistry.GLOBAL_CONTEXT, FileType.BSL);
    var module = typeRegistry.registerConfigurationType("МодульОбъекта");

    // when
    typeRegistry.registerMemberSource(module, () -> List.of(MemberDescriptor.method("Создать")), FileType.BSL);

    // then
    assertThat(typeRegistry.getMembers(TypeRegistry.GLOBAL_CONTEXT, FileType.BSL)).isSameAs(before);
    assertThat(typeRegistry.getMembers(module, FileType.BSL)).anyMatch(member -> member.matches("Создать"));
  }

  @Test
  void declarationOfCommonModuleReachesItsGlobalMember() {
    // given: общий модуль объявлен по метаданным, до разбора файла, — без символа
    var commonModule = typeRegistry.registerConfigurationType("ОбщегоНазначения");
    typeRegistry.registerGlobalPropertyType(commonModule, FileType.BSL);
    assertThat(globalMember("ОбщегоНазначения", FileType.BSL).getSourceSymbol()).isEmpty();
    var declaration = mock(SourceDefinedSymbol.class);

    // when: разбор файла докладывает символ модуля
    typeRegistry.registerGlobalPropertyType(commonModule, FileType.BSL, declaration);

    // then
    assertThat(globalMember("ОбщегоНазначения", FileType.BSL).getSourceSymbol()).containsSame(declaration);
  }

  @Test
  void newDeclarationOfReparsedCommonModuleReachesItsGlobalMember() {
    // given
    var commonModule = typeRegistry.registerConfigurationType("ОбщегоНазначения");
    typeRegistry.registerGlobalPropertyType(commonModule, FileType.BSL, mock(SourceDefinedSymbol.class));
    globalMember("ОбщегоНазначения", FileType.BSL);
    var reparsed = mock(SourceDefinedSymbol.class);

    // when
    typeRegistry.registerGlobalPropertyType(commonModule, FileType.BSL, reparsed);

    // then
    assertThat(globalMember("ОбщегоНазначения", FileType.BSL).getSourceSymbol()).containsSame(reparsed);
  }

  @Test
  void displayNameRegisteredAfterMarkingReachesGlobalMember() {
    // given
    var commonModule = typeRegistry.registerConfigurationType("ОбщегоНазначения");
    typeRegistry.registerGlobalPropertyType(commonModule, FileType.BSL);
    globalMember("ОбщегоНазначения", FileType.BSL);

    // when
    typeRegistry.registerDisplayName(commonModule, BilingualString.of("ОбщегоНазначения", "Common"));

    // then
    assertThat(typeRegistry.getMembers(TypeRegistry.GLOBAL_CONTEXT, FileType.BSL))
      .anyMatch(member -> member.matches("Common"));
  }

  @Test
  void newDeclarationOfUserTypeReachesItsGlobalMember() {
    // given: класс OneScript, видимый глобально
    var userType = typeRegistry.registerUserType("МойКласс", mock(SourceDefinedSymbol.class), FileType.OS);
    typeRegistry.registerGlobalPropertyType(userType, FileType.OS);
    globalMember("МойКласс", FileType.OS);
    var reparsed = mock(SourceDefinedSymbol.class);

    // when
    typeRegistry.registerUserType("МойКласс", reparsed, FileType.OS);

    // then
    assertThat(globalMember("МойКласс", FileType.OS).getSourceSymbol()).containsSame(reparsed);
  }

  private MemberDescriptor globalMember(String name, FileType fileType) {
    return typeRegistry.getMembers(TypeRegistry.GLOBAL_CONTEXT, fileType).stream()
      .filter(member -> member.matches(name))
      .findFirst()
      .orElseThrow();
  }
}
