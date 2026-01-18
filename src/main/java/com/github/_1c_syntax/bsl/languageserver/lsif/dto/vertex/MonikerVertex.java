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
 * Вершина моникера для кросс-проектной навигации.
 * <p>
 * Моникеры позволяют связывать символы между разными проектами,
 * что необходимо для Sourcegraph, GitHub Code Navigation и других систем.
 *
 * @param id идентификатор вершины
 * @param type тип элемента (vertex)
 * @param label метка вершины (moniker)
 * @param scheme схема моникера (например, "bsl")
 * @param identifier уникальный идентификатор символа (формат: mdoRef:moduleType:symbolName)
 * @param kind тип моникера: import, export или local
 * @param unique уникальность моникера в рамках документа, проекта или схемы
 */
public record MonikerVertex(
  long id,
  String type,
  String label,
  String scheme,
  String identifier,
  String kind,
  String unique
) {
  public MonikerVertex(long id, String scheme, String identifier, String kind, String unique) {
    this(id, LsifConstants.ElementType.VERTEX, LsifConstants.VertexLabel.MONIKER,
      scheme, identifier, kind, unique);
  }
}
