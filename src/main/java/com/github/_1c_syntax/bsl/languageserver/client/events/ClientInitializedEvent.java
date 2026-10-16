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
package com.github._1c_syntax.bsl.languageserver.client.events;

import org.eclipse.lsp4j.services.LanguageServer;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;

/**
 * Событие «клиент закончил инициализацию» — пришло уведомление {@code initialized}.
 * <p>
 * Публикуется при получении уведомления, до его обработки. С этого момента клиенту можно
 * слать запросы, в том числе на создание индикаторов хода работы. В качестве источника
 * события содержит ссылку на {@link LanguageServer}.
 */
public class ClientInitializedEvent extends ApplicationEvent {

  @Serial
  private static final long serialVersionUID = -3415296581187320521L;

  public ClientInitializedEvent(LanguageServer source) {
    super(source);
  }

  @Override
  public LanguageServer getSource() {
    return (LanguageServer) super.getSource();
  }
}
