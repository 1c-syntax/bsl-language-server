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

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
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
import org.eclipse.lsp4j.Range;

import java.util.List;

/**
 * Метод с аннотацией {@code &ИзменениеИКонтроль}, в котором директивы
 * {@code #Удаление} и {@code #Вставка} полностью заменяют тело метода.
 * <p>
 * Полная замена — когда оригинального кода не остаётся ни до, ни между,
 * ни после блоков удаления/вставки. Для такого сценария следует
 * использовать аннотацию {@code &Вместо}.
 * <p>
 * Проверяет наличие обеих пар маркеров
 * ({@code #Удаление}+{@code #КонецУдаления},
 * {@code #Вставка}+{@code #КонецВставки}), их положение у границ метода
 * и отсутствие кода между блоками удаления и вставки.
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
   * Полная замена: обе пары маркеров присутствуют,
   * {@code #Удаление} у начала метода, {@code #КонецВставки} у конца,
   * и между {@code #КонецУдаления} и {@code #Вставка} нет кода.
   */
  private static boolean isFullReplacement(MethodSymbol method, List<Token> tokens) {
    var range = method.getRange();
    int methodStart = range.getStart().getLine();
    int methodEnd = range.getEnd().getLine();

    var methodTokens = tokensInRange(tokens, range);

    // Требуем обе пары маркеров
    if (!hasPair(methodTokens, BSLLexer.PREPROC_DELETE, BSLLexer.PREPROC_ENDDELETE)
      || !hasPair(methodTokens, BSLLexer.PREPROC_INSERT, BSLLexer.PREPROC_ENDINSERT)) {
      return false;
    }

    int firstDelete = firstLineOf(methodTokens, BSLLexer.PREPROC_DELETE);
    int lastEndInsert = lastLineOf(methodTokens, BSLLexer.PREPROC_ENDINSERT);

    // Блоки должны быть у границ метода
    if (firstDelete < 0 || lastEndInsert < 0) {
      return false;
    }
    if ((firstDelete - methodStart) > PROXIMITY_LINES) {
      return false;
    }
    if ((methodEnd - lastEndInsert) > PROXIMITY_LINES) {
      return false;
    }

    // Между концом удаления и началом вставки не должно быть кода
    int endDelete = lastLineOf(methodTokens, BSLLexer.PREPROC_ENDDELETE);
    int startInsert = firstLineOf(methodTokens, BSLLexer.PREPROC_INSERT);
    if (endDelete < 0 || startInsert < 0) {
      return false;
    }

    return !hasCodeBetween(tokens, endDelete, startInsert);
  }

  private static boolean hasPair(List<Token> tokens, int startType, int endType) {
    return containsToken(tokens, startType) && containsToken(tokens, endType);
  }

  private static boolean containsToken(List<Token> tokens, int type) {
    return tokens.stream().anyMatch(t -> t.getType() == type);
  }

  /**
   * Есть ли код (default-channel токены) между строками {@code fromLine}
   * и {@code toLine} включительно. Игнорируем сами маркеры.
   */
  private static boolean hasCodeBetween(List<Token> tokens, int fromLine, int toLine) {
    return tokens.stream()
      .filter(t -> t.getChannel() == Token.DEFAULT_CHANNEL)
      .anyMatch(t -> {
        int line = t.getLine() - 1;
        return line >= fromLine && line <= toLine
          && t.getType() != BSLLexer.PREPROC_ENDDELETE
          && t.getType() != BSLLexer.PREPROC_INSERT;
      });
  }

  private static List<Token> tokensInRange(List<Token> tokens, Range range) {
    return tokens.stream()
      .filter(token -> Ranges.containsPosition(range,
        new Position(token.getLine() - 1, 0)))
      .toList();
  }

  private static int firstLineOf(List<Token> tokens, int type) {
    return tokens.stream()
      .filter(t -> t.getType() == type)
      .mapToInt(t -> t.getLine() - 1)
      .min().orElse(-1);
  }

  private static int lastLineOf(List<Token> tokens, int type) {
    return tokens.stream()
      .filter(t -> t.getType() == type)
      .mapToInt(t -> t.getLine() - 1)
      .max().orElse(-1);
  }

}
