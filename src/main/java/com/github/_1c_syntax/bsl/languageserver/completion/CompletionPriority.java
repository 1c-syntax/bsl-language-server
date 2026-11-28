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
package com.github._1c_syntax.bsl.languageserver.completion;


/**
 * Приоритет предложения с учётом всех возможных контекстов и получателей.
 */
final class CompletionPriority {
  static final int AVAILABLE = 0;
  static final int PARTIAL = 1;
  static final int UNAVAILABLE = 2;

  private CompletionPriority() {
  }

  /**
   * Свёртка рангов альтернатив по одному: доступна ли хотя бы одна и недоступна ли хотя бы одна.
   * Без рангов результат — {@link #AVAILABLE}: отсутствие сведений не скрывает вариант.
   */
  static final class Fold {

    private boolean available;
    private boolean unavailable;

    /**
     * Учесть ранг очередной альтернативы.
     *
     * @param rank {@link #AVAILABLE}, {@link #PARTIAL} или {@link #UNAVAILABLE}.
     */
    void add(int rank) {
      available |= rank != UNAVAILABLE;
      unavailable |= rank != AVAILABLE;
    }

    /**
     * Итоговый ранг.
     *
     * @return {@link #AVAILABLE}, если все доступны; {@link #UNAVAILABLE}, если все недоступны;
     *     иначе {@link #PARTIAL}.
     */
    int result() {
      return combine(available, unavailable);
    }
  }

  private static int combine(boolean available, boolean unavailable) {
    if (!unavailable) {
      return AVAILABLE;
    }
    return available ? PARTIAL : UNAVAILABLE;
  }
}
