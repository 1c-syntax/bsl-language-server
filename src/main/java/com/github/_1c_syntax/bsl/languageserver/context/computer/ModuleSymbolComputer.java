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
package com.github._1c_syntax.bsl.languageserver.context.computer;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.antlr.v4.runtime.Token;

import java.util.EnumSet;
import java.util.Set;

/**
 * Компьютер символа модуля документа.
 */
public class ModuleSymbolComputer implements Computer<ModuleSymbol> {

  private static final Set<ModuleType> MODULE_TYPES_TO_APPEND_NAME = EnumSet.of(
    ModuleType.ObjectModule,
    ModuleType.ManagerModule
  );

  private final DocumentContext documentContext;

  public ModuleSymbolComputer(DocumentContext documentContext) {
    this.documentContext = documentContext;
  }

  @Override
  public ModuleSymbol compute() {
    var firstRange = documentContext.getTokens().stream()
      .filter(token -> token.getType() != Token.EOF)
      .filter(token -> token.getType() != BSLLexer.WHITE_SPACE)
      .map(Ranges::create)
      .filter(range -> (!range.getStart().equals(range.getEnd())))
      .findFirst()
      .orElseGet(Ranges::create); // используем нулевую область

    return ModuleSymbol.builder()
      .name(getName(documentContext))
      .owner(documentContext)
      .range(Ranges.create(documentContext.getAst()))
      .selectionRange(firstRange)
      .build();
  }

  private static String getName(DocumentContext documentContext) {
    return name(documentContext.getMdoRef(), documentContext.getModuleType());
  }

  /**
   * Вычислить имя символа модуля по ссылке на объект-метаданные и типу модуля.
   * <p>
   * Имя является чистой производной от {@code mdoRef} и {@code moduleType}: к {@code mdoRef}
   * для отдельных типов модулей дописывается квалификатор-тип, чтобы различать несколько модулей
   * одного объекта метаданных. Метод детерминирован и не зависит от загрузки самого документа,
   * поэтому одинаково применим как при построении символа, так и при индексации ссылок на модуль.
   *
   * @param mdoRef     Ссылка на объект-метаданные модуля (например, {@code CommonModule.ОбщийМодуль1}).
   * @param moduleType Тип модуля (например, {@link ModuleType#CommonModule}).
   * @return Имя символа модуля, совпадающее с {@link ModuleSymbol#getName()}.
   */
  public static String name(String mdoRef, ModuleType moduleType) {
    var name = mdoRef;
    if (MODULE_TYPES_TO_APPEND_NAME.contains(moduleType)) {
      name += "." + moduleType.name();
    }
    return name;
  }
}
