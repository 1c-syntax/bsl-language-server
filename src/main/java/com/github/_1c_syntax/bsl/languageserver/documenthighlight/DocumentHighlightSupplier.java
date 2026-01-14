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
package com.github._1c_syntax.bsl.languageserver.documenthighlight;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightParams;

import java.util.List;
import java.util.Optional;

/**
 * Интерфейс для поставщиков подсветки связанных элементов в документе.
 * <p>
 * Реализации предоставляют подсветку для различных типов блочных конструкций:
 * условные операторы (If/ElseIf/Else/EndIf), циклы, try-except, регионы, скобки и т.д.
 */
public interface DocumentHighlightSupplier {

  /**
   * Информация о терминальном узле на позиции курсора.
   *
   * @param terminalNode найденный терминальный узел
   * @param tokenType    тип токена
   */
  record TerminalNodeInfo(TerminalNode terminalNode, int tokenType) {
  }

  /**
   * Получить список подсветок для элементов, связанных с позицией курсора.
   *
   * @param params Параметры запроса document highlight
   * @param documentContext Контекст документа
   * @param terminalNodeInfo Информация о терминальном узле на позиции курсора
   * @return Список подсветок связанных элементов, или пустой список если нет совпадений
   */
  List<DocumentHighlight> getDocumentHighlight(
    DocumentHighlightParams params,
    DocumentContext documentContext,
    Optional<TerminalNodeInfo> terminalNodeInfo
  );
}
