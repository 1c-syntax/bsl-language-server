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
import com.github._1c_syntax.bsl.parser.BSLLexer;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Position;

import java.util.HashSet;

/**
 * Директива {@code #Вставка} / {@code #КонецВставки} используется внутри метода
 * (процедуры или функции), который не помечен аннотацией {@code &ИзменениеИКонтроль}.
 * Без этой аннотации платформа не сможет скомпилировать модуль — вставка кода расширения
 * разрешена только в методах, явно объявленных как изменяющие.
 *
 * @see <a href="https://its.1c.ru/db/v8std/content/455/hdoc">Стандарт 455</a>
 */
@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.CRITICAL,
  scope = DiagnosticScope.BSL,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.ERROR
  }
)
public class InsertionWithoutChangeAndValidateDiagnostic extends AbstractDiagnostic {

  @Override
  protected void check() {
    var insertionDirectives = documentContext.getTokens().stream()
      .filter(InsertionWithoutChangeAndValidateDiagnostic::isInsertionDirective)
      .toList();

    if (insertionDirectives.isEmpty()) {
      return;
    }

    var symbolTree = documentContext.getSymbolTree();
    var reportedMethods = new HashSet<MethodSymbol>();

    for (var directive : insertionDirectives) {
      var directivePosition = new Position(directive.getLine() - 1, directive.getCharPositionInLine());

      // Вставка на уровне модуля (вне методов) аннотации не требует.
      if (!(symbolTree.getSymbolAtPosition(directivePosition) instanceof MethodSymbol method)) {
        continue;
      }

      // Методу с несколькими директивами достаточно одной диагностики.
      if (hasChangeAndValidate(method) || !reportedMethods.add(method)) {
        continue;
      }

      diagnosticStorage.addDiagnostic(method.getSubNameRange(), info.getMessage(method.getName()));
    }
  }

  private static boolean isInsertionDirective(Token token) {
    return token.getType() == BSLLexer.PREPROC_INSERT
      || token.getType() == BSLLexer.PREPROC_ENDINSERT;
  }

  private static boolean hasChangeAndValidate(MethodSymbol method) {
    return method.getAnnotations().stream()
      .map(Annotation::getKind)
      .anyMatch(kind -> kind == AnnotationKind.CHANGEANDVALIDATE);
  }

}
