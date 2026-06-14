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
package com.github._1c_syntax.bsl.languageserver.rename;

import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import org.eclipse.lsp4j.Location;
import org.springframework.stereotype.Component;

/**
 * Создаёт ссылку на собственное определение символа.
 * <p>
 * Инкапсулирует конструирование {@link Reference} с типом вхождения
 * {@link OccurrenceType#DEFINITION}, указывающей символ сам на себя, чтобы провайдер
 * переименования не зависел напрямую от {@link Location} и {@link OccurrenceType}.
 * Такая ссылка-определение нужна, чтобы наряду с обычными вхождениями переименовать и сам
 * объявляющий символ (например, имя переменной в месте её объявления).
 */
@Component
public class SymbolDefinitionReferenceFactory {

  /**
   * Построить ссылку на собственное определение символа.
   * <p>
   * Возвращаемая ссылка указывает символ сам на себя и имеет тип вхождения
   * {@link OccurrenceType#DEFINITION}; её диапазон — диапазон выделения символа в документе
   * владельца.
   *
   * @param symbol Символ, для которого строится ссылка-определение.
   * @return Ссылка на собственное определение символа.
   */
  public Reference referenceOf(SourceDefinedSymbol symbol) {
    return Reference.of(
      symbol,
      symbol,
      new Location(symbol.getOwner().getUri().toString(), symbol.getSelectionRange()),
      OccurrenceType.DEFINITION
    );
  }

}
