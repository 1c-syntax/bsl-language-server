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
 * Вершина результата символов документа.
 * <p>
 * Содержит иерархическую структуру символов документа (модули, процедуры, функции).
 * Связывается с документом через ребро textDocument/documentSymbol.
 *
 * @param id     уникальный идентификатор вершины
 * @param type   тип элемента (всегда "vertex")
 * @param label  метка вершины (всегда "documentSymbolResult")
 * @param result список символов документа
 */
public record DocumentSymbolResultVertex(
  long id,
  String type,
  String label,
  List<DocumentSymbolInfo> result
) {
  /**
   * Создаёт вершину результата символов с автоматическим заполнением type и label.
   *
   * @param id     уникальный идентификатор вершины
   * @param result список символов документа
   */
  public DocumentSymbolResultVertex(long id, List<DocumentSymbolInfo> result) {
    this(id, LsifConstants.ElementType.VERTEX, LsifConstants.VertexLabel.DOCUMENT_SYMBOL_RESULT, result);
  }

  /**
   * Информация о символе документа.
   *
   * @param name           имя символа
   * @param kind           тип символа (числовое значение из LSP SymbolKind)
   * @param range          полный диапазон символа
   * @param selectionRange диапазон имени символа
   * @param children       дочерние символы
   */
  public record DocumentSymbolInfo(
    String name,
    int kind,
    RangeInfo range,
    RangeInfo selectionRange,
    List<DocumentSymbolInfo> children
  ) {}

  /**
   * Информация о диапазоне.
   *
   * @param start начальная позиция
   * @param end   конечная позиция
   */
  public record RangeInfo(PositionInfo start, PositionInfo end) {}

  /**
   * Информация о позиции.
   *
   * @param line      номер строки (начиная с 0)
   * @param character номер символа в строке (начиная с 0)
   */
  public record PositionInfo(int line, int character) {}
}
