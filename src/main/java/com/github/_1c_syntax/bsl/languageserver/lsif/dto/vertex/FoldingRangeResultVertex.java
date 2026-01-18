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
package com.github._1c_syntax.bsl.languageserver.lsif.dto.vertex;

import com.github._1c_syntax.bsl.languageserver.lsif.dto.LsifConstants;

import java.util.List;

/**
 * Вершина результата сворачиваемых областей.
 * <p>
 * Содержит информацию о всех сворачиваемых областях в документе.
 * Связывается с документом через ребро textDocument/foldingRange.
 *
 * @param id     уникальный идентификатор вершины
 * @param type   тип элемента (всегда "vertex")
 * @param label  метка вершины (всегда "foldingRangeResult")
 * @param result список сворачиваемых областей
 */
public record FoldingRangeResultVertex(
  long id,
  String type,
  String label,
  List<FoldingRangeInfo> result
) {
  /**
   * Создаёт вершину результата сворачивания с автоматическим заполнением type и label.
   *
   * @param id     уникальный идентификатор вершины
   * @param result список сворачиваемых областей
   */
  public FoldingRangeResultVertex(long id, List<FoldingRangeInfo> result) {
    this(id, LsifConstants.ElementType.VERTEX, LsifConstants.VertexLabel.FOLDING_RANGE_RESULT, result);
  }

  /**
   * Информация о сворачиваемой области.
   *
   * @param startLine      начальная строка области
   * @param startCharacter начальный символ области
   * @param endLine        конечная строка области
   * @param endCharacter   конечный символ области
   * @param kind           тип области (например, "region", "comment", "imports")
   */
  public record FoldingRangeInfo(
    int startLine,
    int startCharacter,
    int endLine,
    int endCharacter,
    String kind
  ) {}
}
