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

import java.util.List;

/**
 * Ребро "item" — связь результата с конкретными диапазонами.
 * <p>
 * Связывает результат (referenceResult, definitionResult) с конкретными диапазонами,
 * которые являются определениями или ссылками. Свойство property указывает тип связи.
 *
 * @param id       уникальный идентификатор ребра
 * @param type     тип элемента (всегда "edge")
 * @param label    метка ребра (всегда "item")
 * @param outV     исходящая вершина (ID результата)
 * @param inVs     список входящих вершин (ID диапазонов)
 * @param document ID документа, в котором находятся диапазоны
 * @param property тип связи: "definitions" или "references"
 */
public record ItemEdge(
  long id,
  String type,
  String label,
  long outV,
  List<Long> inVs,
  long document,
  String property
) {
  /**
   * Создаёт ребро item с автоматическим заполнением type и label.
   *
   * @param id       уникальный идентификатор ребра
   * @param outV     ID исходящей вершины (результат)
   * @param inVs     список ID входящих вершин (диапазоны)
   * @param document ID документа
   * @param property тип связи
   */
  public ItemEdge(long id, long outV, List<Long> inVs, long document, String property) {
    this(id, LsifConstants.ElementType.EDGE, LsifConstants.EdgeLabel.ITEM, outV, inVs, document, property);
  }
}
