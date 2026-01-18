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
 * Вершина метаданных LSIF-индекса.
 * <p>
 * Содержит информацию о версии протокола, корне проекта и инструменте,
 * создавшем индекс. Должна быть первым элементом в LSIF-файле.
 *
 * @param id              уникальный идентификатор вершины
 * @param type            тип элемента (всегда "vertex")
 * @param label           метка вершины (всегда "metaData")
 * @param version         версия LSIF-протокола (например, "0.6.0")
 * @param projectRoot     URI корневой директории проекта
 * @param positionEncoding кодировка позиций (например, "utf-16")
 * @param toolInfo        информация об инструменте, создавшем индекс
 */
public record MetaDataVertex(
  long id,
  String type,
  String label,
  String version,
  String projectRoot,
  String positionEncoding,
  ToolInfo toolInfo
) {
  /**
   * Создаёт вершину метаданных с автоматическим заполнением type и label.
   *
   * @param id               уникальный идентификатор вершины
   * @param version          версия LSIF-протокола
   * @param projectRoot      URI корневой директории проекта
   * @param positionEncoding кодировка позиций
   * @param toolInfo         информация об инструменте
   */
  public MetaDataVertex(long id, String version, String projectRoot, String positionEncoding, ToolInfo toolInfo) {
    this(id, LsifConstants.ElementType.VERTEX, LsifConstants.VertexLabel.META_DATA,
      version, projectRoot, positionEncoding, toolInfo);
  }

  /**
   * Информация об инструменте, создавшем индекс.
   *
   * @param name    название инструмента
   * @param version версия инструмента
   */
  public record ToolInfo(String name, String version) {}
}
