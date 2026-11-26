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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.types.index.EventContractsIndex;
import com.github._1c_syntax.bsl.languageserver.types.index.SymbolTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Объявленный тип параметра: комментарий метода, контракт события, ссылка «См. …».
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeclaredParameterTypeResolverTest {

  private static final TypeRef STRING = new TypeRef(TypeKind.PRIMITIVE, "Строка");
  private static final TypeRef STRUCTURE = new TypeRef(TypeKind.PLATFORM, "Структура");

  @Mock
  private SymbolTypeIndex symbolTypeIndex;

  @Mock
  private EventContractsIndex eventContractsIndex;

  @Mock
  private DocumentContext documentContext;

  @Mock
  private MethodSymbol method;

  @Mock
  private VariableSymbol variable;

  @Mock
  private ParameterDefinition parameter;

  @Mock
  private ParameterDefinition otherParameter;

  @InjectMocks
  private DeclaredParameterTypeResolver resolver;

  @Test
  void commentedTypeWins() {
    // given: у параметра есть собственное описание — оно приоритетнее прочих источников.
    givenParameter("Данные");
    when(symbolTypeIndex.getDeclaredParameterTypes(parameter, documentContext))
      .thenReturn(TypeSet.of(STRING));

    // when
    var types = resolver.typesOf(variable);

    // then
    assertThat(types.refs()).containsExactly(STRING);
  }

  @Test
  void eventContractIsUsedWhenCommentIsAbsent() {
    // given: имена параметров обработчика задаёт автор, поэтому контракт сопоставляется
    // по позиции, а не по имени.
    givenParameter("СовсемДругоеИмя");
    when(symbolTypeIndex.getDeclaredParameterTypes(any(), any())).thenReturn(TypeSet.EMPTY);
    givenContract(parameterDescriptor(TypeSet.of(STRUCTURE), false));

    // when
    var types = resolver.typesOf(variable);

    // then
    assertThat(types.refs()).containsExactly(STRUCTURE);
  }

  @Test
  void parameterBeyondContractGetsNothingWhenTailIsNotVariadic() {
    // given: метод объявил больше параметров, чем есть в контракте.
    givenParameter("Пятый");
    givenPrecedingParameter();
    when(symbolTypeIndex.getDeclaredParameterTypes(any(), any())).thenReturn(TypeSet.EMPTY);
    givenContract(parameterDescriptor(TypeSet.of(STRUCTURE), false));
    when(method.getDescription()).thenReturn(Optional.empty());

    // when
    var types = resolver.typesOf(variable);

    // then
    assertThat(types).isEqualTo(TypeSet.EMPTY);
  }

  @Test
  void variadicTailSpreadsItsTypeToFurtherParameters() {
    // given: хвост переменной арности — как у конструктора класса OneScript.
    givenParameter("Третий");
    givenPrecedingParameter();
    when(symbolTypeIndex.getDeclaredParameterTypes(any(), any())).thenReturn(TypeSet.EMPTY);
    givenContract(parameterDescriptor(TypeSet.of(STRUCTURE), true));

    // when
    var types = resolver.typesOf(variable);

    // then
    assertThat(types.refs()).containsExactly(STRUCTURE);
  }

  @Test
  void localVariableIsNotAParameter() {
    // given
    when(variable.getKind()).thenReturn(VariableKind.LOCAL);

    // when
    var types = resolver.typesOf(variable);

    // then
    assertThat(types).isEqualTo(TypeSet.EMPTY);
  }

  @Test
  void variableWithNoMatchingParameterNameGetsNothing() {
    // given
    when(variable.getKind()).thenReturn(VariableKind.PARAMETER);
    when(variable.getName()).thenReturn("Неизвестный");
    when(variable.getScope()).thenReturn(method);
    when(otherParameter.getName()).thenReturn("Другой");
    when(method.getParameters()).thenReturn(List.of(otherParameter));

    // when
    var types = resolver.typesOf(variable);

    // then
    assertThat(types).isEqualTo(TypeSet.EMPTY);
  }

  private void givenParameter(String name) {
    when(variable.getKind()).thenReturn(VariableKind.PARAMETER);
    when(variable.getName()).thenReturn(name);
    when(variable.getScope()).thenReturn(method);
    when(parameter.getName()).thenReturn(name);
    when(method.getParameters()).thenReturn(List.of(parameter));
    when(method.getOwner()).thenReturn(documentContext);
    when(method.getName()).thenReturn("ПриЗаписи");
  }

  private void givenContract(ParameterDescriptor descriptor) {
    var signature = new SignatureDescriptor(List.of(descriptor), TypeSet.EMPTY, "");
    var contract = new MemberDescriptor(
      new BilingualString("ПриЗаписи", "OnWrite"),
      MemberKind.METHOD,
      BilingualString.EMPTY,
      TypeSet.EMPTY,
      List.of(signature),
      null,
      false,
      PlatformMetadata.EMPTY,
      false,
      false
    );
    when(eventContractsIndex.getContract(documentContext, "ПриЗаписи")).thenReturn(Optional.of(contract));
  }

  private static ParameterDescriptor parameterDescriptor(TypeSet types, boolean variadic) {
    return new ParameterDescriptor(
      new BilingualString("Источник", "Source"), types, false, BilingualString.EMPTY, "", variadic);
  }

  /** Первым в объявлении стоит другой параметр — искомый оказывается вторым. */
  private void givenPrecedingParameter() {
    when(otherParameter.getName()).thenReturn("Другой");
    when(method.getParameters()).thenReturn(List.of(otherParameter, parameter));
  }
}
