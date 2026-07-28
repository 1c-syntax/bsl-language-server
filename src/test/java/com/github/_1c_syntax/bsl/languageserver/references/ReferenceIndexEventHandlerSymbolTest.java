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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.symbol.EventMethodSymbol;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.EventHandlerResolver;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Регрессия: объявленный обработчик платформенного события попадает в дерево символов как
 * {@link EventMethodSymbol} (display-вид {@link SymbolKind#Event}), но его прямые вызовы
 * {@link ReferenceIndex#methodCallOccurrence} индексирует под каноническим {@link SymbolKind#Method}.
 * Читающая сторона {@link ReferenceIndex#getReferencesTo} строит ключ по настоящему
 * {@code getSymbolKind()} символа ({@code Event}) и без канонизации в {@link ReferenceIndex#indexedKindOf}
 * никогда не совпала бы с сохранённым {@code Method} — Find References / Call Hierarchy incoming молча
 * не нашли бы ни одного прямого вызова обработчика.
 */
@CleanupContextBeforeClassAndAfterClass
class ReferenceIndexEventHandlerSymbolTest extends AbstractServerContextAwareTest {

  @Autowired
  private ReferenceIndexFiller referenceIndexFiller;
  @Autowired
  private ReferenceIndex referenceIndex;

  @MockitoBean
  EventHandlerResolver eventHandlerResolver;

  @BeforeEach
  void resetResolver() {
    when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.anyString()))
      .thenReturn(Optional.empty());
    // Классификация метода в EventMethodSymbol идёт через isEventHandler; у мок-бина делегируем
    // в lookupContract (как в реальном бине), чтобы тест задавал только lookupContract.
    when(eventHandlerResolver.isEventHandler(ArgumentMatchers.any(), ArgumentMatchers.anyString()))
      .thenAnswer(inv -> eventHandlerResolver.lookupContract(inv.getArgument(0), inv.getArgument(1)).isPresent());
  }

  @Test
  void getReferencesToEventHandlerFindsDirectCall() {
    // given — ПриЗаписи классифицируется как обработчик события и вызывается напрямую из Вызвать()
    var contract = MemberDescriptor.event(
      "ПриЗаписи",
      "Возникает при записи объекта.",
      List.of(new SignatureDescriptor(List.of(), TypeSet.EMPTY, ""))
    );
    when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ПриЗаписи")))
      .thenReturn(Optional.of(contract));

    var documentContext = TestUtils.getDocumentContext("""
      Процедура Вызвать()
        ПриЗаписи(Ложь);
      КонецПроцедуры

      Процедура ПриЗаписи(Отказ)
      КонецПроцедуры
      """);
    referenceIndexFiller.fill(documentContext);

    var eventHandler = documentContext.getSymbolTree().getMethodSymbol("ПриЗаписи").orElseThrow();
    // предусловие регрессии: символ — именно EventMethodSymbol с display-видом Event
    assertThat(eventHandler).isInstanceOf(EventMethodSymbol.class);
    assertThat(eventHandler.getSymbolKind()).isEqualTo(SymbolKind.Event);

    // when
    var references = referenceIndex.getReferencesTo(eventHandler);

    // then — прямой вызов ПриЗаписи(Ложь) найден несмотря на рассинхрон Event (символ) и Method (вхождение)
    assertThat(references)
      .as("Find References на обработчик события должен находить его прямой вызов")
      .hasSize(1);
  }
}
