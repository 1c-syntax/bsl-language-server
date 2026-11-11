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
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberSource;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.mdo.HTTPService;
import com.github._1c_syntax.bsl.mdo.IntegrationService;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.WebService;
import com.github._1c_syntax.bsl.mdo.children.HTTPServiceMethod;
import com.github._1c_syntax.bsl.mdo.children.HTTPServiceURLTemplate;
import com.github._1c_syntax.bsl.mdo.children.IntegrationServiceChannel;
import com.github._1c_syntax.bsl.mdo.children.WebServiceOperation;
import com.github._1c_syntax.bsl.mdo.children.WebServiceOperationParameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Обработчики каждого сервиса должны оставаться при своём сервисе: у разных сервисов
 * операции сплошь и рядом называются одинаково — так выглядят версии одного и того же
 * сервиса, — а параметры у них разные.
 */
class ServiceModuleEventRegistrarTest {

  private static final String WEB_MODULE = "Модуль Web-сервиса";
  private static final String HTTP_MODULE = "Модуль HTTP-сервиса";
  private static final String INTEGRATION_MODULE = "Модуль сервиса интеграции";

  private static final String HANDLER_NAME = "Имя обработчика";
  private static final String RECEIVE_HANDLER_NAME = "Имя обработчика полученного сообщения";

  private TypeRegistry typeRegistry;
  private ServiceModuleEventRegistrar registrar;
  private Map<TypeRef, List<MemberDescriptor>> registeredMembers;

  @BeforeEach
  void prepare() {
    typeRegistry = mock(TypeRegistry.class);
    registrar = new ServiceModuleEventRegistrar(typeRegistry);
    registeredMembers = new HashMap<>();

    for (var moduleType : List.of(WEB_MODULE, HTTP_MODULE, INTEGRATION_MODULE)) {
      when(typeRegistry.resolve(moduleType))
        .thenReturn(Optional.of(new TypeRef(TypeKind.PLATFORM, moduleType)));
    }
    // Реестр материализует член-шаблон по переданным именам обработчиков.
    when(typeRegistry.expandedMembers(any(), any(), any(), any()))
      .thenAnswer(invocation -> {
        Map<String, List<String>> expansions = invocation.getArgument(2);
        return expansions.values().stream()
          .flatMap(List::stream)
          .map(name -> MemberDescriptor.event(name, "", List.of()))
          .toList();
      });
    when(typeRegistry.registerSpecialization(anyString(), any(), any(), any()))
      .thenAnswer(invocation ->
        new TypeRef(TypeKind.PLATFORM, invocation.getArgument(0, String.class)));
  }

  @Test
  void eachWebServiceKeepsItsOwnHandlerContract() {
    // given: два сервиса объявляют операцию «Обмен», но с разным числом параметров.
    var first = webService("Exchange_3_0_1_1", operation("Обмен", "Параметры"));
    var second = webService("Exchange_3_0_2_1", operation("Обмен", "Параметры", "Зона"));

    // when
    registrar.register(List.of(first, second));

    // then: у каждого сервиса свой тип со своей сигнатурой — ни один контракт не потерян.
    captureRegisteredMembers();
    assertThat(parameterNamesOf("Модуль Web-сервиса.Exchange_3_0_1_1", "Обмен"))
      .containsExactly("Параметры");
    assertThat(parameterNamesOf("Модуль Web-сервиса.Exchange_3_0_2_1", "Обмен"))
      .containsExactly("Параметры", "Зона");
  }

  @Test
  void eachHttpServiceKeepsItsOwnHandlers() {
    // given: два HTTP-сервиса с одноимённым обработчиком у разных шаблонов URL.
    var first = httpService("ОбменДанными", urlTemplate("Корень", "GET", "ОбработчикGET"));
    var second = httpService("Файлы", urlTemplate("Корень", "GET", "ОбработчикGET"));

    // when
    registrar.register(List.of(first, second));

    // then
    captureRegisteredMembers();
    assertThat(handlerNamesOf("Модуль HTTP-сервиса.ОбменДанными")).containsExactly("ОбработчикGET");
    assertThat(handlerNamesOf("Модуль HTTP-сервиса.Файлы")).containsExactly("ОбработчикGET");
  }

  @Test
  void eachIntegrationServiceKeepsItsOwnHandlers() {
    // given: два сервиса интеграции с одноимёнными обработчиками полученного сообщения.
    var first = integrationService("Обмен", channel("Канал1", "ПриПолучении"));
    var second = integrationService("Уведомления", channel("Канал1", "ПриПолучении"));

    // when
    registrar.register(List.of(first, second));

    // then
    captureRegisteredMembers();
    assertThat(handlerNamesOf("Модуль сервиса интеграции.Обмен")).containsExactly("ПриПолучении");
    assertThat(handlerNamesOf("Модуль сервиса интеграции.Уведомления")).containsExactly("ПриПолучении");
  }

  @Test
  void sharedTypesGetNoServiceHandlers() {
    // given: по одному сервису каждого вида.
    var web = webService("Обмен", operation("Загрузить", "Данные"));
    var http = httpService("Файлы", urlTemplate("Корень", "GET", "ОбработчикGET"));
    var integration = integrationService("Уведомления", channel("Канал1", "ПриПолучении"));

    // when
    registrar.register(List.of(web, http, integration));

    // then: общие типы вида остаются ни при чём — обработчики висят на типах сервисов.
    captureRegisteredMembers();
    assertThat(registeredMembers.keySet())
      .doesNotContain(
        new TypeRef(TypeKind.PLATFORM, WEB_MODULE),
        new TypeRef(TypeKind.PLATFORM, HTTP_MODULE),
        new TypeRef(TypeKind.PLATFORM, INTEGRATION_MODULE))
      .contains(
        new TypeRef(TypeKind.PLATFORM, "Модуль Web-сервиса.Обмен"),
        new TypeRef(TypeKind.PLATFORM, "Модуль HTTP-сервиса.Файлы"),
        new TypeRef(TypeKind.PLATFORM, "Модуль сервиса интеграции.Уведомления"));
  }

  private void captureRegisteredMembers() {
    var refCaptor = ArgumentCaptor.forClass(TypeRef.class);
    var sourceCaptor = ArgumentCaptor.forClass(MemberSource.class);
    verify(typeRegistry, atLeastOnce())
      .registerMemberSource(refCaptor.capture(), sourceCaptor.capture(), eq(FileType.BSL));
    for (var index = 0; index < refCaptor.getAllValues().size(); index++) {
      registeredMembers.put(refCaptor.getAllValues().get(index),
        List.copyOf(sourceCaptor.getAllValues().get(index).getMembers()));
    }
  }

  private List<MemberDescriptor> membersOf(String typeName) {
    var members = registeredMembers.get(new TypeRef(TypeKind.PLATFORM, typeName));
    assertThat(members).as("члены типа " + typeName).isNotNull();
    return members;
  }

  private List<String> handlerNamesOf(String typeName) {
    return membersOf(typeName).stream().map(MemberDescriptor::name).toList();
  }

  private List<String> parameterNamesOf(String typeName, String handler) {
    return membersOf(typeName).stream()
      .filter(member -> member.name().equals(handler))
      .flatMap(member -> member.signatures().stream())
      .flatMap((SignatureDescriptor signature) -> signature.parameters().stream())
      .map(ParameterDescriptor::name)
      .toList();
  }

  private static MD webService(String name, WebServiceOperation... operations) {
    return WebService.builder().name(name).operations(List.of(operations)).build();
  }

  private static WebServiceOperation operation(String name, String... parameterNames) {
    var parameters = Arrays.stream(parameterNames)
      .map(parameterName -> WebServiceOperationParameter.builder().name(parameterName).build())
      .toList();
    return WebServiceOperation.builder()
      .name(name)
      .procedureName(name)
      .parameters(parameters)
      .build();
  }

  private static MD httpService(String name, HTTPServiceURLTemplate... urlTemplates) {
    return HTTPService.builder().name(name).urlTemplates(List.of(urlTemplates)).build();
  }

  private static HTTPServiceURLTemplate urlTemplate(String name, String method, String handler) {
    return HTTPServiceURLTemplate.builder()
      .name(name)
      .method(HTTPServiceMethod.builder().name(method).handler(handler).build())
      .build();
  }

  private static MD integrationService(String name, IntegrationServiceChannel... channels) {
    return IntegrationService.builder().name(name)
      .integrationServiceChannels(List.of(channels)).build();
  }

  private static IntegrationServiceChannel channel(String name, String receiveHandler) {
    return IntegrationServiceChannel.builder()
      .name(name)
      .receiveMessageProcessing(receiveHandler)
      .build();
  }
}
