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
package com.github._1c_syntax.bsl.languageserver.context.computer;

import com.github._1c_syntax.bsl.languageserver.context.OScriptModuleTypeResolver;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ConstructorSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.EventMethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegularMethodSymbol;
import com.github._1c_syntax.bsl.languageserver.types.registry.EventHandlerResolver;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;

import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Метод, являющийся обработчиком платформенного события, регистрируется в дереве символов СРАЗУ
 * как {@link EventMethodSymbol} — {@code context.computer.MethodSymbolComputer} строит его
 * напрямую (через {@code context.symbol.EventHandlerClassifier}, реализованный {@link
 * EventHandlerResolver}), а не как {@link RegularMethodSymbol}, впоследствии подменяемый или
 * оборачиваемый каким-либо индексом.
 */
@SpringBootTest
class MethodSymbolComputerEventClassificationTest {

  @MockitoBean
  private EventHandlerResolver eventHandlerResolver;

  @Autowired
  private OScriptModuleTypeResolver oScriptModuleTypeResolver;

  @Test
  void methodMatchingEventContractIsRegisteredAsEventMethodSymbolDirectly() {
    // given — стаб регистрируется ДО создания документа: MethodSymbolComputer опрашивает
    // классификатор синхронно при обходе AST, то есть уже во время TestUtils.getDocumentContext(...).
    // Стабится именно isEventHandler (сам метод EventHandlerClassifier, который дёргает
    // MethodSymbolComputer), а не lookupContract: eventHandlerResolver — мок, а не spy, поэтому
    // стаб lookupContract не заставит невыстабленный isEventHandler вызвать её "по-настоящему".
    when(eventHandlerResolver.isEventHandler(ArgumentMatchers.any(), ArgumentMatchers.eq("ПриЗаписи")))
      .thenReturn(true);

    // when
    var documentContext = TestUtils.getDocumentContext("""
      Процедура ПриЗаписи(Отказ)
      КонецПроцедуры
      """);
    var method = documentContext.getSymbolTree().getMethodSymbol("ПриЗаписи").orElseThrow();

    // then — реальный подтип дерева, а не RegularMethodSymbol с подменённым kind
    assertThat(method).isInstanceOf(EventMethodSymbol.class);
    assertThat(method.getSymbolKind()).isEqualTo(SymbolKind.Event);
  }

  @Test
  void methodNotMatchingAnyEventContractIsRegularMethodSymbol() {
    when(eventHandlerResolver.isEventHandler(ArgumentMatchers.any(), ArgumentMatchers.anyString()))
      .thenReturn(false);

    var documentContext = TestUtils.getDocumentContext("""
      Процедура ОбычнаяПроцедура()
      КонецПроцедуры
      """);
    var method = documentContext.getSymbolTree().getMethodSymbol("ОбычнаяПроцедура").orElseThrow();

    assertThat(method).isInstanceOf(RegularMethodSymbol.class);
    assertThat(method.getSymbolKind()).isEqualTo(SymbolKind.Method);
  }

  @Test
  void oscriptClassConstructorStaysConstructorSymbolEvenIfNameMatchesEvent() {
    // given — классификатор возвращает true намеренно (как если бы "ПриСозданииОбъекта"
    // совпало с контрактом события — см. EventHandlerResolver.OSCRIPT_CLASS_EVENTS): конструктор
    // всё равно не должен реклассифицироваться (проверяется ДО события в
    // MethodSymbolComputer.createMethodSymbol).
    when(eventHandlerResolver.isEventHandler(ArgumentMatchers.any(), ArgumentMatchers.anyString()))
      .thenReturn(true);
    // Уникальный URI (не общий FAKE_OSCRIPT_DOCUMENT_URI, которым делится множество других
    // тестовых классов): OScriptModuleTypeResolver.register — putIfAbsent, и при переиспользовании
    // общего URI регистрация могла бы молча оказаться no-op'ом, если тот уже зарегистрирован
    // как OScriptModule другим тестом, делящим Spring-контекст.
    var uri = URI.create(
      TestUtils.FAKE_OSCRIPT_DOCUMENT_URI.toString().replace("fake-uri.os", "fake-uri-event-ctor-test.os"));
    oScriptModuleTypeResolver.register(uri, ModuleType.OScriptClass);

    var documentContext = TestUtils.getDocumentContext(uri, """
      Процедура ПриСозданииОбъекта()
      КонецПроцедуры
      """);
    var constructor = documentContext.getSymbolTree().getConstructor().orElseThrow();

    assertThat(constructor).isInstanceOf(ConstructorSymbol.class);
    assertThat(constructor.getSymbolKind()).isEqualTo(SymbolKind.Constructor);
  }
}
