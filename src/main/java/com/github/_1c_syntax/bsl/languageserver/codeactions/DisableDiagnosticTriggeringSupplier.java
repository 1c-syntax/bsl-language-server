/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


@Component
public class DisableDiagnosticTriggeringSupplier implements CodeActionSupplier {

  private static final String ALL_DIAGNOSTIC_NAME = "";
  private final Language language;
  private List<CodeAction> result;
  private CodeActionParams params;
  private DocumentContext documentContext;
  private Optional<Token> lastTokenSelectedInLine;
  private boolean isOneLineRange;

  public DisableDiagnosticTriggeringSupplier(LanguageServerConfiguration languageServerConfiguration) {
    this.language = languageServerConfiguration.getLanguage();
  }

  /**
   * При необходимости создает {@code CodeAction} для создания служебного комментария отключающего срабатывание
   * диагностики.
   * Может быть в трех вариантах:
   * 1. Отключаются срабатывания в конкретной строке
   * 2. Отключаются срабатывания в области между парой комментариев
   * 3. Отключаются срабатывания во всем файле
   *
   * @param params          параметры вызова генерации {@code codeAction}
   * @param documentContext представление программного модуля
   * @return {@code List<CodeAction>} если модуль не содержит всех стандартных областей,
   * пустой {@code List} если генерация областей не требуется
   */
  @Override
  public List<CodeAction> getCodeActions(CodeActionParams params, DocumentContext documentContext) {

    initParams(params, documentContext);

    if (!params.getContext().getDiagnostics().isEmpty()) {
      actionDisableDiagnosticInLine();
      actionDisableDiagnosticInRegion();
      actionDisableDiagnosticInFile();
    }

    actionDisableAllDiagnosticInLine();
    actionDisableAllDiagnosticInRegion();
    actionDisableAllDiagnosticInFile();
    return result;

  }

  private void initParams(CodeActionParams params, DocumentContext documentContext) {
    this.result = new ArrayList<>();
    this.params = params;
    this.documentContext = documentContext;

    lastTokenSelectedInLine = Optional.empty();

    if (params.getRange().getStart() == null || params.getRange().getEnd() == null) {
      return;
    }

    isOneLineRange = params.getRange().getStart().getLine() == params.getRange().getEnd().getLine();
    var selectedLineNumber = params.getRange().getEnd().getLine() + 1;

    lastTokenSelectedInLine = documentContext
      .getTokens()
      .stream()
      .filter(token -> token.getLine() == selectedLineNumber)
      .max(Comparator.comparingInt(Token::getCharPositionInLine));
  }

  private void actionDisableDiagnosticInLine() {
    if (lastTokenSelectedInLine.isEmpty() || !isOneLineRange) {
      return;
    }

    result.addAll(
      params.getContext()
        .getDiagnostics()
        .stream()
        .map(Diagnostic::getCode)
        .map(Either::getLeft)
        .distinct()
        .map(name -> createCodeAction(getMessage("line", name), createInLineTextEdits(":" + name)))
        .collect(Collectors.toList())
    );
  }

  private void actionDisableDiagnosticInRegion() {
    if (lastTokenSelectedInLine.isEmpty() || isOneLineRange) {
      return;
    }

    result.addAll(
      params.getContext()
        .getDiagnostics()
        .stream()
        .map(Diagnostic::getCode)
        .map(Either::getLeft)
        .distinct()
        .map(name -> createCodeAction(getMessage("range", name), createInRegionTextEdits(":" + name)))
        .collect(Collectors.toList())
    );
  }

  private void actionDisableDiagnosticInFile() {
    result.addAll(
      params.getContext()
        .getDiagnostics()
        .stream()
        .map(Diagnostic::getCode)
        .map(Either::getLeft)
        .distinct()
        .map(name -> createCodeAction(getMessage("file", name), createInFileTextEdits(":" + name)))
        .collect(Collectors.toList())
    );
  }

  private void actionDisableAllDiagnosticInLine() {
    if (lastTokenSelectedInLine.isEmpty() || !isOneLineRange) {
      return;
    }

    result.add(createCodeAction(getMessage("lineAll"), createInLineTextEdits(ALL_DIAGNOSTIC_NAME)));
  }

  private void actionDisableAllDiagnosticInRegion() {
    if (lastTokenSelectedInLine.isEmpty() || isOneLineRange) {
      return;
    }

    result.add(createCodeAction(getMessage("rangeAll"), createInRegionTextEdits(ALL_DIAGNOSTIC_NAME)));
  }

  private void actionDisableAllDiagnosticInFile() {
    result.add(createCodeAction(getMessage("fileAll"), createInFileTextEdits(ALL_DIAGNOSTIC_NAME)));
  }

  private List<TextEdit> createInLineTextEdits(String diagnosticName) {
    Token last = lastTokenSelectedInLine.get();
    Range range = Ranges.create(
      params.getRange().getStart().getLine(),
      last.getCharPositionInLine() + last.getText().length(),
      params.getRange().getStart().getLine(),
      last.getCharPositionInLine() + last.getText().length()
    );

    TextEdit textEdit = new TextEdit(range, String.format(" // BSLLS%s-off", diagnosticName));
    return Collections.singletonList(textEdit);
  }

  private List<TextEdit> createInRegionTextEdits(String diagnosticName) {
    List<TextEdit> edits = new ArrayList<>();

    Range disableRange = Ranges.create(
      params.getRange().getStart().getLine(),
      0,
      params.getRange().getStart().getLine(),
      0
    );
    TextEdit disableTextEdit = new TextEdit(disableRange, String.format("// BSLLS%s-off%n", diagnosticName));
    edits.add(disableTextEdit);

    Token last = lastTokenSelectedInLine.get();
    Range enableRange = Ranges.create(
      params.getRange().getEnd().getLine(),
      last.getCharPositionInLine() + last.getText().length(),
      params.getRange().getEnd().getLine(),
      last.getCharPositionInLine() + last.getText().length()
    );
    TextEdit enableTextEdit = new TextEdit(enableRange, String.format("%n// BSLLS%s-on%n", diagnosticName));
    edits.add(enableTextEdit);
    return edits;
  }

  private List<TextEdit> createInFileTextEdits(String diagnosticName) {
    TextEdit textEdit = new TextEdit(
      Ranges.create(0, 0, 0, 0),
      String.format("// BSLLS%s-off%n", diagnosticName)
    );
    return Collections.singletonList(textEdit);
  }

  @NotNull
  private CodeAction createCodeAction(String title, List<TextEdit> edits) {
    Map<String, List<TextEdit>> changes = Map.of(documentContext.getUri().toString(), edits);
    WorkspaceEdit edit = new WorkspaceEdit();
    edit.setChanges(changes);

    CodeAction codeAction = new CodeAction(title);
    codeAction.setDiagnostics(new ArrayList<>());
    codeAction.setKind(CodeActionKind.Refactor);
    codeAction.setEdit(edit);
    return codeAction;
  }

  private String getMessage(String key) {
    return Resources.getResourceString(language, this.getClass(), key);
  }

  private String getMessage(String key, Object... args) {
    return Resources.getResourceString(language, this.getClass(), key, args);
  }

}
