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
package com.github._1c_syntax.bsl.languageserver.lsif.dto.edge;

import com.github._1c_syntax.bsl.languageserver.lsif.dto.LsifConstants;

/**
 * Ребро "moniker" — связь диапазона/resultSet с моникером.
 * <p>
 * Связывает набор результатов с моникером для кросс-проектной навигации.
 *
 * @param id    уникальный идентификатор ребра
 * @param type  тип элемента (всегда "edge")
 * @param label метка ребра (всегда "moniker")
 * @param outV  исходящая вершина (ID набора результатов)
 * @param inV   входящая вершина (ID моникера)
 */
public record MonikerEdge(
  long id,
  String type,
  String label,
  long outV,
  long inV
) {
  /**
   * Создаёт ребро moniker с автоматическим заполнением type и label.
   *
   * @param id   уникальный идентификатор ребра
   * @param outV ID исходящей вершины (набор результатов)
   * @param inV  ID входящей вершины (моникер)
   */
  public MonikerEdge(long id, long outV, long inV) {
    this(id, LsifConstants.ElementType.EDGE, LsifConstants.EdgeLabel.MONIKER_EDGE, outV, inV);
  }
}
