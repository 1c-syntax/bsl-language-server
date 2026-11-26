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
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;
import java.net.URI;

/**
 * Событие, публикуемое при удалении документа из контекста сервера.
 * <p>
 * Событие генерируется контекстом сервера {@link ServerContext} при вызове метода
 * {@link ServerContext#removeDocument(URI)} и содержит URI удаленного документа.
 * <p>
 * Подписчики на это событие могут выполнить очистку связанных с документом данных,
 * таких как зарегистрированные ссылки, символы, индексы и другие кэшированные данные.
 * <p>
 * Событие публикуется после того, как документ был удален из внутреннего хранилища
 * контекста сервера.
 *
 * @see ServerContext#removeDocument(URI)
 */
public class ServerContextDocumentRemovedEvent extends ApplicationEvent {

  @Serial
  private static final long serialVersionUID = 2328728948264754219L;

  /**
   * URI удаленного документа.
   */
  @Getter
  private final URI uri;

  /**
   * Создает новое событие удаления документа из контекста сервера.
   *
   * @param source контекст сервера, из которого был удален документ
   * @param uri URI удаленного документа
   */
  public ServerContextDocumentRemovedEvent(ServerContext source, URI uri) {
    super(source);
    this.uri = uri;
  }

  @Override
  public ServerContext getSource() {
    return (ServerContext) super.getSource();
  }
}
