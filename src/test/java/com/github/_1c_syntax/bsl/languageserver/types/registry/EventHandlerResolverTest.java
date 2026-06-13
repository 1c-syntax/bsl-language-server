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

import com.github._1c_syntax.bsl.context.api.Availability;
import com.github._1c_syntax.bsl.context.api.ContextEvent;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.context.platform.PlatformGlobalContext;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EventHandlerResolverTest {

  private final TypeRegistry typeRegistry = Mockito.mock(TypeRegistry.class);
  private final BslContextHolder bslContextHolder = Mockito.mock(BslContextHolder.class);
  private final EventHandlerResolver resolver = new EventHandlerResolver(typeRegistry, bslContextHolder);

  @Test
  void oscriptClassConstructorEventByRuName() {
    var doc = oscriptClassDoc();

    var contract = resolver.lookupContract(doc, "ПриСозданииОбъекта").orElseThrow();

    assertThat(contract.kind()).isEqualTo(MemberKind.EVENT);
    assertThat(contract.name()).isEqualTo("ПриСозданииОбъекта");
    var param = contract.signatures().get(0).parameters().get(0);
    assertThat(param.variadic()).isTrue();
    assertThat(param.optional()).isTrue();
  }

  @Test
  void oscriptClassConstructorEventByEnName() {
    var doc = oscriptClassDoc();

    var contract = resolver.lookupContract(doc, "OnObjectCreate").orElseThrow();

    assertThat(contract.name()).isEqualTo("ПриСозданииОбъекта");
  }

  @Test
  void oscriptClassPresentationGetProcessingHasTwoParameters() {
    var doc = oscriptClassDoc();

    var contract = resolver.lookupContract(doc, "ОбработкаПолученияПредставления").orElseThrow();
    var params = contract.signatures().get(0).parameters();

    assertThat(params).hasSize(2);
    assertThat(params.get(0).bilingualName().ru()).isEqualTo("Представление");
    assertThat(params.get(0).bilingualName().en()).isEqualTo("Presentation");
    assertThat(params.get(0).types().refs()).singleElement()
      .extracting(TypeRef::qualifiedName).isEqualTo("Строка");
    assertThat(params.get(1).bilingualName().ru()).isEqualTo("СтандартнаяОбработка");
    assertThat(params.get(1).bilingualName().en()).isEqualTo("StandardProcessing");
    assertThat(params.get(1).types().refs()).singleElement()
      .extracting(TypeRef::qualifiedName).isEqualTo("Булево");
  }

  @Test
  void oscriptClassUnknownMethodIsNotEventHandler() {
    var doc = oscriptClassDoc();

    var contract = resolver.lookupContract(doc, "СлучайныйМетод");

    assertThat(contract).isEmpty();
  }

  @Test
  void typeRegistryNotUsedForOScriptClassModule() {
    var doc = oscriptClassDoc();

    resolver.lookupContract(doc, "ПриСозданииОбъекта");

    Mockito.verifyNoInteractions(typeRegistry);
  }

  @Test
  void globalModuleWithoutHbkReturnsEmpty() {
    Mockito.when(bslContextHolder.get()).thenReturn(Optional.empty());
    var doc = Mockito.mock(DocumentContext.class);
    Mockito.when(doc.getModuleType()).thenReturn(ModuleType.SessionModule);

    var contract = resolver.lookupContract(doc, "УстановкаПараметровСеанса");

    assertThat(contract).isEmpty();
  }

  private static DocumentContext oscriptClassDoc() {
    var doc = Mockito.mock(DocumentContext.class);
    Mockito.when(doc.getModuleType()).thenReturn(ModuleType.OScriptClass);
    return doc;
  }

  @Test
  void globalModuleWithHbkResolvesEventByName() {
    var event = stubEvent("ПередНачаломРаботыСистемы", "BeforeStart");
    var globalContext = Mockito.mock(PlatformGlobalContext.class);
    Mockito.when(globalContext.applicationEvents()).thenReturn(List.of(event));
    Mockito.when(globalContext.ordinaryApplicationEvents()).thenReturn(List.of());
    Mockito.when(globalContext.sessionModuleEvents()).thenReturn(List.of());
    Mockito.when(globalContext.externalConnectionModuleEvents()).thenReturn(List.of());
    var provider = Mockito.mock(ContextProvider.class);
    Mockito.when(provider.getGlobalContext()).thenReturn(globalContext);
    Mockito.when(bslContextHolder.get()).thenReturn(Optional.of(provider));

    var doc = Mockito.mock(DocumentContext.class);
    Mockito.when(doc.getModuleType()).thenReturn(ModuleType.ManagedApplicationModule);

    // По ru-имени и по en-алиасу должны находить один и тот же контракт.
    assertThat(resolver.lookupContract(doc, "ПередНачаломРаботыСистемы")).isPresent();
    assertThat(resolver.lookupContract(doc, "BeforeStart")).isPresent();
    // Чужое имя — пусто.
    assertThat(resolver.lookupContract(doc, "Случайное")).isEmpty();
  }

  @Test
  void globalModuleEmptyEventListReturnsEmpty() {
    var globalContext = Mockito.mock(PlatformGlobalContext.class);
    Mockito.when(globalContext.applicationEvents()).thenReturn(List.of());
    Mockito.when(globalContext.ordinaryApplicationEvents()).thenReturn(List.of());
    Mockito.when(globalContext.sessionModuleEvents()).thenReturn(List.of());
    Mockito.when(globalContext.externalConnectionModuleEvents()).thenReturn(List.of());
    var provider = Mockito.mock(ContextProvider.class);
    Mockito.when(provider.getGlobalContext()).thenReturn(globalContext);
    Mockito.when(bslContextHolder.get()).thenReturn(Optional.of(provider));

    var doc = Mockito.mock(DocumentContext.class);
    Mockito.when(doc.getModuleType()).thenReturn(ModuleType.OrdinaryApplicationModule);

    assertThat(resolver.lookupContract(doc, "Что-то")).isEmpty();
  }

  @Test
  void commandModuleResolvesViaFixedOwnerType() {
    // CommandModule идёт через MODULE_TYPE_TO_FIXED_OWNER_RU = "Модуль команды".
    var commandRef = new TypeRef(TypeKind.PLATFORM, "Модуль команды");
    Mockito.when(typeRegistry.resolve("Модуль команды")).thenReturn(Optional.of(commandRef));
    var eventDescriptor = MemberDescriptor.event(
      "ОбработкаКоманды", "",
      List.of(new SignatureDescriptor(List.of(), TypeSet.EMPTY, "")));
    Mockito.when(typeRegistry.getMembers(Mockito.eq(commandRef), Mockito.any()))
      .thenReturn(List.of(eventDescriptor));

    var doc = Mockito.mock(DocumentContext.class);
    Mockito.when(doc.getModuleType()).thenReturn(ModuleType.CommandModule);
    Mockito.when(doc.getFileType()).thenReturn(FileType.BSL);

    assertThat(resolver.lookupContract(doc, "ОбработкаКоманды")).isPresent();
    assertThat(resolver.lookupContract(doc, "ЧтоТоЕщё")).isEmpty();
  }

  @Test
  void fixedOwnerTypeAbsentInRegistryReturnsEmpty() {
    Mockito.when(typeRegistry.resolve(Mockito.anyString())).thenReturn(Optional.empty());
    var doc = Mockito.mock(DocumentContext.class);
    Mockito.when(doc.getModuleType()).thenReturn(ModuleType.HTTPServiceModule);

    assertThat(resolver.lookupContract(doc, "ЛюбоеИмя")).isEmpty();
  }

  @Test
  void mdoSpecificOwnerWithBlankNameReturnsEmpty() {
    // mdoSpecificQualifiedName возвращает empty при пустом name MDO —
    // строим реальный Catalog с пустым именем.
    var catalog = com.github._1c_syntax.bsl.mdo.Catalog.builder().name("").build();
    var doc = Mockito.mock(DocumentContext.class);
    Mockito.when(doc.getModuleType()).thenReturn(ModuleType.ObjectModule);
    Mockito.when(doc.getMdObject()).thenReturn(Optional.of(catalog));

    assertThat(resolver.lookupContract(doc, "ПриЗаписи")).isEmpty();
  }

  @Test
  void mdoSpecificOwnerWithoutMdObjectReturnsEmpty() {
    // ObjectModule → ищем mdObject; если его нет — empty.
    var doc = Mockito.mock(DocumentContext.class);
    Mockito.when(doc.getModuleType()).thenReturn(ModuleType.ObjectModule);
    Mockito.when(doc.getMdObject()).thenReturn(Optional.empty());

    assertThat(resolver.lookupContract(doc, "ПриЗаписи")).isEmpty();
  }

  @Test
  void unsupportedModuleTypeReturnsEmpty() {
    var doc = Mockito.mock(DocumentContext.class);
    Mockito.when(doc.getModuleType()).thenReturn(ModuleType.CommonModule);

    assertThat(resolver.lookupContract(doc, "ПриЗаписи")).isEmpty();
  }

  private static ContextEvent stubEvent(String ru, String en) {
    var event = Mockito.mock(ContextEvent.class);
    var name = new ContextName(ru, en);
    Mockito.when(event.name()).thenReturn(name);
    Mockito.when(event.signatures()).thenReturn(List.of());
    Mockito.when(event.description()).thenReturn("");
    Mockito.when(event.availabilities()).thenReturn(List.<Availability>of());
    Mockito.when(event.sinceVersion()).thenReturn("");
    Mockito.when(event.deprecatedSinceVersion()).thenReturn("");
    Mockito.when(event.recommendedReplacements()).thenReturn(List.of());
    return event;
  }
}
