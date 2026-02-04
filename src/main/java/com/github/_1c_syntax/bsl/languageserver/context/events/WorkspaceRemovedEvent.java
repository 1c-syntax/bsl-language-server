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

import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import lombok.Getter;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;
import java.net.URI;

/**
 * Событие, публикуемое ПОСЛЕ удаления workspace из провайдера.
 * <p>
 * Событие генерируется {@link ServerContextProvider} ПОСЛЕ вызова метода
 * {@link ServerContextProvider#removeWorkspace(WorkspaceFolder)} и содержит URI workspace.
 * <p>
 * К моменту получения этого события контекст сервера уже удалён и недоступен.
 * Используйте {@link BeforeWorkspaceRemovedEvent} если нужен доступ к контексту.
 *
 * @see ServerContextProvider#removeWorkspace(WorkspaceFolder)
 * @see BeforeWorkspaceRemovedEvent
 */
@Getter
public class WorkspaceRemovedEvent extends ApplicationEvent {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * URI корня удалённого workspace.
   */
  private final URI workspaceUri;

  /**
   * Создает новое событие удаления workspace.
   *
   * @param source провайдер контекстов сервера
   * @param workspaceUri URI корня удалённого workspace
   */
  public WorkspaceRemovedEvent(ServerContextProvider source, URI workspaceUri) {
    super(source);
    this.workspaceUri = workspaceUri;
  }

  @Override
  public ServerContextProvider getSource() {
    return (ServerContextProvider) super.getSource();
  }
}
