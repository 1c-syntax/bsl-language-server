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
package com.github._1c_syntax.bsl.languageserver.context;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Хранилище метрик кода для документа.
 * <p>
 * Содержит различные метрики, вычисленные для файла:
 * количество процедур, функций, строк кода, комментариев, сложность и т.д.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MetricStorage {
  /**
   * Количество процедур.
   */
  private int procedures;
  /**
   * Количество функций.
   */
  private int functions;
  /**
   * Общее количество строк.
   */
  private int lines;
  /**
   * Количество строк кода (без пустых и комментариев).
   */
  private int ncloc;
  /**
   * Количество строк с комментариями.
   */
  private int comments;
  /**
   * Количество операторов.
   */
  private int statements;
  /**
   * Данные о строках кода (массив номеров строк).
   */
  private int[] nclocData;
  /**
   * Данные о покрытии кода (устарело).
   *
   * @deprecated Используется для обратной совместимости
   */
  @Deprecated
  private int[] covlocData;
  /**
   * Когнитивная сложность.
   */
  private int cognitiveComplexity;
  /**
   * Цикломатическая сложность.
   */
  private int cyclomaticComplexity;
}
