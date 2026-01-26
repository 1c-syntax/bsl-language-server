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
/**
 * Пакет содержит реализации поставщиков подсветки документа (Document Highlight).
 * <p>
 * Document Highlight - функция LSP, которая подсвечивает связанные элементы кода
 * при наведении курсора на ключевое слово или символ. Например, при клике на "Если"
 * подсвечиваются все связанные ключевые слова: Если, ИначеЕсли, Иначе, КонецЕсли.
 * <p>
 * Все поставщики реализуют интерфейс {@link com.github._1c_syntax.bsl.languageserver.documenthighlight.DocumentHighlightSupplier}
 * и автоматически регистрируются в Spring контексте.
 *
 * @see com.github._1c_syntax.bsl.languageserver.providers.DocumentHighlightProvider
 */
@NullMarked
package com.github._1c_syntax.bsl.languageserver.documenthighlight;

import org.jspecify.annotations.NullMarked;