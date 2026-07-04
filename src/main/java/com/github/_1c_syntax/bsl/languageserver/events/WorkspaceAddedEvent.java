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
package com.github._1c_syntax.bsl.languageserver.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;
import java.net.URI;

/**
 * Workspace добавлен в провайдер контекстов сервера.
 * <p>
 * Полезная нагрузка — только URI добавленного workspace; контекст сервера в событии не
 * передаётся (на момент рассылки он уже создан и доступен по этому URI).
 */
@Getter
public class WorkspaceAddedEvent extends ApplicationEvent {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * URI корня workspace.
   */
  private final URI workspaceUri;

  /**
   * Создаёт новое событие добавления workspace.
   *
   * @param source       источник события (провайдер контекстов сервера)
   * @param workspaceUri URI корня workspace
   */
  public WorkspaceAddedEvent(Object source, URI workspaceUri) {
    super(source);
    this.workspaceUri = workspaceUri;
  }
}
