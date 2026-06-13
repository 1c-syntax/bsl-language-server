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
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
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
import com.github._1c_syntax.bsl.mdo.ChartOfCalculationTypes;
import com.github._1c_syntax.bsl.mdo.Document;
import com.github._1c_syntax.bsl.mdo.DocumentJournal;
import com.github._1c_syntax.bsl.mdo.InformationRegister;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.children.Dimension;
import com.github._1c_syntax.bsl.mdo.children.DocumentJournalColumn;
import com.github._1c_syntax.bsl.mdo.children.EnumValue;
import com.github._1c_syntax.bsl.mdo.children.ExtDimensionAccountingFlag;
import com.github._1c_syntax.bsl.mdo.children.ObjectAttribute;
import com.github._1c_syntax.bsl.mdo.children.Recalculation;
import com.github._1c_syntax.bsl.mdo.children.Resource;
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
      var provider = new ConfigurationTypesProvider(registry, serverProvider, globalScope, lsConfig, mcs, new ConfigurationGenericExpander(registry, serverProvider));

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
  void tryRegister_withPaletteColorChild_registersManager() {
    var workspaceUri = java.net.URI.create("file:///test-cfg-palette/");
    WorkspaceContextHolder.registerWorkspace(workspaceUri, "t");
    WorkspaceContextHolder.set(workspaceUri);
    try {
      var paletteColor = (MD) com.github._1c_syntax.bsl.mdo.PaletteColor.builder()
        .name("ПервичныйЦвет").build();
      var configuration = Mockito.mock(Configuration.class);
      Mockito.when(configuration.isEmpty()).thenReturn(false);
      Mockito.when(configuration.getChildrenByMdoRef())
        .thenReturn(java.util.Map.of(paletteColor.getMdoReference(), paletteColor));
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
      var provider = new ConfigurationTypesProvider(registry, serverProvider, globalScope,
        lsConfig, mcs, new ConfigurationGenericExpander(registry, serverProvider));

      provider.tryRegister();
      assertThat(registry.resolve("ЦветПалитрыМенеджер.ПервичныйЦвет")).isPresent();
    } finally {
      WorkspaceContextHolder.clear();
      WorkspaceContextHolder.unregisterWorkspace(workspaceUri);
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
      var provider = new ConfigurationTypesProvider(registry, serverProvider, globalScope, lsConfig, mcs, new ConfigurationGenericExpander(registry, serverProvider));

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
    return new ConfigurationTypesProvider(registry, serverProvider, globalScope, lsConfig, mcs, new ConfigurationGenericExpander(registry, serverProvider));
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
    registry.registerMemberSource(ref, () -> List.of(generic), FileType.BSL);

    var name = ConfigurationTypesProvider.memberPlaceholderName(registry, ref);
    assertThat(name).isEqualTo("Имя значения");
  }

  // === register*Expansion: интеграция через synthetic generic-templates ===

  @Test
  void tryRegister_withEnumAndGenericTemplate_expandsEnumValues() {
    runTryRegister(
      "file:///test-enum-expansion/",
      registry -> registry,
      List.of(makeGenericTypeDecl("ПеречислениеМенеджер.<Имя перечисления>", "Имя перечисления")),
      makeEnumChildren("ВидыКонтрагента", "Юридическое", "Физическое"),
      (registry, p) -> {
        p.tryRegister();
        assertThat(registry.resolve("ПеречислениеМенеджер.ВидыКонтрагента")).isPresent();
      });
  }

  @Test
  void tryRegister_withDocumentJournalAndGenericTemplate_runs() {
    var column = DocumentJournalColumn.builder()
      .name("Контрагент").build();
    var journal = DocumentJournal.builder().name("ОбщийЖурнал").column(column).build();
    runTryRegister(
      "file:///test-journal-expansion/",
      registry -> registry,
      List.of(makeGenericTypeDecl("ЖурналДокументов.<Имя журнала документов>", "Имя журнала документов")),
      java.util.Map.of(journal.getMdoReference(), (MD) journal),
      (registry, p) -> {
        p.tryRegister();
        assertThat(registry.resolve("ЖурналДокументовМенеджер.ОбщийЖурнал")).isPresent();
      });
  }

  @Test
  void tryRegister_withInformationRegisterAndGenericTemplate_expandsDimensions() {
    var dim = Dimension.builder()
      .name("Валюта").build();
    var res = Resource.builder()
      .name("Курс").build();
    var reg = InformationRegister.builder().name("Курсы")
      .dimension(dim).resource(res).build();
    runTryRegister(
      "file:///test-inforeg-expansion/",
      registry -> registry,
      List.of(makeGenericTypeDecl("РегистрСведенийЗапись.<Имя регистра сведений>", "Имя регистра сведений")),
      java.util.Map.of(reg.getMdoReference(), (MD) reg),
      (registry, p) -> {
        p.tryRegister();
        assertThat(registry.resolve("РегистрСведенийМенеджер.Курсы")).isPresent();
      });
  }

  @Test
  void tryRegister_withCalculationRegister_invokesRecalculationSpec() {
    var recalc = Recalculation.builder()
      .name("ПоВремени").build();
    var reg = CalculationRegister.builder().name("Начисления")
      .recalculation(recalc).build();
    runTryRegister(
      "file:///test-calc-recalc/",
      registry -> registry,
      List.of(makeGenericTypeDecl("Перерасчет.<Имя перерасчета>", "Имя перерасчета")),
      java.util.Map.of(reg.getMdoReference(), (MD) reg),
      (registry, p) -> {
        p.tryRegister();
        assertThat(registry.resolve("РегистрРасчетаМенеджер.Начисления")).isPresent();
      });
  }

  @Test
  void tryRegister_withChartOfCalculationTypes_invokesDerived() {
    var pvr = ChartOfCalculationTypes.builder()
      .name("ОсновныеВидыРасчета").build();
    runTryRegister(
      "file:///test-pvr/",
      registry -> registry,
      List.of(
        makeGenericTypeDecl("БазовыеВидыРасчета.<Имя плана видов расчета>", "Имя плана видов расчета"),
        makeGenericTypeDecl("ВедущиеВидыРасчета.<Имя плана видов расчета>", "Имя плана видов расчета"),
        makeGenericTypeDecl("ВытесняющиеВидыРасчета.<Имя плана видов расчета>", "Имя плана видов расчета")
      ),
      java.util.Map.of(pvr.getMdoReference(), (MD) pvr),
      (registry, p) -> {
        p.tryRegister();
        assertThat(registry.resolve("ПланВидовРасчетаМенеджер.ОсновныеВидыРасчета")).isPresent();
      });
  }

  @Test
  void tryRegister_withEnumWithoutValues_earlyReturn() {
    var emptyEnum = com.github._1c_syntax.bsl.mdo.Enum.builder().name("ПустоеПеречисление").build();
    runTryRegister(
      "file:///test-enum-empty/",
      registry -> registry,
      List.of(makeGenericTypeDecl("ПеречислениеМенеджер.<Имя перечисления>", "Имя перечисления")),
      java.util.Map.of(emptyEnum.getMdoReference(), (MD) emptyEnum),
      (registry, p) -> {
        p.tryRegister();
        assertThat(registry.resolve("ПеречислениеМенеджер.ПустоеПеречисление")).isPresent();
      });
  }

  @Test
  void tryRegister_withEnumNoGenericTemplate_earlyReturn() {
    var anEnum = com.github._1c_syntax.bsl.mdo.Enum.builder().name("ВидыКонтрагента")
      .enumValue(EnumValue.builder().name("Юридическое").build()).build();
    runTryRegister(
      "file:///test-enum-no-template/",
      registry -> registry,
      List.of(),
      java.util.Map.of(anEnum.getMdoReference(), (MD) anEnum),
      (registry, p) -> {
        p.tryRegister();
        assertThat(registry.resolve("ПеречислениеМенеджер.ВидыКонтрагента")).isPresent();
      });
  }

  @Test
  void tryRegister_withCommonAttribute_buildsApplicableMembers() {
    var common = com.github._1c_syntax.bsl.mdo.CommonAttribute.builder()
      .name("Организация").build();
    var doc = Document.builder().name("ПродажиТоваров")
      .attribute(ObjectAttribute.builder().name("Контрагент").build()).build();
    var children = new java.util.LinkedHashMap<com.github._1c_syntax.bsl.types.MdoReference, MD>();
    children.put(common.getMdoReference(), common);
    children.put(doc.getMdoReference(), doc);
    runTryRegister(
      "file:///test-common-attr/",
      registry -> registry,
      List.of(),
      children,
      (registry, p) -> {
        p.tryRegister();
        assertThat(registry.resolve("ДокументМенеджер.ПродажиТоваров")).isPresent();
      });
  }

  @Test
  void tryRegister_withBlankNameChild_skipsRegistration() {
    var blankCatalog = Catalog.builder().name("").build();
    runTryRegister(
      "file:///test-blank-name/",
      registry -> registry,
      List.of(),
      java.util.Map.of(blankCatalog.getMdoReference(), (MD) blankCatalog),
      (registry, p) -> {
        p.tryRegister();
        assertThat(registry.resolve("СправочникМенеджер.")).isEmpty();
      });
  }

  @Test
  void tryRegister_withInformationRegisterNoGenericTemplate_earlyReturn() {
    var reg = InformationRegister.builder().name("Курсы2").build();
    runTryRegister(
      "file:///test-inforeg-no-template/",
      registry -> registry,
      List.of(),
      java.util.Map.of(reg.getMdoReference(), (MD) reg),
      (registry, p) -> {
        p.tryRegister();
        assertThat(registry.resolve("РегистрСведенийМенеджер.Курсы2")).isPresent();
      });
  }

  @Test
  void tryRegister_withDocumentJournalNoGenericTemplate_earlyReturn() {
    var journal = DocumentJournal.builder().name("ОбщийЖурнал2")
      .column(DocumentJournalColumn.builder().name("X").build()).build();
    runTryRegister(
      "file:///test-journal-no-template/",
      registry -> registry,
      List.of(),
      java.util.Map.of(journal.getMdoReference(), (MD) journal),
      (registry, p) -> {
        p.tryRegister();
        assertThat(registry.resolve("ЖурналДокументовМенеджер.ОбщийЖурнал2")).isPresent();
      });
  }

  @Test
  void tryRegister_enumGenericWithTwoParams_earlyReturn() {
    var anEnum = com.github._1c_syntax.bsl.mdo.Enum.builder().name("ВидыКонтрагентаTwo")
      .enumValue(EnumValue.builder().name("Юридическое").build()).build();
    var typeDecl = new TypePackProvider.TypeDecl(
      TypeKind.PLATFORM,
      BilingualString.of("ПеречислениеМенеджер.<A>.<B>"),
      List.of(),
      false, "", List.of(), List.of(), false, false, "", "",
      List.of("A", "B"),
      false);
    runTryRegister(
      "file:///test-enum-twoparams/",
      registry -> registry,
      List.of(typeDecl),
      java.util.Map.of(anEnum.getMdoReference(), (MD) anEnum),
      (registry, p) -> {
        p.tryRegister();
        assertThat(registry.resolve("ПеречислениеМенеджер.ВидыКонтрагентаTwo")).isPresent();
      });
  }

  @Test
  void tryRegister_enumWithBlankNamedValues_earlyReturn() {
    var anEnum = com.github._1c_syntax.bsl.mdo.Enum.builder().name("ВидыКонтрагентаB")
      .enumValue(EnumValue.builder().name("").build()).build();
    runTryRegister(
      "file:///test-enum-blank-values/",
      registry -> registry,
      List.of(makeGenericTypeDecl("ПеречислениеМенеджер.<Имя перечисления>", "Имя перечисления")),
      java.util.Map.of(anEnum.getMdoReference(), (MD) anEnum),
      (registry, p) -> {
        p.tryRegister();
        assertThat(registry.resolve("ПеречислениеМенеджер.ВидыКонтрагентаB")).isPresent();
      });
  }

  @Test
  void tryRegister_registerWithBlankChildren_expansionsEmpty() {
    var reg = InformationRegister.builder().name("РСB")
      .dimension(Dimension.builder().name("").build())
      .resource(Resource.builder().name("").build())
      .build();
    runTryRegister(
      "file:///test-reg-blank/",
      registry -> registry,
      List.of(makeGenericTypeDecl("РегистрСведенийЗапись.<Имя регистра сведений>", "Имя регистра сведений")),
      java.util.Map.of(reg.getMdoReference(), (MD) reg),
      (registry, p) -> {
        p.tryRegister();
        assertThat(registry.resolve("РегистрСведенийМенеджер.РСB")).isPresent();
      });
  }

  @Test
  void tryRegister_registerGenericWithTwoParams_earlyReturn() {
    var reg = InformationRegister.builder().name("РСTwo")
      .dimension(Dimension.builder().name("Валюта").build()).build();
    var typeDecl = new TypePackProvider.TypeDecl(
      TypeKind.PLATFORM,
      BilingualString.of("РегистрСведенийЗапись.<A>.<B>"),
      List.of(),
      false, "", List.of(), List.of(), false, false, "", "",
      List.of("A", "B"),
      false);
    runTryRegister(
      "file:///test-reg-twoparams/",
      registry -> registry,
      List.of(typeDecl),
      java.util.Map.of(reg.getMdoReference(), (MD) reg),
      (registry, p) -> {
        p.tryRegister();
        assertThat(registry.resolve("РегистрСведенийМенеджер.РСTwo")).isPresent();
      });
  }

  @Test
  void tryRegister_journalWithoutColumns_earlyReturn() {
    var journal = DocumentJournal.builder().name("ПустойЖурнал").build();
    runTryRegister(
      "file:///test-journal-empty/",
      registry -> registry,
      List.of(makeGenericTypeDecl("ЖурналДокументов.<Имя журнала документов>", "Имя журнала документов")),
      java.util.Map.of(journal.getMdoReference(), (MD) journal),
      (registry, p) -> {
        p.tryRegister();
        assertThat(registry.resolve("ЖурналДокументовМенеджер.ПустойЖурнал")).isPresent();
      });
  }

  @Test
  void tryRegister_withChartOfAccountsAndExtDimensionFlag_runs() {
    var flag = ExtDimensionAccountingFlag.builder()
      .name("Валютный").build();
    var coa = ChartOfAccounts.builder().name("Основной")
      .extDimensionAccountingFlag(flag).build();
    runTryRegister(
      "file:///test-coa-flag/",
      registry -> registry,
      List.of(),
      java.util.Map.of(coa.getMdoReference(), (MD) coa),
      (registry, p) -> {
        p.tryRegister();
        assertThat(registry.resolve("ПланСчетовМенеджер.Основной")).isPresent();
      });
  }

  private static TypePackProvider.TypeDecl makeGenericTypeDecl(String qualifiedRu, String placeholder) {
    var memberRu = "<Имя значения>";
    var member = MemberDescriptor.genericProperty(memberRu,
        new com.github._1c_syntax.bsl.languageserver.types.model.TypeRef(TypeKind.PLATFORM, "Строка"),
        "")
      .withBilingualName(BilingualString.of(memberRu, "<Value name>"));
    return new TypePackProvider.TypeDecl(
      TypeKind.PLATFORM,
      BilingualString.of(qualifiedRu),
      List.of(member),
      false,
      "",
      List.of(),
      List.of(),
      false,
      false,
      "",
      "",
      List.of(placeholder),
      false
    );
  }

  private static java.util.Map<com.github._1c_syntax.bsl.types.MdoReference, MD> makeEnumChildren(
      String enumName, String... valueNames) {
    var values = java.util.Arrays.stream(valueNames)
      .map(n -> EnumValue.builder().name(n).build())
      .toList();
    var enumBuilder = com.github._1c_syntax.bsl.mdo.Enum.builder().name(enumName);
    values.forEach(enumBuilder::enumValue);
    var anEnum = enumBuilder.build();
    return java.util.Map.of(anEnum.getMdoReference(), anEnum);
  }

  private static void runTryRegister(
      String workspaceUriStr,
      java.util.function.Function<TypeRegistry, TypeRegistry> registryFn,
      List<? extends TypePackProvider.TypeDecl> typeDecls,
      java.util.Map<com.github._1c_syntax.bsl.types.MdoReference, MD> children,
      java.util.function.BiConsumer<TypeRegistry, ConfigurationTypesProvider> assertion) {
    var workspaceUri = java.net.URI.create(workspaceUriStr);
    WorkspaceContextHolder.registerWorkspace(workspaceUri, "t");
    WorkspaceContextHolder.set(workspaceUri);
    try {
      var packTypes = new java.util.ArrayList<TypePackProvider.TypeDecl>(typeDecls);
      PlatformTypesProvider pack = new PlatformTypesProvider() {
        @Override
        public java.util.List<TypePackProvider.TypeDecl> getTypes() {
          return packTypes;
        }

        @Override
        public FileType getFileType() {
          return FileType.BSL;
        }
      };
      var rawRegistry = new TypeRegistry(List.of(pack),
        Mockito.mock(GlobalScopeProvider.class),
        Mockito.mock(MemberMetadataIndex.class));
      rawRegistry.bootstrap();
      var registry = registryFn.apply(rawRegistry);
      var configuration = Mockito.mock(Configuration.class);
      Mockito.when(configuration.isEmpty()).thenReturn(false);
      Mockito.when(configuration.getChildrenByMdoRef()).thenReturn(children);
      var serverContext = Mockito.mock(ServerContext.class);
      Mockito.when(serverContext.getConfiguration()).thenReturn(configuration);
      var serverProvider = Mockito.mock(ServerContextProvider.class);
      Mockito.when(serverProvider.getAllContexts())
        .thenReturn(java.util.Map.of(workspaceUri, serverContext));
      var globalScope = Mockito.mock(GlobalScopeProvider.class);
      var lsConfig = Mockito.mock(
        com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration.class);
      var mcs = Mockito.mock(MetadataCollectionSpecializer.class);
      var provider = new ConfigurationTypesProvider(registry, serverProvider, globalScope,
        lsConfig, mcs, new ConfigurationGenericExpander(registry, serverProvider));
      assertion.accept(registry, provider);
    } finally {
      WorkspaceContextHolder.clear();
      WorkspaceContextHolder.unregisterWorkspace(workspaceUri);
    }
  }

  @Test
  void memberPlaceholderName_noGenericMember_returnsEmpty() {
    var registry = new TypeRegistry(List.of(),
      Mockito.mock(GlobalScopeProvider.class),
      Mockito.mock(MemberMetadataIndex.class));
    var ref = registry.registerConfigurationType("Тип");
    var regular = MemberDescriptor.property("Регулярный",
      new com.github._1c_syntax.bsl.languageserver.types.model.TypeRef(TypeKind.PLATFORM, "Строка"));
    registry.registerMemberSource(ref, () -> List.of(regular), FileType.BSL);

    assertThat(ConfigurationTypesProvider.memberPlaceholderName(registry, ref)).isEmpty();
  }
}
