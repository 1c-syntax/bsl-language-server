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
package com.github._1c_syntax.bsl.languageserver.context.symbol;

import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.parser.description.VariableDescription;
import org.eclipse.lsp4j.Range;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Информация о символе, представляющем собой переменную.
 */
public interface VariableSymbol extends SourceDefinedSymbol, Exportable, Describable, Comparable<VariableSymbol> {

  /**
   * Естественный порядок переменных: имя → URI владельца → позиция начала имени
   * (строка, символ). Старт имени уникален в пределах документа, поэтому этого
   * достаточно для строгого порядка.
   */
  Comparator<VariableSymbol> NATURAL_ORDER = Comparator
    .comparing(VariableSymbol::getName)
    .thenComparing(variable -> variable.getOwner().getUri())
    .thenComparingInt(VariableSymbol::getVariableNameLine)
    .thenComparingInt(VariableSymbol::getVariableNameStartCharacter);

  /**
   * @return Вид переменной
   */
  VariableKind getKind();

  /**
   * Аннотации, навешанные на объявление переменной (например, {@code &Пластилин}
   * фреймворка ОСень). Для подавляющего большинства переменных список пуст,
   * поэтому он не хранится в объекте символа — см. {@link AnnotatedVariableSymbol}.
   *
   * @return список аннотаций; пустой, если их нет.
   */
  default List<Annotation> getAnnotations() {
    return Collections.emptyList();
  }

  /**
   * @return Диапазон, в котором определено имя переменной.
   */
  Range getVariableNameRange();

  /**
   * Строка, в которой определено имя переменной.
   *
   * @return номер строки имени переменной.
   */
  int getVariableNameLine();

  /**
   * Начальный символ имени переменной в строке объявления.
   *
   * @return номер начального символа имени переменной.
   */
  int getVariableNameStartCharacter();

  /**
   * Конечный символ имени переменной в строке объявления.
   *
   * @return номер конечного символа имени переменной.
   */
  int getVariableNameEndCharacter();

  @Override
  Optional<VariableDescription> getDescription();

  /**
   * @return Область объявления переменной.
   */
  SourceDefinedSymbol getScope();

  static AbstractVariableSymbol.Builder builder() {
    return AbstractVariableSymbol.builder();
  }

  @Override
  default int compareTo(VariableSymbol other) {
    return NATURAL_ORDER.compare(this, other);
  }
}
