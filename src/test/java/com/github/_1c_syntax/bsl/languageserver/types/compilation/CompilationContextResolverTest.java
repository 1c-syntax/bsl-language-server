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
package com.github._1c_syntax.bsl.languageserver.types.compilation;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.CompilerDirectiveKind;
import com.github._1c_syntax.bsl.languageserver.types.model.Availability;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.mdo.CommonModule;
import com.github._1c_syntax.bsl.mdclasses.Configuration;
import com.github._1c_syntax.bsl.mdclasses.Solution;
import com.github._1c_syntax.bsl.mdo.Form;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.support.FormType;
import com.github._1c_syntax.bsl.mdo.support.ApplicationRunMode;
import com.github._1c_syntax.bsl.mdo.support.UsePurposes;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.bsl.types.MDOType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompilationContextResolverTest {
  private final TypeRegistry registry = mock(TypeRegistry.class);
  private final CompilationContextResolver resolver = new CompilationContextResolver(registry);

  @Test
  void serverCallChangesVisibilityWithoutChangingExecutionContexts() {
    var document = document(ModuleType.CommonModule);
    var module = mock(CommonModule.class);
    when(document.getMdObject()).thenReturn(Optional.of(module));
    when(module.isServer()).thenReturn(true);
    when(module.isServerCall()).thenReturn(true);
    assertThat(resolver.moduleContexts(document)).containsExactlyInAnyOrder(
      ExecutionContext.SERVER, ExecutionContext.MOBILE_APPLICATION_SERVER, ExecutionContext.MOBILE_STANDALONE_SERVER,
      ExecutionContext.THICK_CLIENT_MANAGED_FILE, ExecutionContext.THICK_CLIENT_ORDINARY_FILE,
      ExecutionContext.EXTERNAL_CONNECTION_FILE);
    assertThat(resolver.canSeeCommonModule(module, ExecutionContext.THIN_CLIENT)).isTrue();
    assertThat(resolver.canSeeCommonModule(module, ExecutionContext.EXTERNAL_CONNECTION)).isTrue();
    when(module.isServerCall()).thenReturn(false);
    assertThat(resolver.canSeeCommonModule(module, ExecutionContext.THIN_CLIENT)).isFalse();
    assertThat(resolver.canSeeCommonModule(module, ExecutionContext.EXTERNAL_CONNECTION)).isFalse();
    assertThat(resolver.canSeeCommonModule(module, ExecutionContext.THICK_CLIENT_MANAGED_FILE_CLIENT)).isTrue();
    assertThat(resolver.canSeeCommonModule(module, ExecutionContext.EXTERNAL_CONNECTION_FILE)).isTrue();
  }

  @Test
  void commonModuleFlagsCoverOrdinaryAndExternalContextsAndAreNotCached() {
    var document = document(ModuleType.CommonModule);
    var module = mock(CommonModule.class);
    when(document.getMdObject()).thenReturn(Optional.of(module));
    when(module.isClientOrdinaryApplication()).thenReturn(true);
    when(module.isExternalConnection()).thenReturn(true);
    assertThat(resolver.moduleContexts(document)).containsExactlyInAnyOrder(
      ExecutionContext.THICK_CLIENT_ORDINARY, ExecutionContext.THICK_CLIENT_ORDINARY_FILE,
      ExecutionContext.EXTERNAL_CONNECTION, ExecutionContext.EXTERNAL_CONNECTION_FILE);
    when(module.isClientOrdinaryApplication()).thenReturn(false);
    assertThat(resolver.moduleContexts(document)).containsExactlyInAnyOrder(
      ExecutionContext.EXTERNAL_CONNECTION, ExecutionContext.EXTERNAL_CONNECTION_FILE);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void objectModuleWithoutPlatformAvailabilityHasUnknownContext(boolean typeExists) {
    var document = document(ModuleType.ObjectModule);
    var md = com.github._1c_syntax.bsl.mdo.Document.builder().name("Заказ").build();
    when(document.getMdObject()).thenReturn(Optional.of(md));
    var type = new TypeRef(TypeKind.CONFIGURATION, "ДокументОбъект.Заказ");
    if (typeExists) {
      when(registry.resolve(type.qualifiedName())).thenReturn(Optional.of(type));
      when(registry.getTypeMetadata(type, FileType.BSL)).thenReturn(PlatformMetadata.EMPTY);
    }

    assertThat(resolver.methodContext(document, Optional.empty())).isEmpty();
  }

  @Test
  void objectModuleReadsGenericAvailabilityWhenSpecializationHasNoMetadata() {
    var document = document(ModuleType.ObjectModule);
    var md = com.github._1c_syntax.bsl.mdo.Document.builder().name("Заказ").build();
    when(document.getMdObject()).thenReturn(Optional.of(md));
    var type = new TypeRef(TypeKind.CONFIGURATION, "ДокументОбъект.Заказ");
    when(registry.resolve(type.qualifiedName())).thenReturn(Optional.of(type));
    when(registry.getTypeMetadata(type, FileType.BSL)).thenReturn(PlatformMetadata.EMPTY);
    var generic = new TypeRef(TypeKind.PLATFORM, "ДокументОбъект.<Имя документа>");
    when(registry.findAllGenericsByFamilyCore("ДокументОбъект")).thenReturn(List.of(generic));
    var metadata = mock(PlatformMetadata.class);
    when(metadata.availabilities()).thenReturn(Set.of(Availability.SERVER));
    when(registry.getTypeMetadata(generic, FileType.BSL)).thenReturn(metadata);

    assertThat(resolver.moduleContexts(document)).containsExactly(ExecutionContext.SERVER);
  }

  @Test
  void recalculationModuleReadsRecordSetAvailability() {
    var document = document(ModuleType.RecalculationModule);
    var generic = new TypeRef(TypeKind.PLATFORM, "ПерерасчетНаборЗаписей.<Имя перерасчета>");
    when(registry.findAllGenericsByFamilyCore("ПерерасчетНаборЗаписей")).thenReturn(List.of(generic));
    var metadata = mock(PlatformMetadata.class);
    when(metadata.availabilities()).thenReturn(Set.of(Availability.SERVER, Availability.EXTERNAL_CONNECTION));
    when(registry.getTypeMetadata(generic, FileType.BSL)).thenReturn(metadata);

    assertThat(resolver.moduleContexts(document)).containsExactlyInAnyOrder(
      ExecutionContext.SERVER, ExecutionContext.EXTERNAL_CONNECTION, ExecutionContext.EXTERNAL_CONNECTION_FILE);
  }

  @ParameterizedTest
  @CsvSource({
    "EXTERNAL_DATA_SOURCE_TABLE, ManagerModule, ВнешнийИсточникДанныхТаблицаМенеджер",
    "EXTERNAL_DATA_SOURCE_TABLE, RecordSetModule, ВнешнийИсточникДанныхТаблицаНаборЗаписей",
    "EXTERNAL_DATA_SOURCE_CUBE, ManagerModule, ВнешнийИсточникДанныхКубМенеджер",
    "EXTERNAL_DATA_SOURCE_CUBE, RecordSetModule, ВнешнийИсточникДанныхКубНаборЗаписей"
  })
  void externalDataSourceModulesUseTheirPlatformFamily(MDOType mdoType, ModuleType moduleType, String family) {
    var document = document(moduleType);
    var md = mock(MD.class);
    when(md.getMdoType()).thenReturn(mdoType);
    when(md.getName()).thenReturn("Тест");
    when(document.getMdObject()).thenReturn(Optional.of(md));
    var generic = new TypeRef(TypeKind.PLATFORM, family + ".<Источник>.<Объект>");
    when(registry.findAllGenericsByFamilyCore(family)).thenReturn(List.of(generic));
    var metadata = mock(PlatformMetadata.class);
    when(metadata.availabilities()).thenReturn(Set.of(
      Availability.SERVER, Availability.THICK_CLIENT, Availability.EXTERNAL_CONNECTION));
    when(registry.getTypeMetadata(generic, FileType.BSL)).thenReturn(metadata);

    assertThat(resolver.moduleContexts(document)).containsExactlyInAnyOrder(ExecutionContext.SERVER,
      ExecutionContext.THICK_CLIENT_MANAGED, ExecutionContext.THICK_CLIENT_ORDINARY, ExecutionContext.EXTERNAL_CONNECTION,
      ExecutionContext.THICK_CLIENT_MANAGED_FILE, ExecutionContext.THICK_CLIENT_ORDINARY_FILE,
      ExecutionContext.EXTERNAL_CONNECTION_FILE);
  }

  @Test
  void sessionModuleHasServerAndFileContextsButNotClientServerClientContexts() {
    var document = document(ModuleType.SessionModule);
    assertThat(resolver.moduleContexts(document)).containsExactlyInAnyOrder(
      ExecutionContext.SERVER, ExecutionContext.MOBILE_APPLICATION_SERVER, ExecutionContext.MOBILE_STANDALONE_SERVER,
      ExecutionContext.THICK_CLIENT_MANAGED_FILE, ExecutionContext.THICK_CLIENT_ORDINARY_FILE,
      ExecutionContext.EXTERNAL_CONNECTION_FILE);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void serviceModuleWithoutPlatformAvailabilityDoesNotAssumeServer(boolean typeExists) {
    var document = document(ModuleType.HTTPServiceModule);
    var type = new TypeRef(TypeKind.PLATFORM, "Модуль HTTP-сервиса");
    if (typeExists) {
      when(registry.resolve(type.qualifiedName())).thenReturn(Optional.of(type));
      when(registry.getTypeMetadata(type, FileType.BSL)).thenReturn(PlatformMetadata.EMPTY);
    }

    assertThat(resolver.methodContext(document, Optional.empty())).isEmpty();
  }

  @ParameterizedTest
  @CsvSource(value = {
    "HTTPServiceModule|Модуль HTTP-сервиса|SERVER;THICK_CLIENT",
    "WEBServiceModule|Модуль Web-сервиса|SERVER;THICK_CLIENT",
    "IntegrationServiceModule|Модуль сервиса интеграции|SERVER;THICK_CLIENT;EXTERNAL_CONNECTION;MOBILE_APPLICATION_SERVER;MOBILE_STANDALONE_SERVER",
    "BotModule|Модуль бота|SERVER;THICK_CLIENT;EXTERNAL_CONNECTION",
    "WEBSocketClientModule|Модуль WebSocket клиента|SERVER;THICK_CLIENT;EXTERNAL_CONNECTION"
  }, delimiter = '|')
  void serviceModulesPreserveSyntaxAssistantAvailability(ModuleType kind, String name, String availability) {
    var document = document(kind);
    var type = new TypeRef(TypeKind.PLATFORM, name);
    var metadata = mock(PlatformMetadata.class);
    when(metadata.availabilities()).thenReturn(Set.copyOf(
      Arrays.stream(availability.split(";")).map(Availability::valueOf).toList()));
    when(registry.resolve(name)).thenReturn(Optional.of(type));
    when(registry.getTypeMetadata(type, FileType.BSL)).thenReturn(metadata);
    var expected = switch (kind) {
      case HTTPServiceModule, WEBServiceModule -> Set.of(
        ExecutionContext.SERVER, ExecutionContext.THICK_CLIENT_MANAGED, ExecutionContext.THICK_CLIENT_ORDINARY,
        ExecutionContext.THICK_CLIENT_MANAGED_FILE, ExecutionContext.THICK_CLIENT_ORDINARY_FILE);
      case IntegrationServiceModule -> Set.of(
        ExecutionContext.SERVER, ExecutionContext.THICK_CLIENT_MANAGED, ExecutionContext.THICK_CLIENT_ORDINARY,
        ExecutionContext.THICK_CLIENT_MANAGED_FILE, ExecutionContext.THICK_CLIENT_ORDINARY_FILE,
        ExecutionContext.EXTERNAL_CONNECTION, ExecutionContext.EXTERNAL_CONNECTION_FILE,
        ExecutionContext.MOBILE_APPLICATION_SERVER, ExecutionContext.MOBILE_STANDALONE_SERVER);
      default -> Set.of(
        ExecutionContext.SERVER, ExecutionContext.THICK_CLIENT_MANAGED, ExecutionContext.THICK_CLIENT_ORDINARY,
        ExecutionContext.THICK_CLIENT_MANAGED_FILE, ExecutionContext.THICK_CLIENT_ORDINARY_FILE,
        ExecutionContext.EXTERNAL_CONNECTION, ExecutionContext.EXTERNAL_CONNECTION_FILE);
    };
    assertThat(resolver.moduleContexts(document)).containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  void commandModuleHasServerMethodsEvenIfItsContextTypeIsClientOnly() {
    var document = document(ModuleType.CommandModule);
    var type = new TypeRef(TypeKind.PLATFORM, "Модуль команды");
    var metadata = mock(PlatformMetadata.class);
    when(metadata.availabilities()).thenReturn(Set.of(Availability.THIN_CLIENT, Availability.THICK_CLIENT));
    when(registry.resolve(type.qualifiedName())).thenReturn(Optional.of(type));
    when(registry.getTypeMetadata(type, FileType.BSL)).thenReturn(metadata);
    assertThat(resolver.methodContext(document, Optional.of(CompilerDirectiveKind.AT_SERVER)).orElseThrow().contexts())
      .contains(ExecutionContext.SERVER);
    assertThat(resolver.methodContext(document, Optional.of(CompilerDirectiveKind.AT_CLIENT)).orElseThrow().contexts())
      .contains(ExecutionContext.THIN_CLIENT);
  }

  @ParameterizedTest
  @EnumSource(value = ModuleType.class, names = {"UNKNOWN", "OScriptModule", "OScriptClass", "CommonModule",
    "ObjectModule", "ManagerModule", "RecordSetModule", "ValueManagerModule", "RecalculationModule",
    "HTTPServiceModule", "WEBServiceModule", "IntegrationServiceModule", "BotModule", "WEBSocketClientModule"})
  void unknownContextDoesNotBecomeServer(ModuleType kind) {
    assertThat(resolver.methodContext(document(kind), Optional.empty())).isEmpty();
  }

  @Test
  void changingSymbolTreeInvalidatesDeclarationBranches() {
    var document = document(ModuleType.FormModule);
    var method = mock(MethodSymbol.class);
    when(method.getName()).thenReturn("Цель");
    var firstTree = mock(SymbolTree.class);
    var secondTree = mock(SymbolTree.class);
    when(firstTree.getMethods()).thenReturn(List.of(method));
    when(secondTree.getMethods()).thenReturn(List.of(method));
    when(firstTree.getMethodPreprocessorContexts(method)).thenReturn(ExecutionContext.CLIENTS);
    when(secondTree.getMethodPreprocessorContexts(method)).thenReturn(ExecutionContext.SERVERS);
    when(document.getSymbolTreeNoLock()).thenReturn(firstTree);
    assertThat(resolver.declarations(document).get("цель").getFirst().branch())
      .containsExactlyInAnyOrderElementsOf(ExecutionContext.CLIENTS);
    when(document.getSymbolTreeNoLock()).thenReturn(secondTree);
    assertThat(resolver.declarations(document).get("ЦЕЛЬ").getFirst().branch())
      .containsExactlyInAnyOrderElementsOf(ExecutionContext.SERVERS);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void ordinaryFormsRespectConfigurationRunModes(boolean useInManagedApplication) {
    var document = document(ModuleType.FormModule);
    var form = mock(Form.class);
    when(form.getFormType()).thenReturn(FormType.ORDINARY);
    when(document.getMdObject()).thenReturn(Optional.of(form));
    var configuration = configuration(document);
    when(configuration.isUseOrdinaryFormInManagedApplication()).thenReturn(useInManagedApplication);
    assertThat(resolver.moduleContexts(document)).contains(ExecutionContext.THICK_CLIENT_ORDINARY);
    assertThat(resolver.moduleContexts(document).contains(ExecutionContext.THICK_CLIENT_MANAGED))
      .isEqualTo(useInManagedApplication);
  }

  @Test
  void autoContextsWithoutConfigurationExcludeStandaloneServer() {
    assertThat(resolver.automaticContexts(document(ModuleType.FormModule)))
      .contains(ExecutionContext.THICK_CLIENT_MANAGED_FILE, ExecutionContext.EXTERNAL_CONNECTION_FILE)
      .doesNotContain(ExecutionContext.MOBILE_STANDALONE_SERVER);
  }

  @Test
  void autoContextsFollowConfigurationPurpose() {
    var document = document(ModuleType.CommonModule);
    var module = mock(CommonModule.class);
    when(document.getMdObject()).thenReturn(Optional.of(module));
    when(module.isClientManagedApplication()).thenReturn(true);
    when(module.isServer()).thenReturn(true);
    var configuration = configuration(document);
    when(configuration.getUsePurposes()).thenReturn(List.of(UsePurposes.PLATFORM_APPLICATION));
    assertThat(resolver.automaticContexts(document))
      .doesNotContain(ExecutionContext.MOBILE_APPLICATION_CLIENT, ExecutionContext.MOBILE_APPLICATION_SERVER,
        ExecutionContext.MOBILE_STANDALONE_SERVER)
      .contains(ExecutionContext.SERVER, ExecutionContext.THIN_CLIENT);
    when(configuration.getUsePurposes()).thenReturn(List.of(UsePurposes.MOBILE_PLATFORM_APPLICATION));
    assertThat(resolver.automaticContexts(document)).containsExactlyInAnyOrder(
      ExecutionContext.MOBILE_APPLICATION_CLIENT, ExecutionContext.MOBILE_APPLICATION_SERVER);
    when(configuration.getUsePurposes()).thenReturn(List.of(
      UsePurposes.PLATFORM_APPLICATION, UsePurposes.MOBILE_PLATFORM_APPLICATION));
    assertThat(resolver.automaticContexts(document))
      .contains(ExecutionContext.THICK_CLIENT_MANAGED_FILE, ExecutionContext.EXTERNAL_CONNECTION_FILE)
      .doesNotContain(ExecutionContext.MOBILE_STANDALONE_SERVER);
  }

  @Test
  void autoContextsFollowDefaultRunMode() {
    var document = document(ModuleType.FormModule);
    var configuration = configuration(document);
    when(configuration.getUsePurposes()).thenReturn(List.of(UsePurposes.PLATFORM_APPLICATION));
    when(configuration.getDefaultRunMode()).thenReturn(ApplicationRunMode.MANAGED_APPLICATION);
    assertThat(resolver.automaticContexts(document)).doesNotContain(ExecutionContext.THICK_CLIENT_ORDINARY)
      .contains(ExecutionContext.THIN_CLIENT, ExecutionContext.WEB_CLIENT, ExecutionContext.THICK_CLIENT_MANAGED);
    when(configuration.getDefaultRunMode()).thenReturn(ApplicationRunMode.ORDINARY_APPLICATION);
    assertThat(resolver.automaticContexts(document)).containsExactlyInAnyOrder(
      ExecutionContext.SERVER, ExecutionContext.THICK_CLIENT_ORDINARY, ExecutionContext.EXTERNAL_CONNECTION,
      ExecutionContext.THICK_CLIENT_ORDINARY_FILE, ExecutionContext.THICK_CLIENT_ORDINARY_FILE_CLIENT,
      ExecutionContext.THICK_CLIENT_ORDINARY_FILE_SERVER, ExecutionContext.EXTERNAL_CONNECTION_FILE);
  }

  @Test
  void managedFormCanCompileInOrdinaryApplicationWhenAllowed() {
    var document = document(ModuleType.FormModule);
    var configuration = configuration(document);
    when(configuration.isUseManagedFormInOrdinaryApplication()).thenReturn(true);
    assertThat(resolver.moduleContexts(document)).contains(ExecutionContext.THICK_CLIENT_ORDINARY);
  }

  private static Configuration configuration(DocumentContext document) {
    var solution = mock(Solution.class);
    var configuration = mock(Configuration.class);
    when(solution.getMergedConfiguration()).thenReturn(configuration);
    when(document.getServerContext().getConfiguration()).thenReturn(solution);
    return configuration;
  }

  private static DocumentContext document(ModuleType kind) {
    var document = mock(DocumentContext.class);
    when(document.getModuleType()).thenReturn(kind);
    var serverContext = mock(ServerContext.class);
    when(document.getServerContext()).thenReturn(serverContext);
    when(serverContext.getConfiguration()).thenReturn(Solution.EMPTY);
    var options = new com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration();
    options.setSupportThickClientFileMode(true);
    when(serverContext.getLanguageServerConfiguration()).thenReturn(options);
    return document;
  }
}
