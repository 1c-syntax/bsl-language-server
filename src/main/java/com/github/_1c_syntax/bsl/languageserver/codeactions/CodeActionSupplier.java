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
package com.github._1c_syntax.bsl.languageserver.codeactions;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Интерфейс для поставщиков code actions (быстрых исправлений).
 * <p>
 * Реализации предоставляют конкретные типы code actions для различных ситуаций:
 * быстрые исправления диагностик, рефакторинги, генерация кода и т.д.
 */
public interface CodeActionSupplier {
  /**
   * Получить список code actions для указанного диапазона в документе.
   *
   * @param params Параметры запроса code action
   * @param documentContext Контекст документа
   * @return Список доступных code actions
   */
  List<CodeAction> getCodeActions(CodeActionParams params, DocumentContext documentContext);

  /**
   * Запрошен ли вид действия фильтром {@code only} контекста запроса.
   * <p>
   * Виды иерархичны: запрос вида {@code source} включает {@code source.fixAll}. Без фильтра
   * (он отсутствует или пуст) запрошены все виды; действие без вида проходит только тогда.
   *
   * @param params Параметры запроса code action
   * @param kind   Вид действия
   * @return {@code true}, если действие этого вида нужно вернуть клиенту
   */
  static boolean isKindRequested(CodeActionParams params, @Nullable String kind) {
    var only = params.getContext().getOnly();
    return only == null || only.isEmpty() || matchesAny(only, kind);
  }

  /**
   * Запрошен ли вид действия явно: фильтр {@code only} задан и включает этот вид — сам или
   * через родительский.
   *
   * @param params Параметры запроса code action
   * @param kind   Вид действия
   * @return {@code true}, если клиент явно попросил действия этого вида
   */
  static boolean isKindExplicitlyRequested(CodeActionParams params, String kind) {
    var only = params.getContext().getOnly();
    return only != null && matchesAny(only, kind);
  }

  private static boolean matchesAny(List<String> only, @Nullable String kind) {
    if (kind == null) {
      return false;
    }
    return only.stream().anyMatch(requested -> kind.equals(requested) || kind.startsWith(requested + "."));
  }
}
