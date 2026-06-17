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
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceBeanScope;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.util.TestApplicationContext;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Фаза 1 рефактора глобальной области (issue #3994): exposedAsGlobal-типы
 * (системные перечисления и пр.) становятся свойствами-членами синтетического
 * типа {@link TypeRegistry#GLOBAL_CONTEXT}, а не публикуются отдельным
 * push-механизмом в GlobalScopeProvider.
 */
@SpringBootTest
class GlobalContextMembersTest {

  @Autowired
  private WorkspaceBeanScope workspaceScope;

  @TempDir
  private Path freshWorkspaceDir;

  private URI freshWorkspaceUri;

  @AfterEach
  void tearDown() {
    WorkspaceContextHolder.clear();
    if (freshWorkspaceUri != null) {
      workspaceScope.removeWorkspace(freshWorkspaceUri);
    }
  }

  @Test
  void exposedGlobalEnumBecomesPropertyMemberOfGlobalContext() {
    // given: свежий workspace-scope с прогнанным bootstrap TypeRegistry
    freshWorkspaceUri = Absolute.uri(freshWorkspaceDir.toUri());
    try (var ignored = WorkspaceContextHolder.forUri(freshWorkspaceUri, "issue-3994-globalctx")) {
      var typeRegistry = TestApplicationContext.getBean(TypeRegistry.class);
      typeRegistry.ensureInitialized();

      // when: члены синтетического типа ГлобальныйКонтекст
      var members = typeRegistry.getMembers(TypeRegistry.GLOBAL_CONTEXT, FileType.BSL);

      // then: системное перечисление КодировкаТекста — свойство-член с типом-значением перечисления
      var encoding = members.stream()
        .filter(member -> member.kind() == MemberKind.PROPERTY && member.matches("КодировкаТекста"))
        .findFirst();
      assertThat(encoding)
        .as("КодировкаТекста должно быть свойством-членом ГлобальногоКонтекста")
        .isPresent();
      assertThat(encoding.orElseThrow().returnTypes().refs())
        .as("тип-значение свойства — само перечисление")
        .anyMatch(ref -> ref.qualifiedName().equals("КодировкаТекста"));
    }
  }

  @Test
  void oscriptGlobalFunctionResolvesForOsFileType() {
    // given: свежий workspace-scope (issue #3994 — GLOBAL_CONTEXT для OS наполняет
    // отдельный OneScript-composer из builtin-oscript-globals.json).
    freshWorkspaceUri = Absolute.uri(freshWorkspaceDir.toUri());
    try (var ignored = WorkspaceContextHolder.forUri(freshWorkspaceUri, "issue-3994-os")) {
      var typeRegistry = TestApplicationContext.getBean(TypeRegistry.class);
      typeRegistry.ensureInitialized();
      var globalScope = TestApplicationContext.getBean(GlobalScopeProvider.class);

      // when/then: глобальная функция резолвится в .os как метод-член контекста
      var message = globalScope.globalMember("Сообщить", FileType.OS);
      assertThat(message)
        .as("глобальная функция должна резолвиться в OS-файле")
        .isPresent();
      assertThat(message.orElseThrow().kind()).isEqualTo(MemberKind.METHOD);
    }
  }

  @Test
  void globalFunctionBecomesMethodMemberOfGlobalContext() {
    // given: свежий workspace-scope с прогнанным bootstrap TypeRegistry
    freshWorkspaceUri = Absolute.uri(freshWorkspaceDir.toUri());
    try (var ignored = WorkspaceContextHolder.forUri(freshWorkspaceUri, "issue-3994-globalfn")) {
      var typeRegistry = TestApplicationContext.getBean(TypeRegistry.class);
      typeRegistry.ensureInitialized();

      // when: члены синтетического типа ГлобальныйКонтекст
      var members = typeRegistry.getMembers(TypeRegistry.GLOBAL_CONTEXT, FileType.BSL);

      // then: глобальная функция Сообщить — метод-член контекста
      assertThat(members)
        .as("глобальная функция Сообщить должна быть методом-членом ГлобальногоКонтекста")
        .anyMatch(member -> member.kind() == MemberKind.METHOD && member.matches("Сообщить"));
    }
  }

  @Test
  void globalMemberResolvesAndProjectionsClassify() {
    // given: свежий workspace-scope с прогнанным bootstrap TypeRegistry
    freshWorkspaceUri = Absolute.uri(freshWorkspaceDir.toUri());
    try (var ignored = WorkspaceContextHolder.forUri(freshWorkspaceUri, "issue-3994-proj")) {
      var typeRegistry = TestApplicationContext.getBean(TypeRegistry.class);
      typeRegistry.ensureInitialized();
      var globalScope = TestApplicationContext.getBean(GlobalScopeProvider.class);

      // when/then: резолв безпрефиксного имени в член GLOBAL_CONTEXT
      var function = globalScope.globalMember("Сообщить", FileType.BSL);
      assertThat(function).as("Сообщить резолвится").isPresent();
      assertThat(function.orElseThrow().kind()).isEqualTo(MemberKind.METHOD);

      var encoding = globalScope.globalMember("КодировкаТекста", FileType.BSL);
      assertThat(encoding).as("КодировкаТекста резолвится").isPresent();
      assertThat(encoding.orElseThrow().kind()).isEqualTo(MemberKind.PROPERTY);

      // then: тип-значение свойства классифицируется как перечисление
      var valueType = encoding.orElseThrow().returnTypes().refs().stream().findFirst().orElseThrow();
      assertThat(typeRegistry.isEnumType(valueType))
        .as("тип-значение КодировкаТекста — перечисление")
        .isTrue();

      // then: ось type-name отдельно — Структура конструируема, Сообщить нет
      assertThat(typeRegistry.isConstructibleTypeName("Структура", FileType.BSL))
        .as("Структура — конструируемый тип")
        .isTrue();
      assertThat(typeRegistry.isConstructibleTypeName("Сообщить", FileType.BSL))
        .as("Сообщить — не тип")
        .isFalse();
    }
  }
}
