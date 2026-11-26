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

import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.context.platform.PlatformContextEnum;
import com.github._1c_syntax.bsl.context.platform.PlatformContextEnumValue;
import com.github._1c_syntax.bsl.context.platform.PlatformContextMethod;
import com.github._1c_syntax.bsl.context.platform.PlatformContextProperty;
import com.github._1c_syntax.bsl.context.platform.PlatformContextProvider;
import com.github._1c_syntax.bsl.context.platform.PlatformGlobalContext;
import com.github._1c_syntax.bsl.context.platform.internal.PlatformContextStorage;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Юнит-тест поставщика членов глобального контекста, путь bsl-context
 * ({@code membersFromContext}): этот путь исполняется только при подключённой
 * платформе (HBK), поэтому покрывается синтетическим {@link PlatformContextProvider}
 * по образцу {@link BslContextPlatformTypesProviderTest}, без реальной платформы.
 * <p>
 * Проверяет, что члены синтетического типа {@link TypeRegistry#GLOBAL_CONTEXT}
 * собираются из методов и свойств глобального контекста плюс системных
 * перечислений (top-level {@code ContextEnum}) как свойств-членов.
 */
class GlobalContextTypesProviderTest {

  private static BslContextHolder holderOf(ContextProvider provider) {
    return new BslContextHolder(null) {
      @Override
      public Optional<ContextProvider> get() {
        return Optional.ofNullable(provider);
      }
    };
  }

  private static ContextProvider providerOf(Context... contexts) {
    return new PlatformContextProvider(
      new PlatformContextStorage(new ArrayList<>(List.of(contexts))));
  }

  @Test
  void globalContextMethodsPropertiesAndEnumsBecomeMembers() {
    // given: глобальный контекст с методом и свойством плюс системное
    // перечисление верхнего уровня
    var method = PlatformContextMethod.builder()
      .name(new ContextName("Сообщить", "Message"))
      .description("")
      .availabilities(List.of())
      .rawReturnValues(List.of())
      .signatures(new ArrayList<>())
      .build();
    var property = PlatformContextProperty.builder()
      .name(new ContextName("РабочийКаталог", "WorkingDirectory"))
      .rawTypes(List.of())
      .description("")
      .availabilities(List.of())
      .build();
    var globalContext = PlatformGlobalContext.builder()
      .methods(new ArrayList<>(List.of(method)))
      .properties(new ArrayList<>(List.of(property)))
      .applicationEvents(Collections.emptyList())
      .ordinaryApplicationEvents(Collections.emptyList())
      .sessionModuleEvents(Collections.emptyList())
      .externalConnectionModuleEvents(Collections.emptyList())
      .build();
    var encoding = PlatformContextEnum.builder()
      .name(new ContextName("КодировкаТекста", "TextEncoding"))
      .values(List.of(new PlatformContextEnumValue(new ContextName("UTF8", "UTF8"))))
      .build();

    // when: провайдер собирает синтетический тип глобального контекста
    var decl = new GlobalContextTypesProvider(holderOf(providerOf(globalContext, encoding)))
      .getTypes().iterator().next();
    var members = List.copyOf(decl.members());

    // then: метод и свойство глобального контекста — соответствующие члены
    assertThat(members)
      .as("методы и свойства глобального контекста — члены GLOBAL_CONTEXT")
      .anyMatch(m -> m.kind() == MemberKind.METHOD && m.matches("Сообщить"))
      .anyMatch(m -> m.kind() == MemberKind.PROPERTY && m.matches("РабочийКаталог"));

    // then: системное перечисление — свойство-член с valueType = сам тип
    var enumMember = members.stream()
      .filter(m -> m.kind() == MemberKind.PROPERTY && m.matches("КодировкаТекста"))
      .findFirst();
    assertThat(enumMember)
      .as("системное перечисление становится свойством-членом GLOBAL_CONTEXT")
      .isPresent();
    assertThat(enumMember.orElseThrow().returnTypes().refs())
      .as("тип-значение свойства-перечисления — сам тип перечисления")
      .anyMatch(ref -> ref.qualifiedName().equals("КодировкаТекста"));
  }
}
