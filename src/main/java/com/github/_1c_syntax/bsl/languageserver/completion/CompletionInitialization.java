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

/** Подготовка фиксированных классов completion без документов и вычисления типов. */
public final class CompletionInitialization {
  private static final Class<?>[] CLASSES = {
    CompletionContextRanker.class,
    CompletionContextEvaluation.class,
    CompletionTypeContext.class,
    CompletionMethodEvaluation.class,
    CompletionMethodHint.class,
    CompletionMemberKeys.class,
    CompletionData.class
  };

  private CompletionInitialization() { }

  /** Загрузить и инициализировать классы и их вложенные типы; повторный вызов безопасен. */
  public static void initialize() {
    for (var type : CLASSES) {
      for (var member : type.getNestMembers()) {
        try {
          Class.forName(member.getName(), true, member.getClassLoader());
        } catch (ClassNotFoundException exception) {
          throw new IllegalStateException("Completion class is unavailable: " + member.getName(), exception);
        }
      }
    }
  }
}
