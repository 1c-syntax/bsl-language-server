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
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
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
 * Hover на параметре метода-обработчика: описание берётся из контракта
 * платформенного события (а не из BSL-doc-шапки), позиционно. Имена параметров
 * в коде могут не совпадать с контрактом — сопоставление по индексу.
 */
@CleanupContextBeforeClassAndAfterClass
class VariableSymbolMarkupContentBuilderEventHandlerTest extends AbstractServerContextAwareTest {

  @Autowired
  private VariableSymbolMarkupContentBuilder markupContentBuilder;

  @MockitoBean
  EventHandlerResolver eventHandlerResolver;

  @BeforeEach
  void resetResolver() {
    Mockito.when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.anyString()))
      .thenReturn(Optional.empty());
  }

  @Test
  void parameterHoverShowsDescriptionFromContract() {
    var contract = MemberDescriptor.event("ПриЗаписи", "",
      List.of(new SignatureDescriptor(List.of(
        new ParameterDescriptor(BilingualString.of("Отказ", "Cancel"),
          TypeSet.EMPTY, false,
          BilingualString.of("Признак отказа от записи.", ""), "")
      ), TypeSet.EMPTY, "")));
    Mockito.when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ПриЗаписи")))
      .thenReturn(Optional.of(contract));

    var src = """
      Процедура ПриЗаписи(СвойОтказ) Экспорт
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(src);
    var content = paramHover(documentContext, "ПриЗаписи", "СвойОтказ");

    assertThat(content).contains("Признак отказа от записи.");
  }

  @Test
  void parameterHoverHasNoContractSectionWhenResolverEmpty() {
    var src = """
      Процедура Обычный(Отказ) Экспорт
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(src);
    // resolver по умолчанию возвращает Optional.empty() — секция события не добавляется.
    var content = paramHover(documentContext, "Обычный", "Отказ");

    assertThat(content).doesNotContain("Признак отказа от записи.");
  }

  @Test
  void parameterHoverNoExtraContentWhenContractHasNoParameters() {
    // Контракт без подходящего параметра по позиции → дополнительная
    // секция не добавляется, hover собирает базовое наполнение.
    var contract = MemberDescriptor.event("ПриЗаписи", "",
      List.of(new SignatureDescriptor(List.of(), TypeSet.EMPTY, "")));
    Mockito.when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ПриЗаписи")))
      .thenReturn(Optional.of(contract));

    var src = """
      Процедура ПриЗаписи(СвойОтказ) Экспорт
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(src);
    var content = paramHover(documentContext, "ПриЗаписи", "СвойОтказ");

    assertThat(content).doesNotContain("Признак отказа от записи.");
  }

  @Test
  void parameterHoverEmptyContractSignaturesGivesNoDescription() {
    // Contract с пустым signatures (parameterAt L318) — описание не добавляется.
    var contract = MemberDescriptor.event("ПриЗаписи", "", List.of());
    Mockito.when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ПриЗаписи")))
      .thenReturn(Optional.of(contract));

    var src = """
      Процедура ПриЗаписи(СвойОтказ) Экспорт
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(src);
    var content = paramHover(documentContext, "ПриЗаписи", "СвойОтказ");

    assertThat(content).doesNotContain("Признак отказа от записи.");
  }

  @Test
  void parameterHoverBeyondContractWithoutVariadicGivesNoDescription() {
    // У метода больше параметров чем в contract, последний не variadic.
    // parameterAt: index >= params.size() && !variadic → empty (L327).
    var contract = MemberDescriptor.event("ПриЗаписи", "",
      List.of(new SignatureDescriptor(List.of(
        new ParameterDescriptor(BilingualString.of("Отказ", "Cancel"),
          TypeSet.EMPTY, false,
          BilingualString.of("Признак отказа.", ""), "")
      ), TypeSet.EMPTY, "")));
    Mockito.when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ПриЗаписи")))
      .thenReturn(Optional.of(contract));

    var src = """
      Процедура ПриЗаписи(Отказ, Лишний) Экспорт
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(src);
    var content = paramHover(documentContext, "ПриЗаписи", "Лишний");

    // На «Лишнем» описание из contract отсутствует — index за пределами параметров,
    // последний параметр контракта не variadic.
    assertThat(content).doesNotContain("Признак отказа.");
  }

  private String paramHover(DocumentContext documentContext, String methodName, String paramName) {
    var symbolTree = documentContext.getSymbolTree();
    var method = symbolTree.getMethodSymbol(methodName).orElseThrow();
    var variable = symbolTree.getVariableSymbol(paramName, method).orElseThrow();
    return markupContentBuilder.getContent(referenceTo(documentContext, variable)).getValue();
  }

  private Reference referenceTo(DocumentContext documentContext, VariableSymbol variable) {
    var loc = new Location(documentContext.getUri().toString(), variable.getVariableNameRange());
    return Reference.of(documentContext.getSymbolTree().getModule(),
      (SourceDefinedSymbol) variable, loc);
  }
}
