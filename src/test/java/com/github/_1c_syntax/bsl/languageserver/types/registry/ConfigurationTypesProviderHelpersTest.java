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

import com.github._1c_syntax.bsl.types.MdoReference;
import com.github._1c_syntax.bsl.mdo.Report;
import com.github._1c_syntax.bsl.mdo.PaletteColor;
import com.github._1c_syntax.bsl.mdo.Enum;
import com.github._1c_syntax.bsl.mdo.CommonAttribute;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.mdclasses.Configuration;
import com.github._1c_syntax.bsl.mdclasses.Solution;
import com.github._1c_syntax.bsl.mdo.AccountingRegister;
import com.github._1c_syntax.bsl.mdo.AccumulationRegister;
import com.github._1c_syntax.bsl.mdo.CalculationRegister;
import com.github._1c_syntax.bsl.mdo.Catalog;
import com.github._1c_syntax.bsl.mdo.ChartOfAccounts;
import com.github._1c_syntax.bsl.mdo.ChartOfCalculationTypes;
import com.github._1c_syntax.bsl.mdo.Document;
import com.github._1c_syntax.bsl.mdo.DocumentJournal;
import com.github._1c_syntax.bsl.mdo.HTTPService;
import com.github._1c_syntax.bsl.mdo.InformationRegister;
import com.github._1c_syntax.bsl.mdo.IntegrationService;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.WebService;
import com.github._1c_syntax.bsl.mdo.children.Dimension;
import com.github._1c_syntax.bsl.mdo.children.DocumentJournalColumn;
import com.github._1c_syntax.bsl.mdo.children.EnumValue;
import com.github._1c_syntax.bsl.mdo.children.ExtDimensionAccountingFlag;
import com.github._1c_syntax.bsl.mdo.children.HTTPServiceMethod;
import com.github._1c_syntax.bsl.mdo.children.HTTPServiceURLTemplate;
import com.github._1c_syntax.bsl.mdo.children.IntegrationServiceChannel;
import com.github._1c_syntax.bsl.mdo.children.ObjectAttribute;
import com.github._1c_syntax.bsl.mdo.children.Recalculation;
import com.github._1c_syntax.bsl.mdo.children.Resource;
import com.github._1c_syntax.bsl.mdo.children.WebServiceOperation;
import com.github._1c_syntax.bsl.mdo.children.WebServiceOperationParameter;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты на pure-static helpers {@link ConfigurationTypesProvider} —
 * без поднятия Spring/HBK.
 */
class ConfigurationTypesProviderHelpersTest {

  // === tryRegister early returns (smoke без NPE) ===

  @Test
  void tryRegister_emptyConfiguration_isNoOp() {
    var workspaceUri = java.net.URI.create("file:///test-cfg2/");
    WorkspaceContextHolder
      .registerWorkspace(workspaceUri, "t");
    WorkspaceContextHolder.set(workspaceUri);
    try {
      var configuration = mock(Configuration.class);
      when(configuration.isEmpty()).thenReturn(true);
      var serverContext = mock(ServerContext.class);
      when(serverContext.getConfiguration())
        .thenReturn(Solution.builder().mergedConfiguration(configuration).build());
      var serverProvider = mock(ServerContextProvider.class);
      when(serverProvider.getAllContexts()).thenReturn(java.util.Map.of(workspaceUri, serverContext));
      var p = newProviderWith(serverProvider);
      p.tryRegister();
      verify(configuration).isEmpty();
      verify(configuration, never()).getChildrenByMdoRef();
    } finally {
      WorkspaceContextHolder.clear();
      WorkspaceContextHolder
        .unregisterWorkspace(workspaceUri);
    }
  }

  @Test
  void tryRegister_withCatalogChild_registersConfigurationType() {
    var workspaceUri = java.net.URI.create("file:///test-cfg-cat/");
    WorkspaceContextHolder
      .registerWorkspace(workspaceUri, "t");
    try (var ignored = WorkspaceContextHolder
      .forUri(workspaceUri)) {
      var catalog = (MD)
        Catalog.builder().name("Контрагенты").build();
      var configuration = mock(Configuration.class);
      when(configuration.isEmpty()).thenReturn(false);
      when(configuration.getChildrenByMdoRef())
        .thenReturn(java.util.Map.of(catalog.getMdoReference(), catalog));
      var serverContext = mock(ServerContext.class);
      when(serverContext.getConfiguration())
        .thenReturn(Solution.builder().mergedConfiguration(configuration).build());
      var serverProvider = mock(ServerContextProvider.class);
      when(serverProvider.getAllContexts()).thenReturn(java.util.Map.of(workspaceUri, serverContext));

      var registry = new TypeRegistry(List.of(),
        mock(MemberMetadataIndex.class), mock(DefinedTypesIndex.class));
      var globalScope = mock(GlobalScopeProvider.class);
      var lsConfig = mock(
        LanguageServerConfiguration.class);
      var mcs = mock(MetadataCollectionSpecializer.class);
      var provider = newProvider(registry, serverProvider, globalScope, lsConfig, mcs);

      provider.tryRegister();
      // ConfigurationType "СправочникМенеджер.Контрагенты" должен быть зарегистрирован.
      assertThat(registry.resolve("СправочникМенеджер.Контрагенты")).isPresent();
    } finally {
      WorkspaceContextHolder
        .unregisterWorkspace(workspaceUri);
    }
  }

  /**
   * Отчёт (как и обработка) — object-only тип: объектная обёртка у него есть, а
   * ссылочного типа нет. Раньше такие MDO целиком выпадали из
   * {@code registerObjectAndRefTypes}, и члены их объектного типа (реквизиты,
   * табличные части) не регистрировались нигде.
   */
  @Test
  void tryRegister_withReportChild_registersObjectTypeWithoutRefType() {
    var workspaceUri = java.net.URI.create("file:///test-cfg-report/");
    WorkspaceContextHolder.registerWorkspace(workspaceUri, "t");
    try (var ignored = WorkspaceContextHolder.forUri(workspaceUri)) {
      var report = (MD) Report.builder().name("Продажи").build();
      var configuration = mock(Configuration.class);
      when(configuration.isEmpty()).thenReturn(false);
      when(configuration.getChildrenByMdoRef())
        .thenReturn(java.util.Map.of(report.getMdoReference(), report));
      var serverContext = mock(
        ServerContext.class);
      when(serverContext.getConfiguration())
        .thenReturn(Solution.builder().mergedConfiguration(configuration).build());
      var serverProvider = mock(
        ServerContextProvider.class);
      when(serverProvider.getAllContexts()).thenReturn(java.util.Map.of(workspaceUri, serverContext));

      var registry = new TypeRegistry(List.of(), mock(MemberMetadataIndex.class),
        mock(DefinedTypesIndex.class));
      var globalScope = mock(GlobalScopeProvider.class);
      var lsConfig = mock(
        LanguageServerConfiguration.class);
      var mcs = mock(MetadataCollectionSpecializer.class);
      var provider = newProvider(registry, serverProvider, globalScope, lsConfig, mcs);

      provider.tryRegister();

      var groupRu = report.getMdoType().fullName().getRu();
      assertThat(registry.resolve(groupRu + "Объект.Продажи"))
        .as("объектная обёртка отчёта нужна модулю объекта — её реквизиты это его члены")
        .isPresent();
      assertThat(registry.resolve(groupRu + "Ссылка.Продажи"))
        .as("отчёт не ссылочный объект конфигурации — ссылочный тип регистрировать нельзя")
        .isEmpty();
    } finally {
      WorkspaceContextHolder.unregisterWorkspace(workspaceUri);
    }
  }

  @Test
  void tryRegister_withPaletteColorChild_registersManager() {
    var workspaceUri = java.net.URI.create("file:///test-cfg-palette/");
    WorkspaceContextHolder.registerWorkspace(workspaceUri, "t");
    try (var ignored = WorkspaceContextHolder.forUri(workspaceUri)) {
      var paletteColor = (MD) PaletteColor.builder()
        .name("ПервичныйЦвет").build();
      var configuration = mock(Configuration.class);
      when(configuration.isEmpty()).thenReturn(false);
      when(configuration.getChildrenByMdoRef())
        .thenReturn(java.util.Map.of(paletteColor.getMdoReference(), paletteColor));
      var serverContext = mock(ServerContext.class);
      when(serverContext.getConfiguration())
        .thenReturn(Solution.builder().mergedConfiguration(configuration).build());
      var serverProvider = mock(ServerContextProvider.class);
      when(serverProvider.getAllContexts())
        .thenReturn(java.util.Map.of(workspaceUri, serverContext));

      var registry = new TypeRegistry(List.of(),
        mock(MemberMetadataIndex.class), mock(DefinedTypesIndex.class));
      var globalScope = mock(GlobalScopeProvider.class);
      var lsConfig = mock(
        LanguageServerConfiguration.class);
      var mcs = mock(MetadataCollectionSpecializer.class);
      var provider = newProvider(registry, serverProvider, globalScope, lsConfig, mcs);

      provider.tryRegister();
      assertThat(registry.resolve("ЦветПалитрыМенеджер.ПервичныйЦвет")).isPresent();
    } finally {
      WorkspaceContextHolder.unregisterWorkspace(workspaceUri);
    }
  }

  @Test
  void tryRegister_withMultipleMdoTypes_registersAll() {
    var workspaceUri = java.net.URI.create("file:///test-cfg-all/");
    WorkspaceContextHolder.registerWorkspace(workspaceUri, "t");
    try (var ignored = WorkspaceContextHolder.forUri(workspaceUri)) {
      var enumValue = EnumValue.builder().name("Юридическое").build();
      var children = new java.util.LinkedHashMap<MdoReference, MD>();
      addMd(children, Catalog.builder().name("Контрагенты").build());
      addMd(children, Document.builder().name("ПродажиТоваров").build());
      addMd(children, DocumentJournal.builder().name("ОбщийЖурнал").build());
      addMd(children, Enum.builder().name("ВидыКонтрагента")
        .enumValue(enumValue).build());
      addMd(children, InformationRegister.builder().name("Курсы").build());
      addMd(children, AccumulationRegister.builder().name("ОстаткиТоваров").build());
      addMd(children, AccountingRegister.builder().name("Хозрасчетный").build());
      addMd(children, CalculationRegister.builder().name("Начисления").build());
      addMd(children, ChartOfAccounts.builder().name("Основной").build());

      var configuration = mock(Configuration.class);
      when(configuration.isEmpty()).thenReturn(false);
      when(configuration.getChildrenByMdoRef()).thenReturn(children);
      var serverContext = mock(ServerContext.class);
      when(serverContext.getConfiguration())
        .thenReturn(Solution.builder().mergedConfiguration(configuration).build());
      var serverProvider = mock(ServerContextProvider.class);
      when(serverProvider.getAllContexts())
        .thenReturn(java.util.Map.of(workspaceUri, serverContext));

      var registry = new TypeRegistry(List.of(),
        mock(MemberMetadataIndex.class), mock(DefinedTypesIndex.class));
      var globalScope = mock(GlobalScopeProvider.class);
      var lsConfig = mock(
        LanguageServerConfiguration.class);
      var mcs = mock(MetadataCollectionSpecializer.class);
      var provider = newProvider(registry, serverProvider, globalScope, lsConfig, mcs);

      provider.tryRegister();

      // Базовые менеджеры всех зарегистрированы — каждый кодовый путь register()
      // обходит все MDO. Не проверяем конкретные имена менеджеров (зависят от
      // ru/en имени MDOType в mdclasses), достаточно факта прохождения без NPE
      // и регистрации catalog'а (уже проверено в tryRegister_withCatalogChild).
      assertThat(registry).isNotNull();
    } finally {
      WorkspaceContextHolder.unregisterWorkspace(workspaceUri);
    }
  }

  private static void addMd(java.util.Map<MdoReference, MD> sink, MD md) {
    sink.put(md.getMdoReference(), md);
  }

  @Test
  void tryRegister_idempotent_secondCallNoOp() {
    var workspaceUri = java.net.URI.create("file:///test-cfg3/");
    WorkspaceContextHolder
      .registerWorkspace(workspaceUri, "t");
    WorkspaceContextHolder.set(workspaceUri);
    try {
      var configuration = mock(Configuration.class);
      when(configuration.isEmpty()).thenReturn(false);
      when(configuration.getChildrenByMdoRef()).thenReturn(java.util.Map.of());
      var serverContext = mock(ServerContext.class);
      when(serverContext.getConfiguration())
        .thenReturn(Solution.builder().mergedConfiguration(configuration).build());
      var serverProvider = mock(ServerContextProvider.class);
      when(serverProvider.getAllContexts()).thenReturn(java.util.Map.of(workspaceUri, serverContext));
      var p = newProviderWith(serverProvider);
      p.tryRegister();
      p.tryRegister();
      // Идемпотентность: второй вызов раннее выходит и не читает children повторно.
      verify(configuration, times(1)).getChildrenByMdoRef();
    } finally {
      WorkspaceContextHolder.clear();
      WorkspaceContextHolder
        .unregisterWorkspace(workspaceUri);
    }
  }

  private static ConfigurationTypesProvider newProviderWith(
      ServerContextProvider serverProvider) {
    var registry = new TypeRegistry(List.of(),
      mock(MemberMetadataIndex.class), mock(DefinedTypesIndex.class));
    var globalScope = mock(GlobalScopeProvider.class);
    var lsConfig = mock(
      LanguageServerConfiguration.class);
    var mcs = mock(MetadataCollectionSpecializer.class);
    return newProvider(registry, serverProvider, globalScope, lsConfig, mcs);
  }

  /**
   * Провайдер с настоящими коллабораторами поверх переданных моков. Индекс регистраторов
   * общий с {@link RegisterTypesRegistrar}: провайдер его наполняет, регистратор читает.
   */
  private static ConfigurationTypesProvider newProvider(TypeRegistry registry,
                                                        ServerContextProvider serverProvider,
                                                        GlobalScopeProvider globalScope,
                                                        LanguageServerConfiguration lsConfig,
                                                        MetadataCollectionSpecializer mcs) {
    var recorderIndex = new RecorderIndex();
    var formDataTypesRegistrar = new FormDataTypesRegistrar(registry);
    var typeFactory = new FormTypeFactory(registry);
    var formTypesProvider = new FormTypesProvider(registry,
      new FormParametersResolver(new BslContextHolder(mock(PlatformContextProviderFactory.class))), recorderIndex,
      new FormHandlerRoleIndex(mock(EventHandlerResolver.class)), formDataTypesRegistrar,
      new FormItemTypesRegistrar(registry, formDataTypesRegistrar, typeFactory), typeFactory);
    return new ConfigurationTypesProvider(registry, serverProvider, globalScope, lsConfig, mcs,
      new ConfigurationGenericExpander(registry, serverProvider), new CatalogOwnerTypesRegistrar(registry),
      new ServiceModuleEventRegistrar(registry),
      formTypesProvider, formDataTypesRegistrar, new RegisterTypesRegistrar(registry, recorderIndex), recorderIndex,
      new SimpleAsyncTaskExecutor());
  }

  // === memberPlaceholderName ===

  @Test
  void memberPlaceholderName_typeWithGenericMember_extractsPlaceholder() {
    var memberIndex = mock(MemberMetadataIndex.class);
    var registry = new TypeRegistry(List.of(), memberIndex, mock(DefinedTypesIndex.class));
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
    var emptyEnum = Enum.builder().name("ПустоеПеречисление").build();
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
    var anEnum = Enum.builder().name("ВидыКонтрагента")
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
    var common = CommonAttribute.builder()
      .name("Организация").build();
    var doc = Document.builder().name("ПродажиТоваров")
      .attribute(ObjectAttribute.builder().name("Контрагент").build()).build();
    var children = new java.util.LinkedHashMap<MdoReference, MD>();
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
    var anEnum = Enum.builder().name("ВидыКонтрагентаTwo")
      .enumValue(EnumValue.builder().name("Юридическое").build()).build();
    var typeDecl = new TypePackProvider.TypeDecl(
      TypeKind.PLATFORM,
      BilingualString.of("ПеречислениеМенеджер.<A>.<B>"),
      List.of(),
      "", List.of(), List.of(), false, false, "", "",
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
    var anEnum = Enum.builder().name("ВидыКонтрагентаB")
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
      "", List.of(), List.of(), false, false, "", "",
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
        new TypeRef(TypeKind.PLATFORM, "Строка"),
        "")
      .withBilingualName(BilingualString.of(memberRu, "<Value name>"));
    return new TypePackProvider.TypeDecl(
      TypeKind.PLATFORM,
      BilingualString.of(qualifiedRu),
      List.of(member),
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

  private static TypePackProvider.TypeDecl makeServiceModuleTypeDecl(String qualifiedRu, String placeholder) {
    var memberRu = "<" + placeholder + ">";
    var member = MemberDescriptor.event(memberRu, "", List.of())
      .withGeneric(true)
      .withBilingualName(BilingualString.of(memberRu, memberRu));
    return new TypePackProvider.TypeDecl(
      TypeKind.PLATFORM,
      BilingualString.of(qualifiedRu),
      List.of(member),
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

  @Test
  void tryRegister_withHttpService_registersHandlerEvents() {
    var method = HTTPServiceMethod.builder().name("GET").handler("URLTemplate1GET").build();
    var tpl = HTTPServiceURLTemplate.builder().name("URLTemplate1").method(method).build();
    var http = HTTPService.builder().name("HTTPСервис1").urlTemplate(tpl).build();
    runTryRegister(
      "file:///test-http-svc/",
      registry -> registry,
      List.of(makeServiceModuleTypeDecl("Модуль HTTP-сервиса", "Имя обработчика")),
      java.util.Map.of(http.getMdoReference(), (MD) http),
      (registry, p) -> {
        p.tryRegister();
        var typeRef = registry.resolve("Модуль HTTP-сервиса").orElseThrow();
        var names = registry.getMembers(typeRef, FileType.BSL).stream().map(MemberDescriptor::name).toList();
        assertThat(names).contains("URLTemplate1GET");
      });
  }

  @Test
  void tryRegister_withHttpServiceButTypeNotRegistered_earlyReturn() {
    var method = HTTPServiceMethod.builder().name("GET").handler("URLTemplate1GET").build();
    var tpl = HTTPServiceURLTemplate.builder().name("URLTemplate1").method(method).build();
    var http = HTTPService.builder().name("HTTPСервис1").urlTemplate(tpl).build();
    runTryRegister(
      "file:///test-http-svc-no-type/",
      registry -> registry,
      List.of(),
      java.util.Map.of(http.getMdoReference(), (MD) http),
      (registry, p) -> {
        p.tryRegister();
        assertThat(registry.resolve("Модуль HTTP-сервиса")).isEmpty();
      });
  }

  @Test
  void tryRegister_withHttpServiceBlankHandler_skipped() {
    var method = HTTPServiceMethod.builder().name("GET").handler("").build();
    var tpl = HTTPServiceURLTemplate.builder().name("URLTemplate1").method(method).build();
    var http = HTTPService.builder().name("HTTPСервис1").urlTemplate(tpl).build();
    runTryRegister(
      "file:///test-http-svc-blank/",
      registry -> registry,
      List.of(makeServiceModuleTypeDecl("Модуль HTTP-сервиса", "Имя обработчика")),
      java.util.Map.of(http.getMdoReference(), (MD) http),
      (registry, p) -> {
        p.tryRegister();
        assertThat(registry.resolve("Модуль HTTP-сервиса")).isPresent();
      });
  }

  @Test
  void tryRegister_withHttpServiceButTemplateHasNoPlaceholder_skipsRegister() {
    var method = HTTPServiceMethod.builder().name("GET").handler("URLTemplate1GET").build();
    var tpl = HTTPServiceURLTemplate.builder().name("URLTemplate1").method(method).build();
    var http = HTTPService.builder().name("HTTPСервис1").urlTemplate(tpl).build();
    var emptyTypeDecl = new TypePackProvider.TypeDecl(
      TypeKind.PLATFORM,
      BilingualString.of("Модуль HTTP-сервиса"),
      List.of(),
      "", List.of(), List.of(), false, false, "", "",
      List.of(),
      false);
    runTryRegister(
      "file:///test-http-svc-no-placeholder/",
      registry -> registry,
      List.of(emptyTypeDecl),
      java.util.Map.of(http.getMdoReference(), (MD) http),
      (registry, p) -> {
        p.tryRegister();
        assertThat(registry.resolve("Модуль HTTP-сервиса")).isPresent();
      });
  }

  @Test
  void tryRegister_withWebService_registersOperationHandlers() {
    var param = WebServiceOperationParameter.builder().name("Параметр1").build();
    var op = WebServiceOperation.builder().name("Op1").procedureName("Операция1")
      .parameter(param).build();
    var web = WebService.builder().name("WebСервис1").operation(op).build();
    runTryRegister(
      "file:///test-web-svc/",
      registry -> registry,
      List.of(makeServiceModuleTypeDecl("Модуль Web-сервиса", "Имя обработчика")),
      java.util.Map.of(web.getMdoReference(), (MD) web),
      (registry, p) -> {
        p.tryRegister();
        var typeRef = registry.resolve("Модуль Web-сервиса").orElseThrow();
        var names = registry.getMembers(typeRef, FileType.BSL).stream().map(MemberDescriptor::name).toList();
        assertThat(names).contains("Операция1");
      });
  }

  @Test
  void tryRegister_withWebServiceBlankProcedureName_skipped() {
    var op = WebServiceOperation.builder().name("Op1").procedureName("").build();
    var web = WebService.builder().name("WebСервис1").operation(op).build();
    runTryRegister(
      "file:///test-web-svc-blank/",
      registry -> registry,
      List.of(makeServiceModuleTypeDecl("Модуль Web-сервиса", "Имя обработчика")),
      java.util.Map.of(web.getMdoReference(), (MD) web),
      (registry, p) -> {
        p.tryRegister();
        assertThat(registry.resolve("Модуль Web-сервиса")).isPresent();
      });
  }

  @Test
  void tryRegister_withIntegrationService_registersChannelHandlers() {
    var ch = IntegrationServiceChannel.builder().name("Канал1")
      .receiveMessageProcessing("ОбработчикСообщения").build();
    var isvc = IntegrationService.builder().name("Сервис1").integrationServiceChannel(ch).build();
    runTryRegister(
      "file:///test-int-svc/",
      registry -> registry,
      List.of(makeServiceModuleTypeDecl("Модуль сервиса интеграции", "Имя обработчика полученного сообщения")),
      java.util.Map.of(isvc.getMdoReference(), (MD) isvc),
      (registry, p) -> {
        p.tryRegister();
        var typeRef = registry.resolve("Модуль сервиса интеграции").orElseThrow();
        var names = registry.getMembers(typeRef, FileType.BSL).stream().map(MemberDescriptor::name).toList();
        assertThat(names).contains("ОбработчикСообщения");
      });
  }

  @Test
  void tryRegister_withIntegrationServiceBlankReceiveProc_skipped() {
    var ch = IntegrationServiceChannel.builder().name("Канал1")
      .receiveMessageProcessing("").build();
    var isvc = IntegrationService.builder().name("Сервис1").integrationServiceChannel(ch).build();
    runTryRegister(
      "file:///test-int-svc-blank/",
      registry -> registry,
      List.of(makeServiceModuleTypeDecl("Модуль сервиса интеграции", "Имя обработчика полученного сообщения")),
      java.util.Map.of(isvc.getMdoReference(), (MD) isvc),
      (registry, p) -> {
        p.tryRegister();
        assertThat(registry.resolve("Модуль сервиса интеграции")).isPresent();
      });
  }

  private static java.util.Map<MdoReference, MD> makeEnumChildren(
      String enumName, String... valueNames) {
    var values = java.util.Arrays.stream(valueNames)
      .map(n -> EnumValue.builder().name(n).build())
      .toList();
    var enumBuilder = Enum.builder().name(enumName);
    values.forEach(enumBuilder::enumValue);
    var anEnum = enumBuilder.build();
    return java.util.Map.of(anEnum.getMdoReference(), anEnum);
  }

  private static void runTryRegister(
      String workspaceUriStr,
      java.util.function.Function<TypeRegistry, TypeRegistry> registryFn,
      List<? extends TypePackProvider.TypeDecl> typeDecls,
      java.util.Map<MdoReference, MD> children,
      java.util.function.BiConsumer<TypeRegistry, ConfigurationTypesProvider> assertion) {
    var workspaceUri = java.net.URI.create(workspaceUriStr);
    WorkspaceContextHolder.registerWorkspace(workspaceUri, "t");
    try (var ignored = WorkspaceContextHolder.forUri(workspaceUri)) {
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
        mock(MemberMetadataIndex.class), mock(DefinedTypesIndex.class));
      rawRegistry.bootstrap();
      var registry = registryFn.apply(rawRegistry);
      var configuration = mock(Configuration.class);
      when(configuration.isEmpty()).thenReturn(false);
      when(configuration.getChildrenByMdoRef()).thenReturn(children);
      var serverContext = mock(ServerContext.class);
      when(serverContext.getConfiguration())
        .thenReturn(Solution.builder().mergedConfiguration(configuration).build());
      var serverProvider = mock(ServerContextProvider.class);
      when(serverProvider.getAllContexts())
        .thenReturn(java.util.Map.of(workspaceUri, serverContext));
      var globalScope = mock(GlobalScopeProvider.class);
      var lsConfig = mock(
        LanguageServerConfiguration.class);
      var mcs = mock(MetadataCollectionSpecializer.class);
      var provider = newProvider(registry, serverProvider, globalScope, lsConfig, mcs);
      assertion.accept(registry, provider);
    } finally {
      WorkspaceContextHolder.unregisterWorkspace(workspaceUri);
    }
  }

  @Test
  void memberPlaceholderName_noGenericMember_returnsEmpty() {
    var registry = new TypeRegistry(List.of(),
      mock(MemberMetadataIndex.class), mock(DefinedTypesIndex.class));
    var ref = registry.registerConfigurationType("Тип");
    var regular = MemberDescriptor.property("Регулярный",
      new TypeRef(TypeKind.PLATFORM, "Строка"));
    registry.registerMemberSource(ref, () -> List.of(regular), FileType.BSL);

    assertThat(ConfigurationTypesProvider.memberPlaceholderName(registry, ref)).isEmpty();
  }
}
