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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;

/**
 * Источник типа переменной, известного до её первого присваивания.
 * <p>
 * Отвечает на вопрос «что о переменной объявлено помимо кода её тела»: объявленный тип
 * параметра, типизирующий комментарий, внедрение фреймворка. Типы всех источников
 * объединяются и становятся тем, что известно на входе в тело — дальше по телу их
 * перекрывают присваивания.
 * <p>
 * Новый вид объявления — это новая реализация-бин, а не правка вывода типов: реализации
 * внедряются списком.
 */
public interface VariableTypeSource {

  /**
   * Типы, объявленные для переменной этим источником.
   *
   * @param variable переменная.
   * @return объявленные типы; пустой набор, если источник о переменной ничего не знает.
   */
  TypeSet typesOf(VariableSymbol variable);
}
