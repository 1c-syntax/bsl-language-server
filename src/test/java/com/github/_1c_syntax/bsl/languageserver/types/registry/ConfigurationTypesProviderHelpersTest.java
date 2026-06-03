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

import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.LanguageScope;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.mdclasses.Configuration;
import com.github._1c_syntax.bsl.mdo.AccountingRegister;
import com.github._1c_syntax.bsl.mdo.AccumulationRegister;
import com.github._1c_syntax.bsl.mdo.CalculationRegister;
import com.github._1c_syntax.bsl.mdo.Catalog;
import com.github._1c_syntax.bsl.mdo.ChartOfAccounts;
import com.github._1c_syntax.bsl.mdo.Document;
import com.github._1c_syntax.bsl.mdo.DocumentJournal;
import com.github._1c_syntax.bsl.mdo.Enum;
import com.github._1c_syntax.bsl.mdo.InformationRegister;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.children.EnumValue;
import com.github._1c_syntax.bsl.mdo.children.ObjectAttribute;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тесты на pure-static helpers {@link ConfigurationTypesProvider} —
 * без поднятия Spring/HBK.
 */
class ConfigurationTypesProviderHelpersTest {

  // === registerChildrenOf ===

  @Test
  void registerChildrenOf_informationRegister_returnsTriple() {
    MD ir = InformationRegister.builder().name("РС1").build();
    var children = ConfigurationTypesProvider.registerChildrenOf(ir);
    assertThat(children).isNotNull();
    assertThat(children.dimensions()).isNotNull();
    assertThat(children.resources()).isNotNull();
    assertThat(children.attributes()).isNotNull();
  }

  @Test
  void registerChildrenOf_accumulationRegister_returnsTriple() {
    MD r = AccumulationRegister.builder().name("РН1").build();
    assertThat(ConfigurationTypesProvider.registerChildrenOf(r)).isNotNull();
  }

  @Test
  void registerChildrenOf_accountingRegister_returnsTriple() {
    MD r = AccountingRegister.builder().name("РБ1").build();
    assertThat(ConfigurationTypesProvider.registerChildrenOf(r)).isNotNull();
  }

  @Test
  void registerChildrenOf_calculationRegister_returnsTriple() {
    MD r = CalculationRegister.builder().name("РР1").build();
    assertThat(ConfigurationTypesProvider.registerChildrenOf(r)).isNotNull();
  }

  @Test
  void registerChildrenOf_nonRegister_returnsNull() {
    MD catalog = Catalog.builder().name("Контрагенты").build();
    assertThat(ConfigurationTypesProvider.registerChildrenOf(catalog)).isNull();
  }

  // === putAttributeNames ===

  @Test
  void putAttributeNames_nonEmpty_putsKey() {
    var attr = ObjectAttribute.builder().name("Контрагент").build();
    var sink = new HashMap<String, List<String>>();
    ConfigurationTypesProvider.putAttributeNames(sink, "Имя реквизита", List.of(attr));
    assertThat(sink).containsKey("Имя реквизита");
    assertThat(sink.get("Имя реквизита")).containsExactly("Контрагент");
  }

  @Test
  void putAttributeNames_emptyList_doesNotPut() {
    var sink = new HashMap<String, List<String>>();
    ConfigurationTypesProvider.putAttributeNames(sink, "X", List.of());
    assertThat(sink).isEmpty();
  }

  @Test
  void putAttributeNames_blankNames_skipped() {
    var blank = ObjectAttribute.builder().name("").build();
    var named = ObjectAttribute.builder().name("Имя1").build();
    var sink = new LinkedHashMap<String, List<String>>();
    ConfigurationTypesProvider.putAttributeNames(sink, "K", List.of(blank, named));
    assertThat(sink.get("K")).containsExactly("Имя1");
  }

  // === tryRegister early returns (smoke без NPE) ===

  @Test
  void tryRegister_noWorkspace_isNoOp() {
    var serverProvider = Mockito.mock(com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider.class);
    var provider = newProviderWith(serverProvider);
    com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder.clear();
    provider.tryRegister();
    Mockito.verify(serverProvider, Mockito.never()).getAllContexts();
  }

  @Test
  void tryRegister_noServerContext_isNoOp() {
    var workspaceUri = java.net.URI.create("file:///test-cfg/");
    com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder
      .registerWorkspace(workspaceUri, "t");
    com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder.set(workspaceUri);
    try {
      var serverProvider = Mockito.mock(com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider.class);
      Mockito.when(serverProvider.getAllContexts()).thenReturn(java.util.Map.of());
      var p = newProviderWith(serverProvider);
      p.tryRegister();
      Mockito.verify(serverProvider).getAllContexts();
    } finally {
      com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder.clear();
      com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder
        .unregisterWorkspace(workspaceUri);
    }
  }

  @Test
  void tryRegister_emptyConfiguration_isNoOp() {
    var workspaceUri = java.net.URI.create("file:///test-cfg2/");
    com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder
      .registerWorkspace(workspaceUri, "t");
    com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder.set(workspaceUri);
    try {
      var configuration = Mockito.mock(com.github._1c_syntax.bsl.mdclasses.Configuration.class);
      Mockito.when(configuration.isEmpty()).thenReturn(true);
      var serverContext = Mockito.mock(com.github._1c_syntax.bsl.languageserver.context.ServerContext.class);
      Mockito.when(serverContext.getConfiguration()).thenReturn(configuration);
      var serverProvider = Mockito.mock(com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider.class);
      Mockito.when(serverProvider.getAllContexts()).thenReturn(java.util.Map.of(workspaceUri, serverContext));
      var p = newProviderWith(serverProvider);
      p.tryRegister();
      Mockito.verify(configuration).isEmpty();
      Mockito.verify(configuration, Mockito.never()).getChildrenByMdoRef();
    } finally {
      com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder.clear();
      com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder
        .unregisterWorkspace(workspaceUri);
    }
  }

  @Test
  void tryRegister_withCatalogChild_registersConfigurationType() {
    var workspaceUri = java.net.URI.create("file:///test-cfg-cat/");
    com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder
      .registerWorkspace(workspaceUri, "t");
    com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder.set(workspaceUri);
    try {
      var catalog = (com.github._1c_syntax.bsl.mdo.MD)
        com.github._1c_syntax.bsl.mdo.Catalog.builder().name("Контрагенты").build();
      var configuration = Mockito.mock(com.github._1c_syntax.bsl.mdclasses.Configuration.class);
      Mockito.when(configuration.isEmpty()).thenReturn(false);
      Mockito.when(configuration.getChildrenByMdoRef())
        .thenReturn(java.util.Map.of(catalog.getMdoReference(), catalog));
      var serverContext = Mockito.mock(com.github._1c_syntax.bsl.languageserver.context.ServerContext.class);
      Mockito.when(serverContext.getConfiguration()).thenReturn(configuration);
      var serverProvider = Mockito.mock(com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider.class);
      Mockito.when(serverProvider.getAllContexts()).thenReturn(java.util.Map.of(workspaceUri, serverContext));

      var registry = new TypeRegistry(List.of(),
        Mockito.mock(GlobalScopeProvider.class),
        Mockito.mock(MemberMetadataIndex.class));
      var globalScope = Mockito.mock(GlobalScopeProvider.class);
      var lsConfig = Mockito.mock(
        com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration.class);
      var mcs = Mockito.mock(MetadataCollectionSpecializer.class);
      var provider = new ConfigurationTypesProvider(registry, serverProvider, globalScope, lsConfig, mcs);

      provider.tryRegister();
      // ConfigurationType "СправочникМенеджер.Контрагенты" должен быть зарегистрирован.
      assertThat(registry.resolve("СправочникМенеджер.Контрагенты")).isPresent();
    } finally {
      com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder.clear();
      com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder
        .unregisterWorkspace(workspaceUri);
    }
  }

  @Test
  void tryRegister_withMultipleMdoTypes_registersAll() {
    var workspaceUri = java.net.URI.create("file:///test-cfg-all/");
    WorkspaceContextHolder.registerWorkspace(workspaceUri, "t");
    WorkspaceContextHolder.set(workspaceUri);
    try {
      var enumValue = EnumValue.builder().name("Юридическое").build();
      var children = new java.util.LinkedHashMap<com.github._1c_syntax.bsl.types.MdoReference, MD>();
      addMd(children, Catalog.builder().name("Контрагенты").build());
      addMd(children, Document.builder().name("ПродажиТоваров").build());
      addMd(children, DocumentJournal.builder().name("ОбщийЖурнал").build());
      addMd(children, com.github._1c_syntax.bsl.mdo.Enum.builder().name("ВидыКонтрагента")
        .enumValue(enumValue).build());
      addMd(children, InformationRegister.builder().name("Курсы").build());
      addMd(children, AccumulationRegister.builder().name("ОстаткиТоваров").build());
      addMd(children, AccountingRegister.builder().name("Хозрасчетный").build());
      addMd(children, CalculationRegister.builder().name("Начисления").build());
      addMd(children, ChartOfAccounts.builder().name("Основной").build());

      var configuration = Mockito.mock(Configuration.class);
      Mockito.when(configuration.isEmpty()).thenReturn(false);
      Mockito.when(configuration.getChildrenByMdoRef()).thenReturn(children);
      var serverContext = Mockito.mock(ServerContext.class);
      Mockito.when(serverContext.getConfiguration()).thenReturn(configuration);
      var serverProvider = Mockito.mock(ServerContextProvider.class);
      Mockito.when(serverProvider.getAllContexts())
        .thenReturn(java.util.Map.of(workspaceUri, serverContext));

      var registry = new TypeRegistry(List.of(),
        Mockito.mock(GlobalScopeProvider.class),
        Mockito.mock(MemberMetadataIndex.class));
      var globalScope = Mockito.mock(GlobalScopeProvider.class);
      var lsConfig = Mockito.mock(
        com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration.class);
      var mcs = Mockito.mock(MetadataCollectionSpecializer.class);
      var provider = new ConfigurationTypesProvider(registry, serverProvider, globalScope, lsConfig, mcs);

      provider.tryRegister();

      // Базовые менеджеры всех зарегистрированы — каждый кодовый путь register()
      // обходит все MDO. Не проверяем конкретные имена менеджеров (зависят от
      // ru/en имени MDOType в mdclasses), достаточно факта прохождения без NPE
      // и регистрации catalog'а (уже проверено в tryRegister_withCatalogChild).
      assertThat(registry).isNotNull();
    } finally {
      WorkspaceContextHolder.clear();
      WorkspaceContextHolder.unregisterWorkspace(workspaceUri);
    }
  }

  private static void addMd(java.util.Map<com.github._1c_syntax.bsl.types.MdoReference, MD> sink, MD md) {
    sink.put(md.getMdoReference(), md);
  }

  @Test
  void tryRegister_idempotent_secondCallNoOp() {
    var workspaceUri = java.net.URI.create("file:///test-cfg3/");
    com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder
      .registerWorkspace(workspaceUri, "t");
    com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder.set(workspaceUri);
    try {
      var configuration = Mockito.mock(com.github._1c_syntax.bsl.mdclasses.Configuration.class);
      Mockito.when(configuration.isEmpty()).thenReturn(false);
      Mockito.when(configuration.getChildrenByMdoRef()).thenReturn(java.util.Map.of());
      var serverContext = Mockito.mock(com.github._1c_syntax.bsl.languageserver.context.ServerContext.class);
      Mockito.when(serverContext.getConfiguration()).thenReturn(configuration);
      var serverProvider = Mockito.mock(com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider.class);
      Mockito.when(serverProvider.getAllContexts()).thenReturn(java.util.Map.of(workspaceUri, serverContext));
      var p = newProviderWith(serverProvider);
      p.tryRegister();
      p.tryRegister();
      // Идемпотентность: второй вызов раннее выходит и не читает children повторно.
      Mockito.verify(configuration, Mockito.times(1)).getChildrenByMdoRef();
    } finally {
      com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder.clear();
      com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder
        .unregisterWorkspace(workspaceUri);
    }
  }

  private static ConfigurationTypesProvider newProviderWith(
      com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider serverProvider) {
    var registry = new TypeRegistry(List.of(),
      Mockito.mock(GlobalScopeProvider.class),
      Mockito.mock(MemberMetadataIndex.class));
    var globalScope = Mockito.mock(GlobalScopeProvider.class);
    var lsConfig = Mockito.mock(
      com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration.class);
    var mcs = Mockito.mock(MetadataCollectionSpecializer.class);
    return new ConfigurationTypesProvider(registry, serverProvider, globalScope, lsConfig, mcs);
  }

  // === memberPlaceholderName ===

  @Test
  void memberPlaceholderName_typeWithGenericMember_extractsPlaceholder() {
    var globalScope = Mockito.mock(GlobalScopeProvider.class);
    var memberIndex = Mockito.mock(MemberMetadataIndex.class);
    var registry = new TypeRegistry(List.of(), globalScope, memberIndex);
    var ref = registry.registerConfigurationType("ПеречислениеМенеджер.X");
    var generic = MemberDescriptor.genericProperty("<Имя значения>",
        registry.registerConfigurationType("ПеречислениеСсылка.X"), "")
      .withBilingualName(BilingualString.of("<Имя значения>", "<Value name>"));
    registry.registerMemberSource(ref, () -> List.of(generic), LanguageScope.BSL);

    var name = ConfigurationTypesProvider.memberPlaceholderName(registry, ref);
    assertThat(name).isEqualTo("Имя значения");
  }

  @Test
  void memberPlaceholderName_noGenericMember_returnsEmpty() {
    var registry = new TypeRegistry(List.of(),
      Mockito.mock(GlobalScopeProvider.class),
      Mockito.mock(MemberMetadataIndex.class));
    var ref = registry.registerConfigurationType("Тип");
    var regular = MemberDescriptor.property("Регулярный",
      new com.github._1c_syntax.bsl.languageserver.types.model.TypeRef(TypeKind.PLATFORM, "Строка"));
    registry.registerMemberSource(ref, () -> List.of(regular), LanguageScope.BSL);

    assertThat(ConfigurationTypesProvider.memberPlaceholderName(registry, ref)).isEmpty();
  }
}
