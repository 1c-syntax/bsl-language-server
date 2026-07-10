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

import lombok.Value;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

/**
 * Synthetic-символ BSL-keyword'а ({@code Если}, {@code Истина}, {@code Цикл}…).
 * <p>
 * Создаётся on-the-fly в {@link com.github._1c_syntax.bsl.languageserver.types.references.KeywordReferenceFinder}
 * при попадании курсора на keyword-токен — keyword'ы не являются
 * source-defined-символами и не лежат в symbol-tree модуля, но участвуют
 * в общем reference/hover-flow на правах обычного {@link Symbol}.
 * <p>
 * Несёт уже локализованное описание (выбранное по текущему
 * {@link com.github._1c_syntax.bsl.languageserver.configuration.Language}
 * и по AST-контексту использования — Функция/Процедура/Перем для
 * body-keyword'ов вида {@code Знач}/{@code Возврат}/{@code Экспорт}).
 */
@Value
public class KeywordSymbol implements Symbol {

  /**
   * Текст keyword'а — ровно как он введён пользователем в исходнике
   * ({@code Если} или {@code If}, регистрозависимо). Используется как
   * заголовок code-fence в hover'е.
   */
  String name;

  /**
   * Локализованное описание keyword'а из синтакс-помощника (уже выбранное
   * по текущей локали и AST-контексту). Пусто, если описание недоступно.
   */
  String description;

  /**
   * Диапазон keyword-токена в исходнике — для подсветки в hover'е.
   */
  Range selectionRange;

  @Override
  public SymbolKind getSymbolKind() {
    return SymbolKind.Operator;
  }

  @Override
  public void accept(SymbolTreeVisitor visitor) {
    // no-op: synthetic-символ, в symbol-tree не входит
  }
}
