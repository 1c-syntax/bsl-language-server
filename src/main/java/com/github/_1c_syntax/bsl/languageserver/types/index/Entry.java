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
package com.github._1c_syntax.bsl.languageserver.types.index;

import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.SymbolTag;

import java.net.URI;
import java.util.List;

/**
 * Неизменяемая запись индекса символов рабочей области: всё необходимое для построения
 * {@link org.eclipse.lsp4j.WorkspaceSymbol} без повторного обхода дерева символов.
 *
 * @param uri           URI документа-источника
 * @param name          исходное имя символа
 * @param lowerName     lowercase-форма имени для сопоставления
 * @param kind          вид символа
 * @param range         диапазон символа в документе
 * @param tags          теги символа (например, {@link SymbolTag#Deprecated})
 * @param containerName готовое имя контейнера (представление ссылки на объект метаданных) либо
 *                      пустая строка, если документ не связан с объектом метаданных
 */
public record Entry(
  URI uri,
  String name,
  String lowerName,
  SymbolKind kind,
  Range range,
  List<SymbolTag> tags,
  String containerName
) {
}
