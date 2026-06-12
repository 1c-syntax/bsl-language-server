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
package com.github._1c_syntax.bsl.languageserver.hover;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.EventHandlerResolver;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Location;
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
 * Hover для метода, у которого выставлен {@code eventContract}, содержит
 * шапку «Обработчик события платформы: <имя>» и платформенное описание
 * события из bsl-context.
 */
@CleanupContextBeforeClassAndAfterClass
class MethodSymbolMarkupContentBuilderEventHandlerTest extends AbstractServerContextAwareTest {

  @Autowired
  private MethodSymbolMarkupContentBuilder markupContentBuilder;

  @MockitoBean
  EventHandlerResolver eventHandlerResolver;

  @BeforeEach
  void resetResolver() {
    Mockito.when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.anyString()))
      .thenReturn(Optional.empty());
  }

  @Test
  void hoverIncludesEventHandlerHeader() {
    // given
    var contract = MemberDescriptor.event(
      "ПриЗаписи",
      "Возникает при записи объекта.",
      List.of(new SignatureDescriptor(List.of(), TypeSet.EMPTY, ""))
    );
    Mockito.when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ПриЗаписи")))
      .thenReturn(Optional.of(contract));

    var src = """
      Процедура ПриЗаписи(Отказ)
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(src);
    var method = documentContext.getSymbolTree().getMethodSymbol("ПриЗаписи").orElseThrow();

    // when
    var content = markupContentBuilder.getContent(referenceTo(documentContext, method)).getValue();

    // then
    assertThat(content)
      .contains("Обработчик события платформы")
      .contains("ПриЗаписи")
      .contains("Возникает при записи объекта.");
  }

  @Test
  void hoverWithContractRendersParameterTypesFromContract() {
    // given — контракт с типизированным параметром Отказ:Булево
    var cancelParam = new ParameterDescriptor(
      BilingualString.of("Отказ", "Cancel"),
      TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Булево")),
      false,
      BilingualString.of("Признак отказа от записи.", ""),
      "");
    var contract = MemberDescriptor.event(
      "ПриЗаписи",
      "Возникает при записи объекта.",
      List.of(new SignatureDescriptor(List.of(cancelParam), TypeSet.EMPTY, ""))
    );
    Mockito.when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ПриЗаписи")))
      .thenReturn(Optional.of(contract));

    var src = """
      Процедура ПриЗаписи(Отказ)
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(src);
    var method = documentContext.getSymbolTree().getMethodSymbol("ПриЗаписи").orElseThrow();

    // when
    var content = markupContentBuilder.getContent(referenceTo(documentContext, method)).getValue();

    // then
    assertThat(content)
      .contains("Отказ")
      .contains("Булево")
      .contains("Признак отказа от записи.");
  }

  @Test
  void hoverWithoutEventContractHasNoHandlerHeader() {
    // given — resolver по умолчанию возвращает Optional.empty().
    var src = """
      Процедура ОбычныйМетод()
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(src);
    var method = documentContext.getSymbolTree().getMethodSymbol("ОбычныйМетод").orElseThrow();

    // when
    var content = markupContentBuilder.getContent(referenceTo(documentContext, method)).getValue();

    // then
    assertThat(content).doesNotContain("Обработчик события платформы");
  }

  private Reference referenceTo(DocumentContext documentContext, MethodSymbol method) {
    var loc = new Location(documentContext.getUri().toString(), method.getSubNameRange());
    return Reference.of(documentContext.getSymbolTree().getModule(),
      (SourceDefinedSymbol) method, loc);
  }
}
