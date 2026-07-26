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
package com.github._1c_syntax.bsl.languageserver.types.index;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.events.ConfigurationTypesRegisteredEvent;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.registry.EventHandlerResolver;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.assertj.core.api.Assertions.assertThat;

class EventContractsIndexTest extends AbstractServerContextAwareTest {

  @Autowired
  private EventContractsIndex eventContractsIndex;

  @Autowired
  private ApplicationEventPublisher eventPublisher;

  @MockitoBean
  EventHandlerResolver eventHandlerResolver;

  @BeforeEach
  void resetResolver() {
    when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.anyString()))
      .thenReturn(Optional.empty());
    when(eventHandlerResolver.allEvents(ArgumentMatchers.any()))
      .thenReturn(List.of());
  }

  @Test
  void emptyDocumentYieldsNoContract() {
    // Документ без методов — buildFor возвращает пустую карту (ветка
    // methods.isEmpty()), getContract отдаёт Optional.empty().
    var documentContext = TestUtils.getDocumentContext("");

    var contract = eventContractsIndex.getContract(documentContext, "ПриЗаписи");

    assertThat(contract).isEmpty();
  }

  @Test
  void getAllContractsDelegatesToResolver() {
    var documentContext = TestUtils.getDocumentContext("");
    var contracts = List.of(MemberDescriptor.event("ПриЗаписи", "", List.of()));
    when(eventHandlerResolver.allEvents(documentContext)).thenReturn(contracts);

    var first = eventContractsIndex.getAllContracts(documentContext);
    var second = eventContractsIndex.getAllContracts(documentContext);

    assertThat(first).isEqualTo(contracts);
    assertThat(second).isEqualTo(contracts);
    // Отдельного кэша нет: события зависят от типа и уже мемоизированы источником
    // (getMembers/globalEvents), поэтому каждый вызов делегирует в резолвер.
    verify(eventHandlerResolver, times(2)).allEvents(documentContext);
  }

  @Test
  void getAllContractsReflectsReRegisteredTypes() {
    var documentContext = TestUtils.getDocumentContext("");
    when(eventHandlerResolver.allEvents(documentContext))
      .thenReturn(List.of(MemberDescriptor.event("Первое", "", List.of())))
      .thenReturn(List.of(MemberDescriptor.event("Второе", "", List.of())));

    var beforeReload = eventContractsIndex.getAllContracts(documentContext);
    eventPublisher.publishEvent(new ConfigurationTypesRegisteredEvent(documentContext.getServerContext()));
    var afterReload = eventContractsIndex.getAllContracts(documentContext);

    assertThat(beforeReload).extracting(MemberDescriptor::name).containsExactly("Первое");
    assertThat(afterReload).extracting(MemberDescriptor::name).containsExactly("Второе");
  }
}
