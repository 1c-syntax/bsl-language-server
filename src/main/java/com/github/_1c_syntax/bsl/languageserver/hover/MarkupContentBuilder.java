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
package com.github._1c_syntax.bsl.languageserver.hover;

import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import org.eclipse.lsp4j.MarkupContent;

/**
 * Интерфейс построителя контента для всплывающего окна по ссылке под курсором.
 */
public interface MarkupContentBuilder {
  /**
   * Построить контент всплывающего окна для ссылки.
   *
   * @param reference ссылка под курсором.
   * @return контент всплывающего окна.
   */
  MarkupContent getContent(Reference reference);

  /**
   * Конкретный класс символа, который умеет обрабатывать данный построитель.
   * Используется HoverProvider'ом для выбора подходящего билдера — выбор по
   * классу (а не по {@code SymbolKind}) позволяет иметь несколько построителей
   * для символов одного и того же вида (например,
   * {@link com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol}
   * и synthetic-метод).
   *
   * @return класс символа.
   */
  Class<? extends Symbol> getSymbolClass();
}
