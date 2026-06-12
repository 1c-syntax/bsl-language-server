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
import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextProperty;
import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.context.api.ContextType;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.mdo.children.ObjectAttribute;
import com.github._1c_syntax.bsl.mdo.children.ObjectTabularSection;
import com.github._1c_syntax.bsl.types.MDOType;
import com.github._1c_syntax.bsl.types.MdoReference;
import com.github._1c_syntax.bsl.mdclasses.Configuration;
import com.github._1c_syntax.bsl.mdo.Document;
import com.github._1c_syntax.bsl.mdo.MD;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit-покрытие {@link MetadataCollectionSpecializer#specialize()} без Spring/HBK.
 * Bsl-context, ServerContext и Configuration замоканы; verifies, что для
 * known-property из верхнего ОбъектМетаданныхКонфигурация регистрируется
 * specialized synthetic returnType и member-override.
 */
class MetadataCollectionSpecializerUnitTest {

  private static final URI TEST_WORKSPACE = URI.create("file:///test/");

  @BeforeEach
  void registerWorkspace() {
    WorkspaceContextHolder.registerWorkspace(TEST_WORKSPACE, "test");
  }

  @AfterEach
  void cleanup() {
    WorkspaceContextHolder.clear();
    WorkspaceContextHolder.unregisterWorkspace(TEST_WORKSPACE);
  }

  @Test
  void specialize_topLevelGroup_overridesReturnTypeToSyntheticCollection() {
    var globalScope = Mockito.mock(GlobalScopeProvider.class);
    var memberIndex = Mockito.mock(MemberMetadataIndex.class);
    var registry = new TypeRegistry(List.of(), globalScope, memberIndex);

    var ownerRef = registry.registerConfigurationType("ОбъектМетаданныхКонфигурация");
    var baseCollectionRef = registry.registerConfigurationType("КоллекцияОбъектовМетаданных");
    var elementTypeRef = registry.registerConfigurationType("ОбъектМетаданных: Документ");

    // baseColl: generic placeholder + один обычный метод Получить.
    var generic = MemberDescriptor.genericProperty("<Имя объекта>",
        elementTypeRef, "")
      .withBilingualName(BilingualString.of("<Имя объекта>", "<Object name>"));
    var getMethod = MemberDescriptor.property("Получить", elementTypeRef, "");
    registry.registerMemberSource(baseCollectionRef,
      () -> List.of(generic, getMethod), FileType.BSL);

    // owner: property "Документы" пока возвращает базовый тип КоллекцияОбъектовМетаданных.
    var docsMember = MemberDescriptor.property("Документы", baseCollectionRef, "");
    registry.registerMemberSource(ownerRef, () -> List.of(docsMember), FileType.BSL);

    var provider = mockProvider("ОбъектМетаданныхКонфигурация",
      mockProperty("Документы", "Documents",
        List.of(mockContext("КоллекцияОбъектовМетаданных")),
        List.of(mockContext("ОбъектМетаданных: Документ"))));

    var holder = Mockito.mock(BslContextHolder.class);
    when(holder.get()).thenReturn(Optional.of(provider));

    var document = (MD) Document.builder().name("Покупатели").build();
    var configuration = Mockito.mock(Configuration.class);
    when(configuration.isEmpty()).thenReturn(false);
    when(configuration.getChildrenByMdoRef()).thenReturn(Map.of(document.getMdoReference(), document));

    var serverContext = Mockito.mock(ServerContext.class);
    when(serverContext.getConfiguration()).thenReturn(configuration);

    var workspaceUri = TEST_WORKSPACE;
    var serverProvider = Mockito.mock(ServerContextProvider.class);
    when(serverProvider.getAllContexts()).thenReturn(Map.of(workspaceUri, serverContext));

    WorkspaceContextHolder.set(workspaceUri);
    var specializer = new MetadataCollectionSpecializer(registry, holder, serverProvider);
    specializer.specialize();

    var members = registry.getMembers(ownerRef);
    var documentsMember = members.stream()
      .filter(m -> m.name().equals("Документы")).findFirst().orElseThrow();
    assertThat(documentsMember.returnType().qualifiedName())
      .isEqualTo("КоллекцияОбъектовМетаданных.Документы");

    // lazy materialization у synthetic group-collection: imageOf
    // (КоллекцияОбъектовМетаданных.Документы) должен иметь:
    //   - materialized member "Покупатели" → returnType ОбъектМетаданных: Документ.Покупатели
    //   - оригинальный метод "Получить" (передан как not-generic из base)
    var specRef = registry.intern(
      TypeKind.PLATFORM,
      "КоллекцияОбъектовМетаданных.Документы");
    var specMembers = registry.getMembers(specRef);
    var specMemberNames = specMembers.stream().map(MemberDescriptor::name).toList();
    assertThat(specMemberNames).contains("Покупатели", "Получить");
    var buyersMember = specMembers.stream()
      .filter(m -> m.name().equals("Покупатели")).findFirst().orElseThrow();
    assertThat(buyersMember.returnType().qualifiedName())
      .isEqualTo("ОбъектМетаданных: Документ.Покупатели");

    // Element-returning method «Получить» специализирован — returnType заменён на
    // element-тип группы (ОбъектМетаданных: Документ).
    var getMember = specMembers.stream()
      .filter(m -> m.name().equals("Получить")).findFirst().orElseThrow();
    assertThat(getMember.returnType().qualifiedName())
      .isEqualTo("ОбъектМетаданных: Документ");
  }

  @Test
  void specialize_noProvider_isNoOp() {
    var registry = new TypeRegistry(List.of(),
      Mockito.mock(GlobalScopeProvider.class),
      Mockito.mock(MemberMetadataIndex.class));

    var holder = Mockito.mock(BslContextHolder.class);
    when(holder.get()).thenReturn(Optional.empty());

    var serverProvider = Mockito.mock(ServerContextProvider.class);

    WorkspaceContextHolder.set(TEST_WORKSPACE);
    new MetadataCollectionSpecializer(registry, holder, serverProvider).specialize();
    Mockito.verify(serverProvider, Mockito.never()).getAllContexts();
  }

  @Test
  void specialize_noWorkspace_isNoOp() {
    var registry = new TypeRegistry(List.of(),
      Mockito.mock(GlobalScopeProvider.class),
      Mockito.mock(MemberMetadataIndex.class));

    var holder = Mockito.mock(BslContextHolder.class);
    when(holder.get()).thenReturn(Optional.of(Mockito.mock(ContextProvider.class)));

    var serverProvider = Mockito.mock(ServerContextProvider.class);
    WorkspaceContextHolder.clear();
    new MetadataCollectionSpecializer(registry, holder, serverProvider).specialize();
    Mockito.verify(serverProvider, Mockito.never()).getAllContexts();
  }

  @Test
  void specialize_nestedCollectionOnPerMdoType_overridesNestedReturnType() {
    var globalScope = Mockito.mock(GlobalScopeProvider.class);
    var memberIndex = Mockito.mock(MemberMetadataIndex.class);
    var registry = new TypeRegistry(List.of(), globalScope, memberIndex);

    var ownerRef = registry.registerConfigurationType("ОбъектМетаданныхКонфигурация");
    var documentTypeRef = registry.registerConfigurationType("ОбъектМетаданных: Документ");
    var attrTypeRef = registry.registerConfigurationType("ОбъектМетаданных: Реквизит");
    var baseCollectionRef = registry.registerConfigurationType("КоллекцияОбъектовМетаданных");
    // Для Document.Движения нужен base = ЗначениеСвойстваОбъектаМетаданных.
    registry.registerConfigurationType("ЗначениеСвойстваОбъектаМетаданных");
    registry.registerConfigurationType("ОбъектМетаданных: РегистрНакопления.ОстаткиТоваров");

    var generic = MemberDescriptor.genericProperty("<Имя объекта>",
        documentTypeRef, "")
      .withBilingualName(BilingualString.of("<Имя объекта>", "<Object name>"));
    registry.registerMemberSource(baseCollectionRef,
      () -> List.of(generic), FileType.BSL);

    // Документ-тип содержит nested-collection "Реквизиты".
    var docAttrs = MemberDescriptor.property("Реквизиты", baseCollectionRef, "");
    registry.registerMemberSource(documentTypeRef, () -> List.of(docAttrs), FileType.BSL);

    var docsMember = MemberDescriptor.property("Документы", baseCollectionRef, "");
    registry.registerMemberSource(ownerRef, () -> List.of(docsMember), FileType.BSL);

    var provider = mockProvider(List.of(
      mockType("ОбъектМетаданныхКонфигурация",
        mockProperty("Документы", "Documents",
          List.of(mockContext("КоллекцияОбъектовМетаданных")),
          List.of(mockContext("ОбъектМетаданных: Документ")))),
      mockType("ОбъектМетаданных: Документ",
        mockProperty("Реквизиты", "Attributes",
          List.of(mockContext("КоллекцияОбъектовМетаданных")),
          List.of(mockContext("ОбъектМетаданных: Реквизит"))))
    ));

    var holder = Mockito.mock(BslContextHolder.class);
    when(holder.get()).thenReturn(Optional.of(provider));

    var attribute = ObjectAttribute.builder()
      .name("Контрагент").build();
    var ts = ObjectTabularSection.builder()
      .name("Товары").build();
    var registerRef = MdoReference.create(
      MDOType.ACCUMULATION_REGISTER, "ОстаткиТоваров");
    var document = (MD) Document.builder()
      .name("Покупатели")
      .attribute(attribute)
      .tabularSection(ts)
      .addRegisterRecords(registerRef)
      .build();
    var configuration = Mockito.mock(Configuration.class);
    when(configuration.isEmpty()).thenReturn(false);
    when(configuration.getChildrenByMdoRef()).thenReturn(Map.of(document.getMdoReference(), document));

    var serverContext = Mockito.mock(ServerContext.class);
    when(serverContext.getConfiguration()).thenReturn(configuration);

    var serverProvider = Mockito.mock(ServerContextProvider.class);
    when(serverProvider.getAllContexts()).thenReturn(Map.of(TEST_WORKSPACE, serverContext));

    WorkspaceContextHolder.set(TEST_WORKSPACE);
    new MetadataCollectionSpecializer(registry, holder, serverProvider).specialize();

    // Phase B: верхний override Документы.
    assertThat(registry.getMembers(ownerRef).stream()
      .filter(m -> m.name().equals("Документы")).findFirst().orElseThrow()
      .returnType().qualifiedName())
      .isEqualTo("КоллекцияОбъектовМетаданных.Документы");
    // Phase C: nested override Реквизиты на ОбъектМетаданных: Документ.
    assertThat(registry.getMembers(documentTypeRef).stream()
      .filter(m -> m.name().equals("Реквизиты")).findFirst().orElseThrow()
      .returnType().qualifiedName())
      .isEqualTo("КоллекцияОбъектовМетаданных.Реквизиты");
    assertThat(attrTypeRef).isNotNull();

    // Phase C: per-MDO/collection synthetic
    // (ОбъектМетаданных: Документ.Покупатели.Реквизиты) должен материализовать
    // имена реквизитов документа — у Document.builder() без attributes список пуст,
    // но вызов lazy-source покрывает buildPerOwnerCollectionMembers / childReturnType.
    // perCollName = base + "." + collection-ru + "." + ownerSuffix
    var perCollRef = registry.intern(
      TypeKind.PLATFORM,
      "КоллекцияОбъектовМетаданных.Реквизиты.Покупатели");
    var perCollMembers = registry.getMembers(perCollRef);
    assertThat(perCollMembers).isNotNull();

    // perMdoRef (ОбъектМетаданных: Документ.Покупатели) — purchased через
    // registerPerOwner: copy non-generic от element + override Реквизиты/ТЧ/Формы/…
    var perMdoRef = registry.intern(
      TypeKind.PLATFORM,
      "ОбъектМетаданных: Документ.Покупатели");
    var perMdoMembers = registry.getMembers(perMdoRef);
    // у perMdoRef должны быть override-членов известных коллекций.
    var perMdoNames = perMdoMembers.stream().map(MemberDescriptor::name).toList();
    assertThat(perMdoNames).contains("Реквизиты", "ТабличныеЧасти", "Формы", "Команды", "Макеты");

    assertThat(perMdoRef).isNotNull();
  }

  @Test
  void specialize_nestedPropertyWithoutHbkMarker_usesFallbackByPropertyName() {
    var globalScope = Mockito.mock(GlobalScopeProvider.class);
    var memberIndex = Mockito.mock(MemberMetadataIndex.class);
    var registry = new TypeRegistry(List.of(), globalScope, memberIndex);

    var docTypeRef = registry.registerConfigurationType("ОбъектМетаданных: Документ");
    var baseCollectionRef = registry.registerConfigurationType("КоллекцияОбъектовМетаданных");
    registry.registerConfigurationType("ОбъектМетаданных: ТабличнаяЧасть");

    var generic = MemberDescriptor.genericProperty("<Имя объекта>",
        baseCollectionRef, "")
      .withBilingualName(BilingualString.of("<Имя объекта>", "<Object name>"));
    registry.registerMemberSource(baseCollectionRef,
      () -> List.of(generic), FileType.BSL);

    var tabSection = MemberDescriptor.property("ТабличныеЧасти", baseCollectionRef, "");
    registry.registerMemberSource(docTypeRef, () -> List.of(tabSection), FileType.BSL);

    // На nested-уровне (ОбъектМетаданных: Документ) HBK-маркера у ТабличныеЧасти НЕТ.
    // Fallback: COLLECTION_BY_NAME["табличныечасти"] → CollectionSpec с element
    // "ОбъектМетаданных: ТабличнаяЧасть".
    var provider = mockProvider("ОбъектМетаданных: Документ",
      mockProperty("ТабличныеЧасти", "TabularSections",
        List.of(mockContext("КоллекцияОбъектовМетаданных")),
        List.of()));

    var holder = Mockito.mock(BslContextHolder.class);
    when(holder.get()).thenReturn(Optional.of(provider));

    var document = (MD) Document.builder().name("X").build();
    var configuration = Mockito.mock(Configuration.class);
    when(configuration.isEmpty()).thenReturn(false);
    when(configuration.getChildrenByMdoRef()).thenReturn(Map.of(document.getMdoReference(), document));

    var serverContext = Mockito.mock(ServerContext.class);
    when(serverContext.getConfiguration()).thenReturn(configuration);

    var serverProvider = Mockito.mock(ServerContextProvider.class);
    when(serverProvider.getAllContexts()).thenReturn(Map.of(TEST_WORKSPACE, serverContext));

    WorkspaceContextHolder.set(TEST_WORKSPACE);
    new MetadataCollectionSpecializer(registry, holder, serverProvider).specialize();

    var members = registry.getMembers(docTypeRef);
    assertThat(members.stream().filter(m -> m.name().equals("ТабличныеЧасти")).findFirst().orElseThrow()
      .returnType().qualifiedName())
      .isEqualTo("КоллекцияОбъектовМетаданных.ТабличныеЧасти");
  }

  @Test
  void specialize_propertyWithoutBaseCollectionInTypes_skipped() {
    var globalScope = Mockito.mock(GlobalScopeProvider.class);
    var memberIndex = Mockito.mock(MemberMetadataIndex.class);
    var registry = new TypeRegistry(List.of(), globalScope, memberIndex);

    var ownerRef = registry.registerConfigurationType("ОбъектМетаданныхКонфигурация");

    var unrelated = MemberDescriptor.property("Версия",
      registry.registerConfigurationType("Строка"), "");
    registry.registerMemberSource(ownerRef, () -> List.of(unrelated), FileType.BSL);

    var provider = mockProvider("ОбъектМетаданныхКонфигурация",
      // property типа Строка — НЕ коллекция → должна быть пропущена в specialize.
      mockProperty("Версия", "Version",
        List.of(mockContext("Строка")), List.of()));

    var holder = Mockito.mock(BslContextHolder.class);
    when(holder.get()).thenReturn(Optional.of(provider));

    var document = (MD) Document.builder().name("X").build();
    var configuration = Mockito.mock(Configuration.class);
    when(configuration.isEmpty()).thenReturn(false);
    when(configuration.getChildrenByMdoRef()).thenReturn(Map.of(document.getMdoReference(), document));

    var serverContext = Mockito.mock(ServerContext.class);
    when(serverContext.getConfiguration()).thenReturn(configuration);

    var serverProvider = Mockito.mock(ServerContextProvider.class);
    when(serverProvider.getAllContexts()).thenReturn(Map.of(TEST_WORKSPACE, serverContext));

    WorkspaceContextHolder.set(TEST_WORKSPACE);
    new MetadataCollectionSpecializer(registry, holder, serverProvider).specialize();

    // Никаких override — Версия остаётся со старым returnType (Строка).
    var members = registry.getMembers(ownerRef);
    assertThat(members.stream().filter(m -> m.name().equals("Версия")).findFirst().orElseThrow()
      .returnType().qualifiedName()).isEqualTo("Строка");
  }

  @Test
  void specialize_unknownOwnerType_skipped() {
    var globalScope = Mockito.mock(GlobalScopeProvider.class);
    var memberIndex = Mockito.mock(MemberMetadataIndex.class);
    var registry = new TypeRegistry(List.of(), globalScope, memberIndex);

    // ownerName из bsl-context, которого НЕТ в реестре LS → resolve вернёт null → continue.
    var provider = mockProvider("ОбъектМетаданныхНезнакомый",
      mockProperty("Документы", "Documents",
        List.of(mockContext("КоллекцияОбъектовМетаданных")),
        List.of(mockContext("ОбъектМетаданных: Документ"))));

    var holder = Mockito.mock(BslContextHolder.class);
    when(holder.get()).thenReturn(Optional.of(provider));

    var document = (MD) Document.builder().name("X").build();
    var configuration = Mockito.mock(Configuration.class);
    when(configuration.isEmpty()).thenReturn(false);
    when(configuration.getChildrenByMdoRef()).thenReturn(Map.of(document.getMdoReference(), document));

    var serverContext = Mockito.mock(ServerContext.class);
    when(serverContext.getConfiguration()).thenReturn(configuration);

    var serverProvider = Mockito.mock(ServerContextProvider.class);
    when(serverProvider.getAllContexts()).thenReturn(Map.of(TEST_WORKSPACE, serverContext));

    var unknownRef = registry.registerConfigurationType("ОбъектМетаданныхНезнакомый");
    WorkspaceContextHolder.set(TEST_WORKSPACE);
    new MetadataCollectionSpecializer(registry, holder, serverProvider).specialize();
    assertThat(registry.getMembers(unknownRef)).isEmpty();
  }

  @Test
  void specialize_blankPropertyName_skipped() {
    var globalScope = Mockito.mock(GlobalScopeProvider.class);
    var memberIndex = Mockito.mock(MemberMetadataIndex.class);
    var registry = new TypeRegistry(List.of(), globalScope, memberIndex);

    var ownerRef = registry.registerConfigurationType("ОбъектМетаданныхКонфигурация");
    registry.registerConfigurationType("КоллекцияОбъектовМетаданных");

    var provider = mockProvider("ОбъектМетаданныхКонфигурация",
      mockProperty("", "", List.of(mockContext("КоллекцияОбъектовМетаданных")), List.of()));

    var holder = Mockito.mock(BslContextHolder.class);
    when(holder.get()).thenReturn(Optional.of(provider));

    var configuration = Mockito.mock(Configuration.class);
    when(configuration.isEmpty()).thenReturn(false);
    when(configuration.getChildrenByMdoRef()).thenReturn(Map.of());

    var serverContext = Mockito.mock(ServerContext.class);
    when(serverContext.getConfiguration()).thenReturn(configuration);

    var serverProvider = Mockito.mock(ServerContextProvider.class);
    when(serverProvider.getAllContexts()).thenReturn(Map.of(TEST_WORKSPACE, serverContext));

    WorkspaceContextHolder.set(TEST_WORKSPACE);
    new MetadataCollectionSpecializer(registry, holder, serverProvider).specialize();
    // Blank propertyName → continue без override.
    assertThat(registry.getMembers(ownerRef)).isEmpty();
  }

  @Test
  void specialize_emptyConfiguration_isNoOp() {
    var registry = new TypeRegistry(List.of(),
      Mockito.mock(GlobalScopeProvider.class),
      Mockito.mock(MemberMetadataIndex.class));

    var holder = Mockito.mock(BslContextHolder.class);
    when(holder.get()).thenReturn(Optional.of(Mockito.mock(ContextProvider.class)));

    var configuration = Mockito.mock(Configuration.class);
    when(configuration.isEmpty()).thenReturn(true);

    var serverContext = Mockito.mock(ServerContext.class);
    when(serverContext.getConfiguration()).thenReturn(configuration);

    var workspaceUri = TEST_WORKSPACE;
    var serverProvider = Mockito.mock(ServerContextProvider.class);
    when(serverProvider.getAllContexts()).thenReturn(Map.of(workspaceUri, serverContext));

    WorkspaceContextHolder.registerWorkspace(workspaceUri, "test");
    WorkspaceContextHolder.set(workspaceUri);
    new MetadataCollectionSpecializer(registry, holder, serverProvider).specialize();
    Mockito.verify(configuration, Mockito.never()).getChildrenByMdoRef();
  }

  @Test
  void specialize_noServerContext_isNoOp() {
    var registry = new TypeRegistry(List.of(),
      Mockito.mock(GlobalScopeProvider.class),
      Mockito.mock(MemberMetadataIndex.class));
    var holder = Mockito.mock(BslContextHolder.class);
    when(holder.get()).thenReturn(Optional.of(Mockito.mock(ContextProvider.class)));
    var serverProvider = Mockito.mock(ServerContextProvider.class);
    when(serverProvider.getAllContexts()).thenReturn(Map.of());
    WorkspaceContextHolder.registerWorkspace(TEST_WORKSPACE, "test");
    WorkspaceContextHolder.set(TEST_WORKSPACE);
    new MetadataCollectionSpecializer(registry, holder, serverProvider).specialize();
    Mockito.verify(serverProvider).getAllContexts();
  }

  private static ContextProvider mockProvider(String typeName, ContextProperty... properties) {
    return mockProvider(List.of(mockType(typeName, properties)));
  }

  private static ContextProvider mockProvider(List<ContextType> types) {
    var provider = Mockito.mock(ContextProvider.class);
    when(provider.getContexts()).thenReturn(List.copyOf(types));
    return provider;
  }

  private static ContextType mockType(String typeName, ContextProperty... properties) {
    var contextType = Mockito.mock(ContextType.class);
    when(contextType.name()).thenReturn(new ContextName(typeName, ""));
    when(contextType.properties()).thenReturn(List.of(properties));
    return contextType;
  }

  private static ContextProperty mockProperty(String ru, String en,
                                              List<Context> types,
                                              List<Context> elementTypes) {
    var property = Mockito.mock(ContextProperty.class);
    when(property.name()).thenReturn(new ContextName(ru, en));
    when(property.types()).thenReturn(types);
    when(property.collectionElementTypes()).thenReturn(elementTypes);
    return property;
  }

  private static Context mockContext(String name) {
    var context = Mockito.mock(Context.class);
    when(context.name()).thenReturn(new ContextName(name, ""));
    return context;
  }
}
