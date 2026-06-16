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
import com.github._1c_syntax.bsl.languageserver.types.scope.GlobalSymbolScope;
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
 * Страж развязки неявной связанности bootstrap (issue #3994): чтение
 * {@link GlobalScopeProvider} в свежем workspace-scope должно само
 * материализовать workspace-scoped {@code TypeRegistry}, чей
 * {@code @PostConstruct bootstrap()} наполняет провайдер платформенным
 * глобальным скоупом — без ручного {@code typeRegistry.resolve("")} у
 * потребителя.
 */
@SpringBootTest
class GlobalScopeProviderBootstrapTest {

  @Autowired
  private GlobalScopeProvider globalScopeProvider;

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
  void readingGlobalScopeMaterializesTypeRegistryBootstrap() {
    // given: свежий workspace-scope (уникальный @TempDir-URI, в который ещё никто
    // не входил) — значит @PostConstruct bootstrap() workspace-scoped TypeRegistry
    // в этом scope не выполнялся. Имя платформенного класса «Структура» попадает в
    // GlobalScopeProvider ИСКЛЮЧИТЕЛЬНО из этого bootstrap (registerAsPlatformClass);
    // собственный publishGlobals() провайдера записи с Role.TYPE_NAME не публикует.
    freshWorkspaceUri = Absolute.uri(freshWorkspaceDir.toUri());

    try (var ignored = WorkspaceContextHolder.forUri(freshWorkspaceUri, "issue-3994")) {
      // when: первым же действием в scope читаем имя класса, не дёргая TypeRegistry руками
      var entry = globalScopeProvider.findGlobalEntry("Структура", FileType.BSL);

      // then: чтение само гарантировало bootstrap, класс найден как имя типа
      assertThat(entry)
        .as("чтение GlobalScopeProvider должно само материализовать bootstrap TypeRegistry")
        .isPresent();
      assertThat(entry.orElseThrow().role())
        .isEqualTo(GlobalSymbolScope.Role.TYPE_NAME);
    }
  }
}
