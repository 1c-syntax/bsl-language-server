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
package com.github._1c_syntax.bsl.languageserver.context;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.WorkspaceFolder;

import java.net.URI;
import java.nio.file.Path;

/**
 * Контекст рабочей области (workspace).
 * <p>
 * Инкапсулирует информацию о конкретной рабочей области и связанный с ней {@link ServerContext}.
 * Каждая рабочая область имеет свой собственный контекст сервера для изоляции документов и метаданных.
 */
@Getter
@RequiredArgsConstructor
public class WorkspaceContext {
  private final WorkspaceFolder workspaceFolder;
  private final ServerContext serverContext;
  private final Path rootPath;

  /**
   * Получить URI рабочей области.
   *
   * @return URI рабочей области
   */
  public URI getUri() {
    return URI.create(workspaceFolder.getUri());
  }

  /**
   * Получить имя рабочей области.
   *
   * @return имя рабочей области
   */
  public String getName() {
    return workspaceFolder.getName();
  }

  /**
   * Проверить, принадлежит ли документ данной рабочей области.
   *
   * @param documentUri URI документа
   * @return true, если документ принадлежит рабочей области
   */
  public boolean contains(URI documentUri) {
    var documentPath = Path.of(documentUri);
    return documentPath.startsWith(rootPath);
  }
}
