/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.semantictokens;

/**
 * Запись для хранения информации о семантическом токене.
 * <p>
 * Содержит позицию токена в документе, его длину, тип и модификаторы.
 * Индексы типов и модификаторов соответствуют легенде семантических токенов
 * {@link org.eclipse.lsp4j.SemanticTokensLegend}.
 *
 * @param line      Номер строки токена (0-индексированный)
 * @param start     Начальная позиция токена в строке (0-индексированная)
 * @param length    Длина токена в символах
 * @param type      Индекс типа токена в легенде
 * @param modifiers Битовая маска модификаторов токена
 */
public record SemanticTokenEntry(int line, int start, int length, int type, int modifiers) {
}

