/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.BRAINOVERLOAD,
    DiagnosticTag.SUSPICIOUS
  }
)
public class LatinAndCyrillicSymbolInWordDiagnostic extends AbstractDiagnostic {
  /**
   * Минимальная длина имени для анализа
   */
  private static final int MINIMAL_WORD_LEN = 2;

  /**
   * Минимальная длина имени для анализа имен, в которых сочетание кириллицы и латиницы возможно
   */
  private static final int MINIMAL_WORD_LEN_TWO_LANG = 4;

  /**
   * Список слов-исключений через `,`
   */
  private static final String DEFAULT_EXCLUDE_WORDS = "ЧтениеXML, ЧтениеJSON, ЗаписьXML, ЗаписьJSON, ComОбъект, " +
    "ФабрикаXDTO, ОбъектXDTO, СоединениеFTP, HTTPСоединение, HTTPЗапрос, HTTPСервисОтвет, SMSСообщение, WSПрокси";

  /**
   * Параметр для возможности использования начала или конца слова на другом языке
   */
  private static final boolean DEFAULT_ALLOW_TRAILING_PARTS_IN_ANOTHER_LANG = true;
  /**
   * Паттерн для поиска кириллических символов в имени
   */
  private static final Pattern RU_LANG_PATTERN = CaseInsensitivePattern.compile("[а-яё]");

  /**
   * Паттерн для поиска латинских символов в имени
   */
  private static final Pattern EN_LANG_PATTERN = CaseInsensitivePattern.compile("[a-z]");

  /**
   * Паттерн для исключения слов, которые начинаются и заканчиваются на разных языках
   */
  private static final Pattern RU_EN_LANG_PATTERN =
    Pattern.compile("(?:[A-Z][A-Za-z]+[A-Za-z1-9_]*[А-ЯЁ][А-Яа-яЁё]+[А-Яа-я1-9Ёё_]*)|" +
      "(?:[А-ЯЁ][А-Яа-яЁё]+[А-Яа-я1-9Ёё_]*[A-Z][A-Za-z]+[A-Za-z1-9_]*)");

  @DiagnosticParameter(
    type = String.class,
    defaultValue = "" + DEFAULT_EXCLUDE_WORDS
  )
  private Pattern excludeWords = createExcludeWordPattern(DEFAULT_EXCLUDE_WORDS);

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + DEFAULT_ALLOW_TRAILING_PARTS_IN_ANOTHER_LANG
  )
  private boolean allowTrailingPartsInAnotherLanguage = DEFAULT_ALLOW_TRAILING_PARTS_IN_ANOTHER_LANG;

  private static Pattern createExcludeWordPattern(String words) {
    var stringJoiner = new StringJoiner("|");
    for (String elem : words.split(",")) {
      stringJoiner.add(Pattern.quote(elem.trim()));
    }

    return CaseInsensitivePattern.compile("(?:^" + stringJoiner + ")");
  }

  @Override
  public void configure(Map<String, Object> configuration) {
    DiagnosticHelper.configureDiagnostic(this, configuration, "allowTrailingPartsInAnotherLanguage");
    this.excludeWords = createExcludeWordPattern(
      (String) configuration.getOrDefault("excludeWords", DEFAULT_EXCLUDE_WORDS));
  }

  @Override
  protected void check() {
    check(BSLParser.RULE_subName);
    check(BSLParser.RULE_var_name);
    check(BSLParser.RULE_annotationName);
    check(BSLParser.RULE_annotationParamName);
    check(BSLParser.RULE_regionName);

    checkLabel();
    checkParameters();
    checkLValue();
  }

  private void check(int ruleID) {
    checkTree(Trees.findAllRuleNodes(documentContext.getAst(), ruleID).stream());
  }

  private void checkLValue() {
    checkTree(Trees.findAllRuleNodes(documentContext.getAst(), BSLParser.RULE_lValue).stream()
      .filter(ctx -> ((BSLParser.LValueContext) ctx).IDENTIFIER() != null)
      .map(ctx -> ((BSLParser.LValueContext) ctx).IDENTIFIER()));
  }

  private void checkParameters() {
    checkTree(Trees.findAllRuleNodes(documentContext.getAst(), BSLParser.RULE_param).stream()
      .filter(ctx -> ((BSLParser.ParamContext) ctx).IDENTIFIER() != null)
      .map(ctx -> ((BSLParser.ParamContext) ctx).IDENTIFIER()));
  }

  private void checkLabel() {
    checkTree(Trees.findAllRuleNodes(documentContext.getAst(), BSLParser.RULE_labelName).stream()
      .filter(ctx -> ctx.getParent() instanceof BSLParser.GotoStatementContext));
  }

  private void checkTree(Stream<ParseTree> tree) {
    var elements = tree.filter(ctx -> ctx.getText() != null && ctx.getText().length() >= MINIMAL_WORD_LEN)
      .filter(ctx -> !excludeWords.matcher(ctx.getText()).matches())
      .filter(ctx -> RU_LANG_PATTERN.matcher(ctx.getText()).find() && EN_LANG_PATTERN.matcher(ctx.getText()).find());

    if (allowTrailingPartsInAnotherLanguage) {
      elements.filter(ctx ->
        ctx.getText().length() < MINIMAL_WORD_LEN_TWO_LANG
          || !RU_EN_LANG_PATTERN.matcher(ctx.getText()).matches()
      ).forEach(diagnosticStorage::addDiagnostic);
    } else {
      elements.forEach(diagnosticStorage::addDiagnostic);
    }
  }
}
