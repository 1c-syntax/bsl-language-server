/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.RelatedInformation;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.Trees;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static java.lang.Math.max;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.BADPRACTICE
  }

)
public class DuplicateStringLiteralDiagnostic extends AbstractVisitorDiagnostic {

  /**
   * Разрешенное количество повторов по умолчанию
   */
  private static final int ALLOWED_NUMBER_COPIES = 2;

  /**
   * Анализировать весь файл целиком
   */
  private static final boolean ANALYZE_FILE = false;

  /**
   * Анализировать учетом регистра символов
   */
  private static final boolean CASE_SENSITIVE = false;

  /**
   * Минимальная длина анализируемого литерала (с кавычками)
   */
  private static final int MIN_TEXT_LENGTH = 5;

  @DiagnosticParameter(
    type = Integer.class,
    defaultValue = "" + ALLOWED_NUMBER_COPIES
  )
  private int allowedNumberCopies = ALLOWED_NUMBER_COPIES;

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + ANALYZE_FILE
  )
  private boolean analyzeFile = ANALYZE_FILE;

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + CASE_SENSITIVE
  )
  private boolean caseSensitive = CASE_SENSITIVE;

  @DiagnosticParameter(
    type = Integer.class,
    defaultValue = "" + MIN_TEXT_LENGTH
  )
  private int minTextLength = MIN_TEXT_LENGTH;

  @Override
  public void configure(Map<String, Object> configuration) {
    super.configure(configuration);
    // ноль использовать нельзя
    if (allowedNumberCopies < 1) {
      allowedNumberCopies = ALLOWED_NUMBER_COPIES;
    }

    // нет смысла анализировать строки длиной менее значения в константе
    minTextLength = max(minTextLength, MIN_TEXT_LENGTH);

  }

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {
    if (analyzeFile) {
      checkStringLiterals(ctx);
      return ctx;
    } else {
      return super.visitFile(ctx);
    }
  }

  @Override
  public ParseTree visitSub(BSLParser.SubContext ctx) {
    checkStringLiterals(ctx);
    return ctx;
  }

  @Override
  public ParseTree visitFileCodeBlock(BSLParser.FileCodeBlockContext ctx) {
    checkStringLiterals(ctx);
    return ctx;
  }

  @Override
  public ParseTree visitFileCodeBlockBeforeSub(BSLParser.FileCodeBlockBeforeSubContext ctx) {
    checkStringLiterals(ctx);
    return ctx;
  }

  /**
   * Анализирует литералы блока
   *
   * @param ctx Узел блока для анализа
   */
  private void checkStringLiterals(ParserRuleContext ctx) {
    Trees.findAllRuleNodes(ctx, BSLParser.RULE_string).stream()
      .map(ParserRuleContext.class::cast)
      .filter(this::checkLiteral)
      .collect(Collectors.groupingBy(this::getLiteralText))
      .forEach((String name, List<ParserRuleContext> literals) -> {
        if (literals.size() > allowedNumberCopies) {
          List<DiagnosticRelatedInformation> relatedInformation = literals.stream()
            .map(literal -> RelatedInformation.create(
              documentContext.getUri(),
              Ranges.create(literal),
              literal.getText()
            )).collect(Collectors.toList());

          var firstLiteral = literals.get(0);
          diagnosticStorage.addDiagnostic(firstLiteral, info.getMessage(firstLiteral.getText()), relatedInformation);
        }
      });
  }

  private String getLiteralText(ParserRuleContext literal) {
    if (caseSensitive) {
      return literal.getText();
    } else {
      return literal.getText().toLowerCase(Locale.ROOT);
    }
  }

  /**
   * Проверяет необходимость анализа литерала, т.к. некоторые пропускаются
   *
   * @param literal Строковый литерал
   * @return Необходимо анализировать
   */
  private boolean checkLiteral(ParserRuleContext literal) {
    return literal.getText().length() >= minTextLength;
  }
}
