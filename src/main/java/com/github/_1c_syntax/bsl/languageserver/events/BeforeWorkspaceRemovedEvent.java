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
 * Событие, публикуемое ДО удаления workspace из провайдера контекстов сервера.
 * <p>
 * Несёт только URI удаляемого workspace. Публикуется, пока контекст сервера ещё
 * существует, поэтому подписчики, которым нужен сам контекст, успевают
 * зарезолвить его по URI через свои зависимости.
 * <p>
 * Подписчики используют это событие для освобождения связанных с workspace ресурсов.
 *
 * @see WorkspaceRemovedEvent
 */
@Getter
public class BeforeWorkspaceRemovedEvent extends ApplicationEvent {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * URI корня workspace.
   */
  private final URI workspaceUri;

  /**
   * Создаёт новое событие перед удалением workspace.
   *
   * @param source       источник события (провайдер контекстов сервера)
   * @param workspaceUri URI корня workspace
   */
  public BeforeWorkspaceRemovedEvent(Object source, URI workspaceUri) {
    super(source);
    this.workspaceUri = workspaceUri;
  }
}
