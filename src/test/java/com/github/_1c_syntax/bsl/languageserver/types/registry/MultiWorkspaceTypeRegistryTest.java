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
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет изоляцию {@link TypeRegistry} между несколькими workspace.
 * Конфликтующие имена ({@code Справочники.Контрагенты}) в каждом workspace
 * должны разрешаться независимо.
 */
@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class MultiWorkspaceTypeRegistryTest {

  private static final URI WS_A = URI.create("file:///tmp/mw-test-a/");
  private static final URI WS_B = URI.create("file:///tmp/mw-test-b/");

  @Autowired
  private TypeRegistry typeRegistry;

  @Autowired
  private GlobalScopeProvider globalScopeProvider;

  @BeforeEach
  void setUp() {
    WorkspaceContextHolder.registerWorkspace(WS_A, "mw-a");
    WorkspaceContextHolder.registerWorkspace(WS_B, "mw-b");
  }

  @AfterEach
  void tearDown() {
    WorkspaceContextHolder.clear();
    WorkspaceContextHolder.unregisterWorkspace(WS_A);
    WorkspaceContextHolder.unregisterWorkspace(WS_B);
  }

  @Test
  void typeRegistryIsIsolatedAcrossWorkspaces() {
    // workspace A: регистрируем Справочники.Контрагенты + namespace Справочники
    WorkspaceContextHolder.set(WS_A, "mw-a");
    var refA = typeRegistry.registerConfigurationType("Справочники.Контрагенты");
    var nsA = typeRegistry.registerConfigurationType("Справочники");
    typeRegistry.registerMemberSource(nsA, () -> List.of(MemberDescriptor.property("Контрагенты", refA)), FileType.BSL);
    typeRegistry.registerAsGlobalProperty(nsA, FileType.BSL);

    // workspace B: регистрируем только Справочники.Номенклатура + свой namespace
    WorkspaceContextHolder.set(WS_B, "mw-b");
    var refB = typeRegistry.registerConfigurationType("Справочники.Номенклатура");
    var nsB = typeRegistry.registerConfigurationType("Справочники");
    typeRegistry.registerMemberSource(nsB, () -> List.of(MemberDescriptor.property("Номенклатура", refB)), FileType.BSL);
    typeRegistry.registerAsGlobalProperty(nsB, FileType.BSL);

    // A видит только свои типы
    WorkspaceContextHolder.set(WS_A, "mw-a");
    assertThat(typeRegistry.resolve("Справочники.Контрагенты")).isPresent();
    assertThat(typeRegistry.resolve("Справочники.Номенклатура")).isEmpty();
    var nsRefA = globalScopeProvider.findGlobalContext("Справочники").orElseThrow();
    assertThat(typeRegistry.getMembers(nsRefA))
      .extracting(MemberDescriptor::name)
      .containsExactly("Контрагенты");

    // B видит только свои типы
    WorkspaceContextHolder.set(WS_B, "mw-b");
    assertThat(typeRegistry.resolve("Справочники.Номенклатура")).isPresent();
    assertThat(typeRegistry.resolve("Справочники.Контрагенты")).isEmpty();
    var nsRefB = globalScopeProvider.findGlobalContext("Справочники").orElseThrow();
    assertThat(typeRegistry.getMembers(nsRefB))
      .extracting(MemberDescriptor::name)
      .containsExactly("Номенклатура");
  }

  @Test
  void typeServiceFollowsWorkspaceContext() {
    WorkspaceContextHolder.set(WS_A, "mw-a");
    typeRegistry.registerConfigurationType("ОбщийМодульA");
    typeRegistry.registerAsGlobalProperty(typeRegistry.resolve("ОбщийМодульA").orElseThrow(), FileType.BSL);

    WorkspaceContextHolder.set(WS_B, "mw-b");
    typeRegistry.registerConfigurationType("ОбщийМодульB");
    typeRegistry.registerAsGlobalProperty(typeRegistry.resolve("ОбщийМодульB").orElseThrow(), FileType.BSL);

    WorkspaceContextHolder.set(WS_A, "mw-a");
    assertThat(globalScopeProvider.findGlobalContext("ОбщийМодульA")).isPresent();
    assertThat(globalScopeProvider.findGlobalContext("ОбщийМодульB")).isEmpty();

    WorkspaceContextHolder.set(WS_B, "mw-b");
    assertThat(globalScopeProvider.findGlobalContext("ОбщийМодульB")).isPresent();
    assertThat(globalScopeProvider.findGlobalContext("ОбщийМодульA")).isEmpty();
  }
}
