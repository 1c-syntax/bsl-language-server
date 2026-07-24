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
package com.github._1c_syntax.bsl.languageserver.context.events;

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;

/**
 * Событие начала заполнения контекста сервера — публикуется ПЕРЕД тем, как
 * {@link ServerContext#populateContext(java.util.Collection)} начнёт строить деревья
 * символов документов (в отличие от {@link ServerContextPopulatedEvent}, который летит
 * ПОСЛЕ).
 * <p>
 * К этому моменту {@code configurationRoot} уже задан (без него populate невозможен),
 * поэтому событие — общий для всех «голов» (LSP/CLI/MCP) момент «конфигурация готова,
 * но ни одно дерево ещё не построено». Используется, чтобы зарегистрировать
 * конфигурационные типы до построения деревьев — иначе всё типозависимое в дереве
 * (например, классификация обработчиков событий) считалось бы по ещё не
 * зарегистрированным типам (см. {@code ConfigurationTypesProvider}). В LSP типы обычно
 * уже зарегистрированы раньше — на {@code WorkspaceAddedEvent}, поэтому там обработка
 * идемпотентна.
 */
public class ServerContextPopulatingEvent extends ApplicationEvent {

  @Serial
  private static final long serialVersionUID = -4485675935728156709L;

  private ServerContextPopulatingEvent(ServerContext source) {
    super(source);
  }

  /** Фабричный метод. Public-конструктор намеренно не предоставляется, чтобы класс
   *  не воспринимался Spring/линтерами как кандидат на @Autowire. */
  public static ServerContextPopulatingEvent of(ServerContext source) {
    return new ServerContextPopulatingEvent(source);
  }

  @Override
  public ServerContext getSource() {
    return (ServerContext) super.getSource();
  }
}
