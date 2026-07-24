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
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EventHandlerResolverTest {

  private final TypeRegistry typeRegistry = mock(TypeRegistry.class);
  private final BslContextHolder bslContextHolder = mock(BslContextHolder.class);
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

    verifyNoInteractions(typeRegistry);
  }

  @Test
  void globalModuleWithoutHbkReturnsEmpty() {
    when(bslContextHolder.get()).thenReturn(Optional.empty());
    var doc = mock(DocumentContext.class);
    when(doc.getModuleType()).thenReturn(ModuleType.SessionModule);

    var contract = resolver.lookupContract(doc, "УстановкаПараметровСеанса");

    assertThat(contract).isEmpty();
  }

  private static DocumentContext oscriptClassDoc() {
    var doc = mock(DocumentContext.class);
    when(doc.getModuleType()).thenReturn(ModuleType.OScriptClass);
    return doc;
  }

  @Test
  void globalModuleWithHbkResolvesEventByName() {
    var event = stubEvent("ПередНачаломРаботыСистемы", "BeforeStart");
    var globalContext = mock(PlatformGlobalContext.class);
    when(globalContext.applicationEvents()).thenReturn(List.of(event));
    when(globalContext.ordinaryApplicationEvents()).thenReturn(List.of());
    when(globalContext.sessionModuleEvents()).thenReturn(List.of());
    when(globalContext.externalConnectionModuleEvents()).thenReturn(List.of());
    var provider = mock(ContextProvider.class);
    when(provider.getGlobalContext()).thenReturn(globalContext);
    when(bslContextHolder.get()).thenReturn(Optional.of(provider));

    var doc = mock(DocumentContext.class);
    when(doc.getModuleType()).thenReturn(ModuleType.ManagedApplicationModule);

    // По ru-имени и по en-алиасу должны находить один и тот же контракт.
    assertThat(resolver.lookupContract(doc, "ПередНачаломРаботыСистемы")).isPresent();
    assertThat(resolver.lookupContract(doc, "BeforeStart")).isPresent();
    // Чужое имя — пусто.
    assertThat(resolver.lookupContract(doc, "Случайное")).isEmpty();
  }

  @Test
  void globalEventsRebuiltAfterHbkBecomesAvailable() {
    var doc = mock(DocumentContext.class);
    when(doc.getModuleType()).thenReturn(ModuleType.ManagedApplicationModule);

    // 1) Провайдер (HBK/bsl-context) ещё не готов — событий нет.
    when(bslContextHolder.get()).thenReturn(Optional.empty());
    assertThat(resolver.allEvents(doc)).isEmpty();

    // 2) HBK подгрузился — пустой результат не должен был закэшироваться: те же вызовы
    // теперь находят событие (регресс на инвалидацию кэша globalEvents).
    var event = stubEvent("ПередНачаломРаботыСистемы", "BeforeStart");
    var globalContext = mock(PlatformGlobalContext.class);
    when(globalContext.applicationEvents()).thenReturn(List.of(event));
    when(globalContext.ordinaryApplicationEvents()).thenReturn(List.of());
    when(globalContext.sessionModuleEvents()).thenReturn(List.of());
    when(globalContext.externalConnectionModuleEvents()).thenReturn(List.of());
    var provider = mock(ContextProvider.class);
    when(provider.getGlobalContext()).thenReturn(globalContext);
    when(bslContextHolder.get()).thenReturn(Optional.of(provider));

    assertThat(resolver.lookupContract(doc, "ПередНачаломРаботыСистемы")).isPresent();
    assertThat(resolver.allEvents(doc)).isNotEmpty();
  }

  @Test
  void globalModuleEmptyEventListReturnsEmpty() {
    var globalContext = mock(PlatformGlobalContext.class);
    when(globalContext.applicationEvents()).thenReturn(List.of());
    when(globalContext.ordinaryApplicationEvents()).thenReturn(List.of());
    when(globalContext.sessionModuleEvents()).thenReturn(List.of());
    when(globalContext.externalConnectionModuleEvents()).thenReturn(List.of());
    var provider = mock(ContextProvider.class);
    when(provider.getGlobalContext()).thenReturn(globalContext);
    when(bslContextHolder.get()).thenReturn(Optional.of(provider));

    var doc = mock(DocumentContext.class);
    when(doc.getModuleType()).thenReturn(ModuleType.OrdinaryApplicationModule);

    assertThat(resolver.lookupContract(doc, "Что-то")).isEmpty();
  }

  @Test
  void commandModuleResolvesViaFixedOwnerType() {
    // CommandModule идёт через MODULE_TYPE_TO_FIXED_OWNER_RU = "Модуль команды".
    var commandRef = new TypeRef(TypeKind.PLATFORM, "Модуль команды");
    when(typeRegistry.resolve("Модуль команды")).thenReturn(Optional.of(commandRef));
    var eventDescriptor = MemberDescriptor.event(
      "ОбработкаКоманды", "",
      List.of(new SignatureDescriptor(List.of(), TypeSet.EMPTY, "")));
    when(typeRegistry.getMembers(eq(commandRef), any()))
      .thenReturn(List.of(eventDescriptor));

    var doc = mock(DocumentContext.class);
    when(doc.getModuleType()).thenReturn(ModuleType.CommandModule);
    when(doc.getFileType()).thenReturn(FileType.BSL);

    assertThat(resolver.lookupContract(doc, "ОбработкаКоманды")).isPresent();
    assertThat(resolver.lookupContract(doc, "ЧтоТоЕщё")).isEmpty();
  }

  @Test
  void ownerTypeEventResolvesByEnglishAliasNotJustRuPrimaryName() {
    var objectRef = new TypeRef(TypeKind.CONFIGURATION, "ДокументОбъект.Заказ");
    when(typeRegistry.resolve("ДокументОбъект.Заказ")).thenReturn(Optional.of(objectRef));
    var eventDescriptor = MemberDescriptor.event(
        "ПередЗаписью", "",
        List.of(new SignatureDescriptor(List.of(), TypeSet.EMPTY, "")))
      .withBilingualName(BilingualString.of("ПередЗаписью", "BeforeWrite"));
    when(typeRegistry.getMembers(eq(objectRef), any()))
      .thenReturn(List.of(eventDescriptor));

    var document = com.github._1c_syntax.bsl.mdo.Document.builder().name("Заказ").build();
    var doc = mock(DocumentContext.class);
    when(doc.getModuleType()).thenReturn(ModuleType.ObjectModule);
    when(doc.getMdObject()).thenReturn(Optional.of(document));
    when(doc.getFileType()).thenReturn(FileType.BSL);

    assertThat(resolver.lookupContract(doc, "ПередЗаписью")).isPresent();
    assertThat(resolver.lookupContract(doc, "BeforeWrite")).isPresent();
    assertThat(resolver.lookupContract(doc, "beforewrite")).isPresent();
    assertThat(resolver.lookupContract(doc, "СлучайноеИмя")).isEmpty();
  }

  @Test
  void fixedOwnerTypeAbsentInRegistryReturnsEmpty() {
    when(typeRegistry.resolve(anyString())).thenReturn(Optional.empty());
    var doc = mock(DocumentContext.class);
    when(doc.getModuleType()).thenReturn(ModuleType.HTTPServiceModule);

    assertThat(resolver.lookupContract(doc, "ЛюбоеИмя")).isEmpty();
  }

  @Test
  void mdoSpecificOwnerWithBlankNameReturnsEmpty() {
    // mdoSpecificQualifiedName возвращает empty при пустом name MDO —
    // строим реальный Catalog с пустым именем.
    var catalog = com.github._1c_syntax.bsl.mdo.Catalog.builder().name("").build();
    var doc = mock(DocumentContext.class);
    when(doc.getModuleType()).thenReturn(ModuleType.ObjectModule);
    when(doc.getMdObject()).thenReturn(Optional.of(catalog));

    assertThat(resolver.lookupContract(doc, "ПриЗаписи")).isEmpty();
  }

  @Test
  void mdoSpecificOwnerWithoutMdObjectReturnsEmpty() {
    // ObjectModule → ищем mdObject; если его нет — empty.
    var doc = mock(DocumentContext.class);
    when(doc.getModuleType()).thenReturn(ModuleType.ObjectModule);
    when(doc.getMdObject()).thenReturn(Optional.empty());

    assertThat(resolver.lookupContract(doc, "ПриЗаписи")).isEmpty();
  }

  @Test
  void unsupportedModuleTypeReturnsEmpty() {
    var doc = mock(DocumentContext.class);
    when(doc.getModuleType()).thenReturn(ModuleType.CommonModule);

    assertThat(resolver.lookupContract(doc, "ПриЗаписи")).isEmpty();
  }

  /**
   * Осознанное ограничение (не баг): обработчики форм не резолвятся. Форма — единственный
   * {@link ModuleType}, у которого события декларируются не по имени, а в {@code Form.xml}
   * (блок {@code <Events>}), это отдельная, не начатая задача (см. javadoc класса). Явный тест
   * фиксирует контракт, чтобы отсутствие резолва для форм не осталось молчаливым и не
   * воспринималось как забытый кейс при последующих правках.
   */
  @Test
  void formModuleIsNotResolvedByDesign() {
    var doc = mock(DocumentContext.class);
    when(doc.getModuleType()).thenReturn(ModuleType.FormModule);

    assertThat(resolver.lookupContract(doc, "ПриОткрытии")).isEmpty();
    assertThat(resolver.allEvents(doc)).isEmpty();
    verifyNoInteractions(typeRegistry);
  }

  @Test
  void allEventsForOScriptClassReturnsBuiltinEvents() {
    var doc = oscriptClassDoc();

    var events = resolver.allEvents(doc);

    assertThat(events)
      .extracting(MemberDescriptor::name)
      .containsExactlyInAnyOrder("ПриСозданииОбъекта", "ОбработкаПолученияПредставления");
  }

  @Test
  void allEventsForGlobalModuleDedupsRuEnAliases() {
    var event = stubEvent("ПередНачаломРаботыСистемы", "BeforeStart");
    var globalContext = mock(PlatformGlobalContext.class);
    when(globalContext.applicationEvents()).thenReturn(List.of(event));
    when(globalContext.ordinaryApplicationEvents()).thenReturn(List.of());
    when(globalContext.sessionModuleEvents()).thenReturn(List.of());
    when(globalContext.externalConnectionModuleEvents()).thenReturn(List.of());
    var provider = mock(ContextProvider.class);
    when(provider.getGlobalContext()).thenReturn(globalContext);
    when(bslContextHolder.get()).thenReturn(Optional.of(provider));

    var doc = mock(DocumentContext.class);
    when(doc.getModuleType()).thenReturn(ModuleType.ManagedApplicationModule);

    // Событие лежит в карте под ru- и en-ключом на один и тот же объект — в allEvents() должно
    // остаться единственное вхождение, а не два дубля.
    assertThat(resolver.allEvents(doc))
      .hasSize(1)
      .extracting(MemberDescriptor::name)
      .containsExactly("ПередНачаломРаботыСистемы");
  }

  @Test
  void allEventsForGlobalModuleWithoutHbkReturnsEmpty() {
    when(bslContextHolder.get()).thenReturn(Optional.empty());
    var doc = mock(DocumentContext.class);
    when(doc.getModuleType()).thenReturn(ModuleType.SessionModule);

    assertThat(resolver.allEvents(doc)).isEmpty();
  }

  @Test
  void allEventsForMdoSpecificOwnerFiltersByEventKind() {
    var eventDescriptor = MemberDescriptor.event(
      "ПриЗаписи", "", List.of(new SignatureDescriptor(List.of(), TypeSet.EMPTY, "")));
    var methodDescriptor = MemberDescriptor.method("Записать");
    var commandRef = new TypeRef(TypeKind.PLATFORM, "Модуль команды");
    when(typeRegistry.resolve("Модуль команды")).thenReturn(Optional.of(commandRef));
    when(typeRegistry.getMembers(eq(commandRef), any()))
      .thenReturn(List.of(eventDescriptor, methodDescriptor));

    var doc = mock(DocumentContext.class);
    when(doc.getModuleType()).thenReturn(ModuleType.CommandModule);
    when(doc.getFileType()).thenReturn(FileType.BSL);

    assertThat(resolver.allEvents(doc))
      .extracting(MemberDescriptor::name)
      .containsExactly("ПриЗаписи");
  }

  @Test
  void allEventsForUnsupportedModuleTypeReturnsEmpty() {
    var doc = mock(DocumentContext.class);
    when(doc.getModuleType()).thenReturn(ModuleType.CommonModule);

    assertThat(resolver.allEvents(doc)).isEmpty();
  }

  private static ContextEvent stubEvent(String ru, String en) {
    var event = mock(ContextEvent.class);
    var name = new ContextName(ru, en);
    when(event.name()).thenReturn(name);
    when(event.signatures()).thenReturn(List.of());
    when(event.description()).thenReturn("");
    when(event.availabilities()).thenReturn(List.<Availability>of());
    when(event.sinceVersion()).thenReturn("");
    when(event.deprecatedSinceVersion()).thenReturn("");
    when(event.recommendedReplacements()).thenReturn(List.of());
    return event;
  }
}
