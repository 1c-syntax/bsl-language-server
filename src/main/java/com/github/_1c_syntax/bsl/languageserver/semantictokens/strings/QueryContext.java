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
package com.github._1c_syntax.bsl.languageserver.semantictokens.strings;

import org.antlr.v4.runtime.Token;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Контекст для строк, содержащих запросы SDBL.
 *
 * @param sdblTokensByLine  токены SDBL, сгруппированные по номеру строки
 * @param astTokenOverrides переопределения типов токенов на основе AST
 * @param skipPositions     позиции токенов, которые нужно пропустить при обработке
 */
public record QueryContext(
  Map<Integer, List<Token>> sdblTokensByLine,
  Map<TokenPosition, AstTokenInfo> astTokenOverrides,
  Set<TokenPosition> skipPositions
) {
}

