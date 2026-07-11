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
package com.github._1c_syntax.bsl.languageserver.jsonrpc;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

/**
 * Параметры запроса <code>workspace/x-configurationTree</code>.
 * <br>
 * Идентификатор рабочей области обязателен: должен быть задан {@link #workspaceUri}
 * либо {@link #workspaceName} (при указании обоих приоритет у {@link #workspaceUri}).
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class ConfigurationTreeParams {

  /**
   * URI корня рабочей области, для которой строится дерево конфигурации.
   */
  @Nullable
  private String workspaceUri;

  /**
   * Имя рабочей области, для которой строится дерево конфигурации.
   * <br>
   * Используется, если {@link #workspaceUri} не задан.
   */
  @Nullable
  private String workspaceName;
}
