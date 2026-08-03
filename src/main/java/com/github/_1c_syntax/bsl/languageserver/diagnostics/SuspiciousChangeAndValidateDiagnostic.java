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

import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.AnnotationKind;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Position;

import java.util.List;

/**
 * Метод, помеченный аннотацией {@code &ИзменениеИКонтроль}, содержит
 * одновременно директивы {@code #Удаление} / {@code #КонецУдаления}
 * и {@code #Вставка} / {@code #КонецВставки}.
 * <p>
 * Такая комбинация означает полную замену тела метода, что скрывает
 * реальные изменения от ревьюера. Аннотация {@code &ИзменениеИКонтроль}
 * предназначена для точечных правок — удаления и вставки отдельных
 * блоков кода с сохранением читаемого diff'а.
 * <p>
 * Для полной замены метода следует использовать аннотацию {@code &Вместо}.
 *
 * @see <a href="https://its.1c.ru/db/v8std/content/455/hdoc">Стандарт 455</a>
 */
@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  scope = DiagnosticScope.BSL,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.BADPRACTICE,
    DiagnosticTag.SUSPICIOUS
  }
)
public class SuspiciousChangeAndValidateDiagnostic extends AbstractDiagnostic {

  /** Максимальное расстояние в строках от границы метода до директивы для срабатывания. */
  private static final int PROXIMITY_LINES = 3;

  @Override
  public void check() {
    var tokens = documentContext.getTokens();

    documentContext.getSymbolTree().getMethods()
      .stream()
      .filter(method -> method.getAnnotations().stream()
        .map(Annotation::getKind)
        .anyMatch(kind -> kind == AnnotationKind.CHANGEANDVALIDATE))
      .filter(method -> isFullReplacement(method, tokens))
      .forEach(method ->
        diagnosticStorage.addDiagnostic(method.getSubNameRange(),
          info.getMessage(method.getName())));
  }

  /**
   * Полная замена: метод с {@code &ИзменениеИКонтроль}, у которого
   * {@code #Удаление} в начале тела и {@code #КонецВставки} в конце —
   * оригинального кода снаружи блоков удаления/вставки не осталось.
   */
  private static boolean isFullReplacement(
    com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol method,
    List<Token> tokens
  ) {
    var range = method.getRange();
    int methodStart = range.getStart().getLine();
    int methodEnd = range.getEnd().getLine();

    var methodTokens = tokensInRange(tokens, range);

    boolean hasDelete = containsAny(methodTokens, BSLLexer.PREPROC_DELETE, BSLLexer.PREPROC_ENDDELETE);
    boolean hasInsert = containsAny(methodTokens, BSLLexer.PREPROC_INSERT, BSLLexer.PREPROC_ENDINSERT);
    if (!hasDelete || !hasInsert) {
      return false;
    }

    int firstDeleteLine = firstTokenLine(methodTokens, BSLLexer.PREPROC_DELETE);
    int lastEndInsertLine = lastTokenLine(methodTokens, BSLLexer.PREPROC_ENDINSERT);

    return firstDeleteLine >= 0
      && lastEndInsertLine >= 0
      && (firstDeleteLine - methodStart) <= PROXIMITY_LINES
      && (methodEnd - lastEndInsertLine) <= PROXIMITY_LINES;
  }

  private static List<Token> tokensInRange(List<Token> tokens, org.eclipse.lsp4j.Range range) {
    return tokens.stream()
      .filter(token -> {
        var pos = new Position(token.getLine() - 1, 0);
        return Ranges.containsPosition(range, pos);
      })
      .toList();
  }

  private static boolean containsAny(List<Token> tokens, int... tokenTypes) {
    return tokens.stream()
      .mapToInt(Token::getType)
      .anyMatch(type -> {
        for (int tt : tokenTypes) {
          if (type == tt) {
            return true;
          }
        }
        return false;
      });
  }

  private static int firstTokenLine(List<Token> tokens, int tokenType) {
    return tokens.stream()
      .filter(t -> t.getType() == tokenType)
      .mapToInt(t -> t.getLine() - 1)
      .min()
      .orElse(-1);
  }

  private static int lastTokenLine(List<Token> tokens, int tokenType) {
    return tokens.stream()
      .filter(t -> t.getType() == tokenType)
      .mapToInt(t -> t.getLine() - 1)
      .max()
      .orElse(-1);
  }

}
