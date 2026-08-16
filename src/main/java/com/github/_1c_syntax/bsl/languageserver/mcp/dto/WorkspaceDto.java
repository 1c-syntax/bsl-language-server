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
package com.github._1c_syntax.bsl.languageserver.mcp.dto;

import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;

import java.net.URI;

/**
 * Зарегистрированное рабочее пространство: 1С-конфигурация или OneScript-проект,
 * исходники которого проиндексированы сервером.
 *
 * @param root URI рабочего пространства — значение, которое инструменты принимают в параметре
 *   {@code root}.
 * @param name Имя рабочего пространства — то же, что {@code name} у workspace folder в LSP:
 *   задаётся клиентом при регистрации, иначе берётся из последнего сегмента {@code root}.
 */
public record WorkspaceDto(URI root, String name) {

  public static WorkspaceDto from(URI workspaceUri) {
    try (var ignored = WorkspaceContextHolder.forUri(workspaceUri)) {
      var name = WorkspaceContextHolder.getName();
      return new WorkspaceDto(workspaceUri, name == null ? workspaceUri.toString() : name);
    }
  }
}
