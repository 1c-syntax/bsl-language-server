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
}
