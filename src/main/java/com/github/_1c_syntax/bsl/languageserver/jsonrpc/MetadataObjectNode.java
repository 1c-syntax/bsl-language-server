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
package com.github._1c_syntax.bsl.languageserver.jsonrpc;

import java.util.List;

/**
 * Узел объекта метаданных верхнего уровня в дереве конфигурации: имя, синоним, тип и реквизиты.
 *
 * @param name               Имя объекта метаданных.
 * @param synonym            Синоним объекта метаданных (пустая строка, если синоним не задан).
 * @param mdoType            Тип объекта метаданных (например, {@code CATALOG}, {@code DOCUMENT}).
 * @param mdoRef             Ссылка на объект метаданных (mdoRef).
 * @param attributes         Реквизиты объекта (без стандартных реквизитов).
 * @param standardAttributes Стандартные реквизиты объекта.
 */
public record MetadataObjectNode(
  String name,
  String synonym,
  String mdoType,
  String mdoRef,
  List<AttributeNode> attributes,
  List<AttributeNode> standardAttributes
) {
}
