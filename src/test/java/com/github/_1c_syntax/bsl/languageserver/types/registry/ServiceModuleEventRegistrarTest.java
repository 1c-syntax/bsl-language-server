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
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.mdo.WebService;
import com.github._1c_syntax.bsl.mdo.children.WebServiceOperation;
import com.github._1c_syntax.bsl.mdo.children.WebServiceOperationParameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Одноимённые операции разных веб-сервисов принимают разные параметры — в типовых
 * конфигурациях так выглядят версии одного и того же сервиса. Контракт каждой должен
 * оставаться при своём сервисе.
 */
class ServiceModuleEventRegistrarTest {

  private static final String WEB_SERVICE_MODULE = "Модуль Web-сервиса";
  private static final TypeRef GENERIC = new TypeRef(TypeKind.PLATFORM, WEB_SERVICE_MODULE);

  private TypeRegistry typeRegistry;
  private ServiceModuleEventRegistrar registrar;
  private Map<TypeRef, List<MemberDescriptor>> registeredMembers;

  @BeforeEach
  void prepare() {
    typeRegistry = mock(TypeRegistry.class);
    registrar = new ServiceModuleEventRegistrar(typeRegistry);
    registeredMembers = new HashMap<>();

    when(typeRegistry.resolve(WEB_SERVICE_MODULE)).thenReturn(Optional.of(GENERIC));
    // Реестр материализует шаблон «<Имя обработчика>» по переданным именам.
    when(typeRegistry.expandedMembers(eq(GENERIC), any(), any(), any()))
      .thenAnswer(invocation -> {
        Map<String, List<String>> expansions = invocation.getArgument(2);
        return expansions.get("Имя обработчика").stream()
          .map(name -> MemberDescriptor.event(name, "", List.of()))
          .toList();
      });
    when(typeRegistry.registerSpecialization(anyString(), eq(GENERIC), any(), any()))
      .thenAnswer(invocation ->
        new TypeRef(TypeKind.PLATFORM, invocation.getArgument(0, String.class)));
  }

  @Test
  void eachServiceKeepsItsOwnHandlerContract() {
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
  void sharedTypeGetsNoServiceHandlers() {
    // given
    var service = webService("Обмен", operation("Загрузить", "Данные"));

    // when
    registrar.register(List.of(service));

    // then: общий тип вида остаётся ни при чём — обработчики висят на типе сервиса.
    captureRegisteredMembers();
    assertThat(registeredMembers).doesNotContainKey(GENERIC);
    assertThat(registeredMembers).containsKey(new TypeRef(TypeKind.PLATFORM, "Модуль Web-сервиса.Обмен"));
  }

  private void captureRegisteredMembers() {
    var refCaptor = ArgumentCaptor.forClass(TypeRef.class);
    var sourceCaptor = ArgumentCaptor.forClass(MemberSource.class);
    org.mockito.Mockito.verify(typeRegistry, org.mockito.Mockito.atLeastOnce())
      .registerMemberSource(refCaptor.capture(), sourceCaptor.capture(), eq(FileType.BSL));
    for (var index = 0; index < refCaptor.getAllValues().size(); index++) {
      registeredMembers.put(refCaptor.getAllValues().get(index),
        List.copyOf(sourceCaptor.getAllValues().get(index).getMembers()));
    }
  }

  private List<String> parameterNamesOf(String typeName, String handler) {
    var members = registeredMembers.get(new TypeRef(TypeKind.PLATFORM, typeName));
    assertThat(members).as("члены типа " + typeName).isNotNull();
    return members.stream()
      .filter(member -> member.name().equals(handler))
      .flatMap(member -> member.signatures().stream())
      .flatMap((SignatureDescriptor signature) -> signature.parameters().stream())
      .map(parameter -> parameter.name())
      .toList();
  }

  private static WebService webService(String name, WebServiceOperation... operations) {
    return WebService.builder().name(name).operations(List.of(operations)).build();
  }

  private static WebServiceOperation operation(String name, String... parameterNames) {
    var parameters = java.util.Arrays.stream(parameterNames)
      .map(parameterName -> WebServiceOperationParameter.builder().name(parameterName).build())
      .toList();
    return WebServiceOperation.builder()
      .name(name)
      .procedureName(name)
      .parameters(parameters)
      .build();
  }
}
