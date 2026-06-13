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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.AnnotatedTextEdit;
import org.eclipse.lsp4j.ChangeAnnotation;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.SnippetTextEdit;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Сборщик {@link WorkspaceEdit} для переименования символа.
 * <p>
 * Инкапсулирует выбор формата результата по заявленным клиентом возможностям
 * {@code workspace.workspaceEdit}: построение {@code documentChanges}
 * ({@link TextDocumentEdit} с версионированным идентификатором документа), оборачивание
 * правок в {@link AnnotatedTextEdit} со связыванием с {@link ChangeAnnotation} либо понижение
 * результата до legacy {@code changes}-map для обратной совместимости.
 */
@Component
@RequiredArgsConstructor
public class RenameWorkspaceEditBuilder {

  private final Resources resources;

  /**
   * Построить {@link WorkspaceEdit} с правками переименования символа.
   * <p>
   * Если клиент заявил {@code workspace.workspaceEdit.documentChanges}, правки группируются по
   * документам в {@code documentChanges} ({@link TextDocumentEdit} с версионированным
   * идентификатором документа); при дополнительной поддержке
   * {@code workspace.workspaceEdit.changeAnnotationSupport} правки оборачиваются в
   * {@link AnnotatedTextEdit} и связываются с {@link ChangeAnnotation}, описывающей переименование.
   * Иначе результат понижается до legacy {@code changes}-map для обратной совместимости.
   *
   * @param changes                 Правки, сгруппированные по uri документа.
   * @param oldName                 Прежнее имя символа, для текста аннотации.
   * @param newName                 Новое имя символа, для текста аннотации.
   * @param documentChangesSupport  {@code true}, если клиент поддерживает documentChanges.
   * @param changeAnnotationSupport {@code true}, если клиент поддерживает аннотации правок.
   * @return Изменения документов
   */
  public WorkspaceEdit build(
    Map<String, List<TextEdit>> changes,
    String oldName,
    String newName,
    boolean documentChangesSupport,
    boolean changeAnnotationSupport
  ) {
    if (!documentChangesSupport) {
      return new WorkspaceEdit(changes);
    }

    var workspaceEdit = new WorkspaceEdit();

    var annotationId = "";
    if (changeAnnotationSupport && !changes.isEmpty()) {
      annotationId = UUID.randomUUID().toString();
      var label = resources.getResourceString(getClass(), "renameAnnotation", oldName, newName);
      workspaceEdit.setChangeAnnotations(Map.of(annotationId, new ChangeAnnotation(label)));
    }

    var resolvedAnnotationId = annotationId;
    List<Either<TextDocumentEdit, ResourceOperation>> documentChanges = changes.entrySet().stream()
      .map((Map.Entry<String, List<TextEdit>> entry) -> {
        var textDocument = new VersionedTextDocumentIdentifier(entry.getKey(), null);
        var edits = annotate(entry.getValue(), resolvedAnnotationId);
        return Either.<TextDocumentEdit, ResourceOperation>forLeft(new TextDocumentEdit(textDocument, edits));
      })
      .toList();

    workspaceEdit.setDocumentChanges(documentChanges);
    return workspaceEdit;
  }

  private static List<Either<TextEdit, SnippetTextEdit>> annotate(List<TextEdit> textEdits, String annotationId) {
    return textEdits.stream()
      .map((TextEdit textEdit) -> {
        TextEdit edit = annotationId.isEmpty()
          ? textEdit
          : new AnnotatedTextEdit(textEdit.getRange(), textEdit.getNewText(), annotationId);
        return Either.<TextEdit, SnippetTextEdit>forLeft(edit);
      })
      .toList();
  }

}
