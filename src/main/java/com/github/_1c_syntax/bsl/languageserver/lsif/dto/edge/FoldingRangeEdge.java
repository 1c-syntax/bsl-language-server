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
 * Ребро "textDocument/foldingRange" — связь с результатом сворачивания.
 * <p>
 * Связывает документ с результатом сворачиваемых областей (foldingRangeResult).
 *
 * @param id    уникальный идентификатор ребра
 * @param type  тип элемента (всегда "edge")
 * @param label метка ребра (всегда "textDocument/foldingRange")
 * @param outV  исходящая вершина (ID документа)
 * @param inV   входящая вершина (ID результата сворачивания)
 */
public record FoldingRangeEdge(
  long id,
  String type,
  String label,
  long outV,
  long inV
) {
  /**
   * Создаёт ребро foldingRange с автоматическим заполнением type и label.
   *
   * @param id   уникальный идентификатор ребра
   * @param outV ID исходящей вершины (документ)
   * @param inV  ID входящей вершины (результат сворачивания)
   */
  public FoldingRangeEdge(long id, long outV, long inV) {
    this(id, LsifConstants.ElementType.EDGE, LsifConstants.EdgeLabel.FOLDING_RANGE, outV, inV);
  }
}
