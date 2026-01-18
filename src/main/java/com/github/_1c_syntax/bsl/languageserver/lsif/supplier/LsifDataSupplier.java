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
package com.github._1c_syntax.bsl.languageserver.lsif.supplier;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.lsif.LsifEmitter;

/**
 * Базовый интерфейс для поставщиков LSIF-данных.
 * <p>
 * Реализации этого интерфейса отвечают за генерацию специфичных LSIF-элементов
 * (hover, definition, references и т.д.) для документа.
 */
public interface LsifDataSupplier {

  /**
   * Генерирует LSIF-элементы для документа.
   *
   * @param documentContext контекст документа
   * @param documentId      ID вершины документа в LSIF-графе
   * @param emitter         эмиттер для записи LSIF-элементов
   */
  void supply(DocumentContext documentContext, long documentId, LsifEmitter emitter);
}
