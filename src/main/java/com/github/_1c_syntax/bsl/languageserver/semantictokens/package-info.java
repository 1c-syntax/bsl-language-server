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
/**
 * Пакет для работы с семантическими токенами Language Server Protocol.
 * <p>
 * Содержит классы для конфигурации и управления семантическими токенами,
 * которые используются для подсветки синтаксиса на основе семантического анализа кода.
 * Семантические токены позволяют клиентам LSP (редакторам кода) более точно и богато
 * раскрашивать код, учитывая его семантическое значение, а не только синтаксическую структуру.
 * <p>
 * Пакет содержит:
 * <ul>
 *   <li>{@link com.github._1c_syntax.bsl.languageserver.semantictokens.SemanticTokensSupplier} - базовый интерфейс для сапплаеров токенов</li>
 *   <li>{@link com.github._1c_syntax.bsl.languageserver.semantictokens.SemanticTokenEntry} - запись семантического токена</li>
 *   <li>{@link com.github._1c_syntax.bsl.languageserver.semantictokens.SemanticTokensHelper} - вспомогательные методы</li>
 *   <li>Сапплаеры для различных типов токенов (символы, комментарии, запросы и т.д.)</li>
 * </ul>
 */
@NullMarked
package com.github._1c_syntax.bsl.languageserver.semantictokens;

import org.jspecify.annotations.NullMarked;
