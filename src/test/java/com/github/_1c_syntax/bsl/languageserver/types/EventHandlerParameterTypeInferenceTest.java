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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.EventHandlerResolver;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Покрытие веток {@code ExpressionTypeInferencer.eventHandlerParameterTypes}:
 * тип параметра обработчика берётся из контракта платформенного события, когда
 * у переменной нет ни doc-аннотации, ни hyperlink-источника.
 */
class EventHandlerParameterTypeInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private ExpressionTypeInferencer inferencer;

  @MockitoBean
  EventHandlerResolver eventHandlerResolver;

  @BeforeEach
  void resetResolver() {
    Mockito.when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.anyString()))
      .thenReturn(Optional.empty());
  }

  @Test
  void parameterTypeResolvedFromContract() {
    // contract содержит типизированный параметр — inference должен вернуть его
    // тип для параметра обработчика, у которого собственного типа нет.
    var contract = MemberDescriptor.event("ПриЗаписи", "",
      List.of(new SignatureDescriptor(List.of(
        new ParameterDescriptor(BilingualString.of("Отказ", "Cancel"),
          TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Булево")),
          false, BilingualString.EMPTY, "")
      ), TypeSet.EMPTY, "")));
    Mockito.when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ПриЗаписи")))
      .thenReturn(Optional.of(contract));

    var documentContext = TestUtils.getDocumentContext("""
      Процедура ПриЗаписи(Отказ) Экспорт
      КонецПроцедуры
      """);
    var method = documentContext.getSymbolTree().getMethodSymbol("ПриЗаписи").orElseThrow();
    var variable = documentContext.getSymbolTree().getVariableSymbol("Отказ", method).orElseThrow();

    var types = inferencer.inferSymbol(variable);

    assertThat(types.refs()).extracting(TypeRef::qualifiedName).contains("Булево");
  }

  @Test
  void parameterBeyondContractWithoutVariadicYieldsEmpty() {
    // Лишний параметр (за пределами контракта, последний не variadic) —
    // eventHandlerParameterTypes возвращает TypeSet.EMPTY.
    var contract = MemberDescriptor.event("ПриЗаписи", "",
      List.of(new SignatureDescriptor(List.of(
        new ParameterDescriptor(BilingualString.of("Отказ", "Cancel"),
          TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Булево")),
          false, BilingualString.EMPTY, "")
      ), TypeSet.EMPTY, "")));
    Mockito.when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ПриЗаписи")))
      .thenReturn(Optional.of(contract));

    var documentContext = TestUtils.getDocumentContext("""
      Процедура ПриЗаписи(Отказ, Лишний) Экспорт
      КонецПроцедуры
      """);
    var method = documentContext.getSymbolTree().getMethodSymbol("ПриЗаписи").orElseThrow();
    var lishniy = documentContext.getSymbolTree().getVariableSymbol("Лишний", method).orElseThrow();

    var types = inferencer.inferSymbol(lishniy);

    // Если для лишнего параметра inference что-то вернул — это не Булево из contract.
    assertThat(types.refs()).extracting(TypeRef::qualifiedName)
      .as("contract без variadic не покрывает лишний параметр — тип не должен быть Булево")
      .isEmpty();
  }

  @Test
  void parameterWithEmptyContractSignaturesYieldsEmpty() {
    // contract без signatures — eventHandlerParameterTypes возвращает EMPTY.
    var contract = MemberDescriptor.event("ПриЗаписи", "", List.of());
    Mockito.when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ПриЗаписи")))
      .thenReturn(Optional.of(contract));

    var documentContext = TestUtils.getDocumentContext("""
      Процедура ПриЗаписи(Отказ) Экспорт
      КонецПроцедуры
      """);
    var method = documentContext.getSymbolTree().getMethodSymbol("ПриЗаписи").orElseThrow();
    var variable = documentContext.getSymbolTree().getVariableSymbol("Отказ", method).orElseThrow();

    var types = inferencer.inferSymbol(variable);

    assertThat(types.refs()).isEmpty();
  }
}
