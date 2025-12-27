/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;

/**
 * Событие, публикуемое при закрытии документа в контексте сервера.
 * <p>
 * Событие генерируется контекстом сервера {@link ServerContext} при вызове метода
 * {@link ServerContext#closeDocument(DocumentContext)} и содержит закрытый документ.
 * <p>
 * Подписчики на это событие могут выполнить очистку связанных с документом данных,
 * таких как кэшированные семантические токены и другие временные данные.
 * <p>
 * Событие публикуется после того, как документ был помечен как закрытый.
 *
 * @see ServerContext#closeDocument(DocumentContext)
 */
public class ServerContextDocumentClosedEvent extends ApplicationEvent {

  @Serial
  private static final long serialVersionUID = 8274629847264754220L;

  /**
   * Закрытый документ.
   */
  @Getter
  private final DocumentContext documentContext;

  /**
   * Создает новое событие закрытия документа в контексте сервера.
   *
   * @param source контекст сервера, в котором был закрыт документ
   * @param documentContext закрытый документ
   */
  public ServerContextDocumentClosedEvent(ServerContext source, DocumentContext documentContext) {
    super(source);
    this.documentContext = documentContext;
  }

  @Override
  public ServerContext getSource() {
    return (ServerContext) super.getSource();
  }
}
