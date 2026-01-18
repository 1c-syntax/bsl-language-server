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

/**
 * Вершина результата hover.
 * <p>
 * Содержит информацию для всплывающей подсказки при наведении на символ.
 * Связывается с resultSet через ребро textDocument/hover.
 *
 * @param id     уникальный идентификатор вершины
 * @param type   тип элемента (всегда "vertex")
 * @param label  метка вершины (всегда "hoverResult")
 * @param result содержимое hover-подсказки
 */
public record HoverResultVertex(
  long id,
  String type,
  String label,
  HoverContent result
) {
  /**
   * Создаёт вершину результата hover с автоматическим заполнением type и label.
   *
   * @param id     уникальный идентификатор вершины
   * @param result содержимое hover-подсказки
   */
  public HoverResultVertex(long id, HoverContent result) {
    this(id, LsifConstants.ElementType.VERTEX, LsifConstants.VertexLabel.HOVER_RESULT, result);
  }

  /**
   * Содержимое hover.
   *
   * @param contents контент для отображения
   */
  public record HoverContent(Contents contents) {}

  /**
   * Контент для hover.
   *
   * @param kind  тип контента (например, "markdown" или "plaintext")
   * @param value текстовое содержимое
   */
  public record Contents(String kind, String value) {}
}
