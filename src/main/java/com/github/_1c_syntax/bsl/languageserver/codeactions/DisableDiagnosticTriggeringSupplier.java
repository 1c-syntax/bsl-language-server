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

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCode;
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
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider.SOURCE;


/**
 * Поставщик code action для отключения срабатывания диагностики.
 * <p>
 * Добавляет комментарий для игнорирования конкретной диагностики
 * в указанной строке кода.
 */
@Component
public class DisableDiagnosticTriggeringSupplier implements CodeActionSupplier {

  private static final String ALL_DIAGNOSTIC_NAME = "";
  private final LanguageServerConfiguration languageServerConfiguration;

  public DisableDiagnosticTriggeringSupplier(LanguageServerConfiguration languageServerConfiguration) {
    this.languageServerConfiguration = languageServerConfiguration;
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
    List<CodeAction> result = new ArrayList<>();

    if (!params.getContext().getDiagnostics().isEmpty()) {
      if (params.getRange().getStart() != null && params.getRange().getEnd() != null) {
        var selectedLineNumber = params.getRange().getEnd().getLine() + 1;

        documentContext
          .getTokens()
          .stream()
          .filter(token -> token.getLine() == selectedLineNumber)
          .max(Comparator.comparingInt(Token::getCharPositionInLine))
          .ifPresent(token -> {
            if (params.getRange().getStart().getLine() == params.getRange().getEnd().getLine()) {
              result.addAll(getDisableActionForLine(params, documentContext, token));
            } else {
              result.addAll(getDisableActionForRange(params, documentContext, token));
            }
          }
        );
      }

      result.addAll(
        actionDisableDiagnostic(
          name -> createCodeAction(
            getMessage("file", name),
            createInFileTextEdits(":" + name),
            documentContext
          ),
          params
        )
      );
    }

    result.add(
      createCodeAction(
        getMessage("fileAll"),
        createInFileTextEdits(ALL_DIAGNOSTIC_NAME),
        documentContext
      )
    );
    return new ArrayList<>(result);
  }

  private List<CodeAction> getDisableActionForLine(
    CodeActionParams params,
    DocumentContext documentContext,
    Token lastToken
  ) {
    var result = actionDisableDiagnostic(
      name -> createCodeAction(
        getMessage("line", name),
        createInLineTextEdits(":" + name, lastToken, params),
        documentContext
      ),
      params
    );
    result.add(
      createCodeAction(
        getMessage("lineAll"),
        createInLineTextEdits(ALL_DIAGNOSTIC_NAME, lastToken, params),
        documentContext
      )
    );
    return result;
  }

  private List<CodeAction> getDisableActionForRange(
    CodeActionParams params,
    DocumentContext documentContext,
    Token lastToken
  ) {
    var result = actionDisableDiagnostic(
      name -> createCodeAction(
        getMessage("range", name),
        createInRegionTextEdits(":" + name, lastToken, params),
        documentContext
      ),
      params
    );
    result.add(
      createCodeAction(
        getMessage("rangeAll"),
        createInRegionTextEdits(ALL_DIAGNOSTIC_NAME, lastToken, params),
        documentContext
      )
    );
    return result;
  }

  private List<CodeAction> actionDisableDiagnostic(Function<String, CodeAction> func, CodeActionParams params) {
    return params.getContext()
      .getDiagnostics()
      .stream()
      .filter(diagnostic -> SOURCE.equals(diagnostic.getSource()))
      .map(Diagnostic::getCode)
      .map(DiagnosticCode::getStringValue)
      .distinct()
      .map(func)
      .collect(Collectors.toList());
  }

  private List<TextEdit> createInRegionTextEdits(String diagnosticName, Token last, CodeActionParams params) {
    List<TextEdit> edits = new ArrayList<>();

    Range disableRange = Ranges.create(
      params.getRange().getStart().getLine(),
      0,
      params.getRange().getStart().getLine(),
      0
    );
    TextEdit disableTextEdit = new TextEdit(disableRange, String.format("// BSLLS%s-off%n", diagnosticName));
    edits.add(disableTextEdit);

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

  private CodeAction createCodeAction(String title, List<TextEdit> edits, DocumentContext documentContext) {
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
    return Resources.getResourceString(languageServerConfiguration.getLanguage(), this.getClass(), key);
  }

  private String getMessage(String key, Object... args) {
    return Resources.getResourceString(languageServerConfiguration.getLanguage(), this.getClass(), key, args);
  }

  private static List<TextEdit> createInLineTextEdits(String diagnosticName, Token last, CodeActionParams params) {
    Range range = Ranges.create(
      params.getRange().getStart().getLine(),
      last.getCharPositionInLine() + last.getText().length(),
      params.getRange().getStart().getLine(),
      last.getCharPositionInLine() + last.getText().length()
    );

    TextEdit textEdit = new TextEdit(range, String.format(" // BSLLS%s-off", diagnosticName));
    return Collections.singletonList(textEdit);
  }

}
