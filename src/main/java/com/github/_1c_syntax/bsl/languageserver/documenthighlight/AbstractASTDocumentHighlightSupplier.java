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

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.DocumentHighlight;

import java.util.List;

/**
 * Базовый класс для поставщиков подсветки на основе AST.
 * <p>
 * Предоставляет общую функциональность для подсветки токенов, полученных через геттеры ANTLR-контекста.
 */
public abstract class AbstractASTDocumentHighlightSupplier implements DocumentHighlightSupplier {

  /**
   * Добавляет подсветку для токена, полученного из TerminalNode.
   * <p>
   * Используется для прямого доступа к токенам через геттеры ANTLR-контекста.
   *
   * @param highlights Список подсветок, в который будет добавлена подсветка токена
   * @param terminalNode Терминальный узел с токеном (может быть null)
   */
  protected void addTokenHighlight(List<DocumentHighlight> highlights, TerminalNode terminalNode) {
    if (terminalNode != null) {
      var token = terminalNode.getSymbol();
      var range = Ranges.create(token);
      highlights.add(new DocumentHighlight(range));
    }
  }
}
