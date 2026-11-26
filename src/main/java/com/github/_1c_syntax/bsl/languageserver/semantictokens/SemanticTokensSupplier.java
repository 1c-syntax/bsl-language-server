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
package com.github._1c_syntax.bsl.languageserver.semantictokens;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.eclipse.lsp4j.Range;

import java.util.List;

/**
 * Базовый интерфейс для наполнения {@link com.github._1c_syntax.bsl.languageserver.providers.SemanticTokensProvider}
 * данными о семантических токенах.
 */
public interface SemanticTokensSupplier {
  /**
   * Получить семантические токены для документа.
   *
   * @param documentContext Контекст документа
   * @return Список семантических токенов
   */
  List<SemanticTokenEntry> getSemanticTokens(DocumentContext documentContext);

  /**
   * Получить семантические токены только для указанного диапазона документа
   * (запрос {@code textDocument/semanticTokens/range}).
   * <p>
   * Реализация по умолчанию игнорирует диапазон и возвращает токены всего
   * документа — провайдер всё равно отфильтрует их по диапазону. Дорогие
   * сапплаеры (с инференсом типов на каждый узел) переопределяют этот метод,
   * чтобы не выполнять тяжёлую работу за пределами видимой области.
   *
   * @param documentContext Контекст документа
   * @param range           Запрошенный диапазон (всегда задан вызывающим)
   * @return Список семантических токенов в пределах диапазона
   */
  default List<SemanticTokenEntry> getSemanticTokens(DocumentContext documentContext, Range range) {
    return getSemanticTokens(documentContext);
  }
}

