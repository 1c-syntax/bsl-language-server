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



/** Приоритет предложения с учётом всех возможных контекстов и получателей. */
final class CompletionPriority {
  static final int AVAILABLE = 0;
  static final int PARTIAL = 1;
  static final int UNAVAILABLE = 2;

  private CompletionPriority() {
  }

  /** Пустой набор означает отсутствие сведений; смешанная доступность — отдельную промежуточную группу. */
  static int combine(int[] ranks) {
    var available = false;
    var unavailable = false;
    for (var rank : ranks) {
      available |= rank != UNAVAILABLE;
      unavailable |= rank != AVAILABLE;
    }
    if (!unavailable) {
      return AVAILABLE;
    }
    return available ? PARTIAL : UNAVAILABLE;
  }
}
