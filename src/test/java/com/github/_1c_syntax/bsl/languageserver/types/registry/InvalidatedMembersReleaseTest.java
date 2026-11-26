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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceBeanScope;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.util.TestApplicationContext;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.ref.WeakReference;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Сброшенные члены типа не удерживаются кэшем до следующего чтения: с ними держались бы
 * символы-источники прежней редакции модуля, а через них — всё его прежнее дерево.
 */
@SpringBootTest
class InvalidatedMembersReleaseTest {

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
    workspace = WorkspaceContextHolder.forUri(workspaceUri, "invalidated-members-release");
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
  void invalidatedMembersAreNotHeldUntilNextRead() {
    // given: члены модуля собраны по символу первой редакции
    var module = typeRegistry.registerConfigurationType("МодульСИсточником");
    var owner = mock(DocumentContext.class);
    var current = new AtomicReference<>(moduleSymbol(owner));
    typeRegistry.registerOwnMemberSource(module,
      () -> List.of(MemberDescriptor.method("Метод").withSourceSymbol(current.get())), FileType.BSL);
    assertThat(typeRegistry.getMembers(module, FileType.BSL)).hasSize(1);
    var previous = new WeakReference<>(current.get());

    // when: модуль перечитан — источник отдаёт новый символ, члены сброшены, но не прочитаны
    current.set(moduleSymbol(owner));
    typeRegistry.invalidateMembers(module);

    // then: прежний символ больше ничем не удерживается
    for (var attempt = 0; attempt < 10 && previous.get() != null; attempt++) {
      System.gc();
    }
    assertThat(previous.get()).as("прежний символ освобождён").isNull();
  }

  private static ModuleSymbol moduleSymbol(DocumentContext owner) {
    return ModuleSymbol.builder()
      .name("МодульСИсточником")
      .owner(owner)
      .range(Ranges.create(0, 0, 10, 0))
      .selectionRange(Ranges.create(0, 0, 0, 9))
      .build();
  }
}
