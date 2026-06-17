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

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.mdclasses.Configuration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URI;
import java.util.List;

/**
 * Покрытие early-return веток {@link ConfigurationGenericExpander} —
 * отсутствие workspace, отсутствие контекста, пустая конфигурация.
 */
class ConfigurationGenericExpanderTest {

  @Test
  void registerCommonLibraryExpansions_noWorkspace_noOp() {
    var serverProvider = Mockito.mock(ServerContextProvider.class);
    var registry = new TypeRegistry(List.of(),
      Mockito.mock(MemberMetadataIndex.class));
    var expander = new ConfigurationGenericExpander(registry, serverProvider);
    WorkspaceContextHolder.clear();
    expander.registerCommonLibraryExpansions();
    Mockito.verify(serverProvider, Mockito.never()).getAllContexts();
  }

  @Test
  void registerCommonLibraryExpansions_noServerContext_noOp() {
    var workspaceUri = URI.create("file:///test-cge-no-ctx/");
    WorkspaceContextHolder.registerWorkspace(workspaceUri, "t");
    WorkspaceContextHolder.set(workspaceUri);
    try {
      var serverProvider = Mockito.mock(ServerContextProvider.class);
      Mockito.when(serverProvider.getAllContexts()).thenReturn(java.util.Map.of());
      var registry = new TypeRegistry(List.of(),
        Mockito.mock(MemberMetadataIndex.class));
      var expander = new ConfigurationGenericExpander(registry, serverProvider);
      expander.registerCommonLibraryExpansions();
      Mockito.verify(serverProvider).getAllContexts();
    } finally {
      WorkspaceContextHolder.clear();
      WorkspaceContextHolder.unregisterWorkspace(workspaceUri);
    }
  }

  @Test
  void registerCommonLibraryExpansions_emptyConfiguration_noOp() {
    var workspaceUri = URI.create("file:///test-cge-empty-cfg/");
    WorkspaceContextHolder.registerWorkspace(workspaceUri, "t");
    WorkspaceContextHolder.set(workspaceUri);
    try {
      var configuration = Mockito.mock(Configuration.class);
      Mockito.when(configuration.isEmpty()).thenReturn(true);
      var serverContext = Mockito.mock(ServerContext.class);
      Mockito.when(serverContext.getConfiguration()).thenReturn(configuration);
      var serverProvider = Mockito.mock(ServerContextProvider.class);
      Mockito.when(serverProvider.getAllContexts()).thenReturn(java.util.Map.of(workspaceUri, serverContext));
      var registry = new TypeRegistry(List.of(),
        Mockito.mock(MemberMetadataIndex.class));
      var expander = new ConfigurationGenericExpander(registry, serverProvider);
      expander.registerCommonLibraryExpansions();
      Mockito.verify(configuration).isEmpty();
    } finally {
      WorkspaceContextHolder.clear();
      WorkspaceContextHolder.unregisterWorkspace(workspaceUri);
    }
  }

  @Test
  void registerExternalDataSourceSpecializations_emptyBindings_noOp() {
    var registry = new TypeRegistry(List.of(),
      Mockito.mock(MemberMetadataIndex.class));
    var serverProvider = Mockito.mock(ServerContextProvider.class);
    var expander = new ConfigurationGenericExpander(registry, serverProvider);
    expander.registerFamilySpecializations("X", java.util.Map.of());
    org.assertj.core.api.Assertions.assertThat(registry.resolve("X")).isEmpty();
  }

  @Test
  void registerExternalDataSourceSpecializations_blankNamedEntities_skipsThem() {
    var blankTable = com.github._1c_syntax.bsl.mdo.children.ExternalDataSourceTable.builder()
      .name("").build();
    var blankCube = com.github._1c_syntax.bsl.mdo.children.ExternalDataSourceCube.builder()
      .name("").build();
    var goodCube = com.github._1c_syntax.bsl.mdo.children.ExternalDataSourceCube.builder()
      .name("Куб1")
      .dimensionTable(com.github._1c_syntax.bsl.mdo.children.ExternalDataSourceCubeDimensionTable.builder()
        .name("").build())
      .dimension(com.github._1c_syntax.bsl.mdo.children.Dimension.builder()
        .name("").build())
      .build();
    var eds = com.github._1c_syntax.bsl.mdo.ExternalDataSource.builder()
      .name("ВИД1")
      .table(blankTable)
      .cube(blankCube)
      .cube(goodCube)
      .build();
    var registry = new TypeRegistry(List.of(),
      Mockito.mock(MemberMetadataIndex.class));
    var serverProvider = Mockito.mock(ServerContextProvider.class);
    var expander = new ConfigurationGenericExpander(registry, serverProvider);
    expander.registerExternalDataSourceSpecializations(List.of(eds));
    org.assertj.core.api.Assertions.assertThat(registry.resolve("ВнешнийИсточникДанных.ВИД1")).isEmpty();
  }
}
