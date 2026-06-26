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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Интерфейс для диагностик, предоставляющих быстрые исправления.
 * <p>
 * Диагностика, реализующая этот интерфейс, может предложить автоматические исправления
 * для найденных проблем в коде.
 */
public interface QuickFixProvider {

  /**
   * Получить список быстрых исправлений для диагностик.
   *
   * @param diagnostics Список диагностик для исправления
   * @param params Параметры запроса code action
   * @param documentContext Контекст документа
   * @return Список code actions для автоматического исправления проблем
   */
  List<CodeAction> getQuickFixes(
    List<Diagnostic> diagnostics,
    CodeActionParams params,
    DocumentContext documentContext
  );

  /**
   * Собрать Code Actions (быстрые исправления) для списка текстовых изменений.
   *
   * @param textEdits Список текстовых изменений
   * @param title Название действия
   * @param uri URI документа
   * @param diagnostics Список диагностик, которые исправляет это действие
   * @return Список Code Actions
   */
  static List<CodeAction> createCodeActions(
    List<TextEdit> textEdits,
    String title,
    URI uri,
    List<Diagnostic> diagnostics
  ) {

    if (diagnostics.isEmpty()) {
      return Collections.emptyList();
    }

    WorkspaceEdit edit = new WorkspaceEdit();

    Map<String, List<TextEdit>> changes = new HashMap<>();
    changes.put(uri.toString(), textEdits);
    edit.setChanges(changes);

    if (diagnostics.size() > 1) {
      title = "Fix all: " + title;
    }

    CodeAction codeAction = new CodeAction(title);
    codeAction.setDiagnostics(diagnostics);
    codeAction.setEdit(edit);
    codeAction.setKind(CodeActionKind.QuickFix);
    if (diagnostics.size() == 1) {
      codeAction.setIsPreferred(Boolean.TRUE);
    }

    return Collections.singletonList(codeAction);

  }

}
