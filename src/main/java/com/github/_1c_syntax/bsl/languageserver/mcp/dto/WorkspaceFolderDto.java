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
import java.util.Objects;
import java.util.Optional;

/**
 * Зарегистрированная рабочая папка — workspace folder в терминах LSP: 1С-конфигурация или
 * OneScript-проект, исходники которого проиндексированы сервером.
 *
 * Форма повторяет {@code WorkspaceFolder} из LSP: {@code uri} + {@code name}.
 *
 * @param uri URI рабочей папки — значение, которое инструменты принимают в параметре
 *   {@code workspaceFolder}.
 * @param name Имя рабочей папки — то же, что {@code name} у workspace folder в LSP:
 *   задаётся клиентом при регистрации, иначе берётся из последнего сегмента {@code uri}.
 */
public record WorkspaceFolderDto(URI uri, String name) {

  public static WorkspaceFolderDto from(URI workspaceUri) {
    try (var ignored = WorkspaceContextHolder.forUri(workspaceUri)) {
      // Имя после forUri всегда есть: незарегистрированная папка отсекается там же.
      return new WorkspaceFolderDto(workspaceUri, Objects.requireNonNull(WorkspaceContextHolder.getName()));
    }
  }

  /**
   * Описать папку из снятого ранее снимка набора папок.
   *
   * @param workspaceUri URI рабочей папки из снимка.
   * @return Описание папки либо пусто, если папку успели снять с регистрации: снимок набора
   *   не замораживает реестр имён, и папка могла уйти между снимком и чтением имени.
   */
  public static Optional<WorkspaceFolderDto> fromSnapshot(URI workspaceUri) {
    try {
      return Optional.of(from(workspaceUri));
    } catch (IllegalStateException alreadyUnregistered) {
      return Optional.empty();
    }
  }
}
