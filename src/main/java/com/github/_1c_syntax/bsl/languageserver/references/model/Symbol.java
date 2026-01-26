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
package com.github._1c_syntax.bsl.languageserver.references.model;

import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.utils.GenericInterner;
import lombok.Builder;
import org.eclipse.lsp4j.SymbolKind;
import org.jspecify.annotations.Nullable;

import static java.util.Comparator.comparing;

/**
 * Облегченные данные символа для поиска без кросс-ссылок между файлами.
 *
 * @param mdoRef     Ссылка на объект метаданных в формате ВидОбъектаМетаданных.ИмяОбъекта, в котором расположен символ.
 * @param moduleType Тип модуля объекта метаданных, в котором расположен символ.
 * @param scopeName  Область видимости символа.
 * @param symbolKind Тип символа.
 * @param symbolName Имя символа.
 * @see SourceDefinedSymbol
 */
@Builder
public record Symbol(
  String mdoRef,
  ModuleType moduleType,
  String scopeName,
  SymbolKind symbolKind,
  String symbolName
) implements Comparable<Symbol> {

  private static final GenericInterner<Symbol> INTERNER = new GenericInterner<>();

  public Symbol intern() {
    return INTERNER.intern(this);
  }

  @Override
  public int compareTo(@Nullable Symbol other) {
    if (other == null) {
      return 1;
    }

    return comparing(Symbol::mdoRef)
      .thenComparing(Symbol::moduleType)
      .thenComparing(Symbol::scopeName)
      .thenComparing(Symbol::symbolKind)
      .thenComparing(Symbol::symbolName)
      .compare(this, other);
  }
}
