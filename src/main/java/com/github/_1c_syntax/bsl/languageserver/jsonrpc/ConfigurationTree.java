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

import java.util.List;

/**
 * Ответ на запрос <code>workspace/x-configurationTree</code>.
 * <br>
 * Дерево конфигурации рабочей области в разрезе основной конфигурации и её расширений.
 *
 * @param workspaceUri  URI корня рабочей области, для которой построено дерево.
 * @param configuration Узел основной конфигурации.
 * @param extensions    Узлы расширений конфигурации (пустой список, если расширений нет).
 */
public record ConfigurationTree(
  String workspaceUri,
  MdClassNode configuration,
  List<MdClassNode> extensions
) {
}
