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
package com.github._1c_syntax.bsl.languageserver.lsif.writer;

import java.io.Closeable;
import java.io.IOException;

/**
 * Интерфейс для записи LSIF-элементов в различных форматах.
 * <p>
 * Реализации этого интерфейса определяют, как LSIF-элементы
 * (вершины и рёбра) сериализуются и записываются в файл.
 *
 * @see NdJsonLsifWriter
 * @see JsonLsifWriter
 */
public interface LsifWriter extends Closeable {

  /**
   * Записывает LSIF-элемент.
   *
   * @param element LSIF-элемент (вершина или ребро)
   * @throws IOException если произошла ошибка записи
   */
  void write(Object element) throws IOException;

  /**
   * Завершает запись и освобождает ресурсы.
   *
   * @throws IOException если произошла ошибка закрытия
   */
  @Override
  void close() throws IOException;
}
