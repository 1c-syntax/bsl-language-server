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
package com.github._1c_syntax.bsl.languageserver.util;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.types.registry.EventHandlerResolver;

import java.util.function.Predicate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Заглушки мок-бина {@link EventHandlerResolver}.
 */
public final class EventHandlerResolverStubs {

  private EventHandlerResolverStubs() {
    // утилитный класс
  }

  /**
   * Проверка обработчиков событий на документ у мока отвечает так же, как застабленный
   * {@link EventHandlerResolver#isEventHandler}: ответы по-прежнему задаются через него.
   *
   * @param resolver мок резолвера.
   */
  public static void delegateEventHandlersOfToIsEventHandler(EventHandlerResolver resolver) {
    when(resolver.eventHandlersOf(any())).thenAnswer(invocation -> {
      DocumentContext documentContext = invocation.getArgument(0);
      return (Predicate<String>) methodName -> resolver.isEventHandler(documentContext, methodName);
    });
  }
}
