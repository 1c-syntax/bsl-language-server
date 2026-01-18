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
 * Вершина документа.
 * <p>
 * Представляет файл исходного кода в LSIF-графе. Документ содержит
 * диапазоны (range), связанные с ним через ребро contains.
 *
 * @param id         уникальный идентификатор вершины
 * @param type       тип элемента (всегда "vertex")
 * @param label      метка вершины (всегда "document")
 * @param uri        URI документа (например, "file:///path/to/file.bsl")
 * @param languageId идентификатор языка документа (например, "bsl" или "oscript")
 */
public record DocumentVertex(
  long id,
  String type,
  String label,
  String uri,
  String languageId
) {
  /**
   * Создаёт вершину документа с автоматическим заполнением type и label.
   *
   * @param id         уникальный идентификатор вершины
   * @param uri        URI документа
   * @param languageId идентификатор языка документа
   */
  public DocumentVertex(long id, String uri, String languageId) {
    this(id, LsifConstants.ElementType.VERTEX, LsifConstants.VertexLabel.DOCUMENT, uri, languageId);
  }
}
