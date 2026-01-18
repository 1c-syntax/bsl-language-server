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
 * Вершина диапазона в документе.
 * <p>
 * Представляет текстовый диапазон (позицию символа, идентификатора и т.д.)
 * в документе. Диапазоны связываются с результатами (hover, definition и т.д.)
 * через resultSet и соответствующие рёбра.
 *
 * @param id    уникальный идентификатор вершины
 * @param type  тип элемента (всегда "vertex")
 * @param label метка вершины (всегда "range")
 * @param start начальная позиция диапазона
 * @param end   конечная позиция диапазона
 */
public record RangeVertex(
  long id,
  String type,
  String label,
  Position start,
  Position end
) {
  /**
   * Создаёт вершину диапазона с автоматическим заполнением type и label.
   *
   * @param id    уникальный идентификатор вершины
   * @param start начальная позиция диапазона
   * @param end   конечная позиция диапазона
   */
  public RangeVertex(long id, Position start, Position end) {
    this(id, LsifConstants.ElementType.VERTEX, LsifConstants.VertexLabel.RANGE, start, end);
  }

  /**
   * Позиция в документе.
   * <p>
   * Использует нумерацию с нуля (0-based) для строк и символов.
   *
   * @param line      номер строки (начиная с 0)
   * @param character номер символа в строке (начиная с 0)
   */
  public record Position(int line, int character) {}
}
