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
package com.github._1c_syntax.bsl.languageserver.semantictokens;

import lombok.experimental.UtilityClass;
import org.eclipse.lsp4j.SemanticTokenModifiers;

import java.util.ArrayList;

/**
 * Наборы модификаторов семантических токенов из флагов «стандартная библиотека», «статический»,
 * «асинхронный» — в этом порядке.
 * <p>
 * Наборы общие и создаются один раз: вызывающий не должен менять возвращённый массив.
 */
@UtilityClass
class SemanticTokenModifierSets {

  /**
   * Набор модификаторов для сочетания флагов.
   *
   * @param defaultLibrary член стандартной библиотеки платформы
   * @param isStatic       статический (член модуля, а не экземпляра)
   * @param async          асинхронный метод
   * @return модификаторы, взведённые флагами; пустой массив, если ни один не взведён
   */
  String[] of(boolean defaultLibrary, boolean isStatic, boolean async) {
    return SETS[index(defaultLibrary, isStatic, async)];
  }

  private static final String[][] SETS = buildSets();

  private static String[][] buildSets() {
    var sets = new String[8][];
    for (var defaultLibrary : new boolean[]{false, true}) {
      for (var isStatic : new boolean[]{false, true}) {
        for (var async : new boolean[]{false, true}) {
          var modifiers = new ArrayList<String>(3);
          if (defaultLibrary) {
            modifiers.add(SemanticTokenModifiers.DefaultLibrary);
          }
          if (isStatic) {
            modifiers.add(SemanticTokenModifiers.Static);
          }
          if (async) {
            modifiers.add(SemanticTokenModifiers.Async);
          }
          sets[index(defaultLibrary, isStatic, async)] = modifiers.toArray(String[]::new);
        }
      }
    }
    return sets;
  }

  private static int index(boolean defaultLibrary, boolean isStatic, boolean async) {
    return (defaultLibrary ? 4 : 0) | (isStatic ? 2 : 0) | (async ? 1 : 0);
  }
}
