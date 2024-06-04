/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

import java.util.List;
import java.util.Optional;

/**
 * Мета-информация о логически конечной единице, созданной и описанной в исходном коде, например,
 * объявленный в коде метод, созданная область и т.д.
 *
 * @see Symbol
 */
public interface SourceDefinedSymbol extends Symbol {
  /**
   * @return Документ, в котором объявлен данный символ.
   */
  DocumentContext getOwner();

  /**
   * @return Диапазон, который захватывает символ.
   */
  Range getRange();

  /**
   * @return Место интереса символа.
   * Например, диапазон, где указано имя символа (в противовес полной строки декларации символа).
   */
  Range getSelectionRange();

  /**
   * @return Символ, внутри которого располагается данный символ.
   */
  Optional<SourceDefinedSymbol> getParent();

  /**
   * @param symbol Символ, внутри которого располагается данный символ.
   */
  void setParent(Optional<SourceDefinedSymbol> symbol);

  /**
   * @return Список "детей" символа - символов, которые располагаются внутри данного символа.
   */
  List<SourceDefinedSymbol> getChildren();

  /**
   * Получить наиболее близкий к корню символ указанного типа.
   * <p>
   * Например, если переменная объявлена внутри области, которая в свою очередь объявлена внутри области,
   * вызов данного метода с {@link SymbolKind#Namespace} вернет внешнюю область.
   *
   * @param symbolKind Тип искомого символа
   * @return Найденный символ.
   */
  default Optional<SourceDefinedSymbol> getRootParent(SymbolKind symbolKind) {
    SourceDefinedSymbol rootParent = null;
    Optional<SourceDefinedSymbol> currentParent = getParent();
    while (currentParent.isPresent()) {
      var symbol = currentParent.get();
      if (symbol.getSymbolKind() == symbolKind) {
        rootParent = symbol;
      }
      currentParent = symbol.getParent();
    }

    return Optional.ofNullable(rootParent);
  }
}
