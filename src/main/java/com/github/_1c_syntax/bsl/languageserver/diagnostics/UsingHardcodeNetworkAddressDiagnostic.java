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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Map;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.VULNERABILITY,
  severity = DiagnosticSeverity.CRITICAL,
  minutesToFix = 15,
  tags = {
    DiagnosticTag.STANDARD
  }
)
public class UsingHardcodeNetworkAddressDiagnostic extends AbstractVisitorDiagnostic {

  private static final String REGEX_NETWORK_ADDRESS =
    "(([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:" +
      "|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}" +
      "|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}" +
      "|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})" +
      "|(?<![g-zа-яА-ЯёЁ]):((:[0-9a-fA-F]{1,4}){1,7}|\\s:)|fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}" +
      "|::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]" +
      "|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]" +
      "|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9]))" +
      "|((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])";

  private static final int DOTS_IN_IPV4 = 3;

  private static final String REGEX_URL = "^(ftp|http|https):\\/\\/[^ \"].*";

  private static final String REGEX_EXCLUSION = "Верси|Version|ЗапуститьПриложение|RunApp|Пространств|" +
    "Namespace|Драйвер|Driver";

  private static final String REGEX_ALPHABET = "[A-zА-я]";

  private static final String REGEX_POPULAR_VERSION = "^(1|2|3|8\\.3|11)\\.";

  private static final Pattern patternNetworkAddress = CaseInsensitivePattern.compile(REGEX_NETWORK_ADDRESS);
  private static final Pattern patternURL = CaseInsensitivePattern.compile(REGEX_URL);
  private static final Pattern patternAlphabet = CaseInsensitivePattern.compile(REGEX_ALPHABET);

  @DiagnosticParameter(
    type = String.class,
    defaultValue = REGEX_EXCLUSION
  )
  private Pattern searchWordsExclusion = CaseInsensitivePattern.compile(REGEX_EXCLUSION);

  /**
   * Паттерн для исключения популярных версий из списка IP-адресов. Например, `2.5.4.10`.
   */
  @DiagnosticParameter(
    type = String.class,
    defaultValue = REGEX_POPULAR_VERSION
  )
  private Pattern searchPopularVersionExclusion = CaseInsensitivePattern.compile(REGEX_POPULAR_VERSION);

  @Override
  public void configure(Map<String, Object> configuration) {
    // Слова исключения, при поиске IP адресов
    String searchWordsExclusionProperty =
      (String) configuration.getOrDefault("searchWordsExclusion", REGEX_EXCLUSION);
    searchWordsExclusion = CaseInsensitivePattern.compile(searchWordsExclusionProperty);

    // Паттерн исключения популярных версий
    String searchPopularVersionExclusionProperty =
      (String) configuration.getOrDefault("searchPopularVersionExclusion", REGEX_POPULAR_VERSION);
    searchPopularVersionExclusion = CaseInsensitivePattern.compile(searchPopularVersionExclusionProperty);
  }

  /**
   * Проверяем строковые литералы на пути содержание IP4 / IP6.
   * Пример:
   * <p>
   * СетевойПуть = "127.0.0.1";
   */
  @Override
  public ParseTree visitString(BSLParser.StringContext ctx) {
    var content = ctx.getText().substring(1, ctx.getText().length() - 1);
    if (content.length() > 2) {
      var matcherURL = patternURL.matcher(content);
      if (!matcherURL.find()) {
        processVisitString(ctx, content);
      }
    }
    return ctx;
  }

  private void processVisitString(BSLParser.StringContext ctx, String content) {
    var matcher = patternNetworkAddress.matcher(content);
    if (matcher.find()) {

      String firstValue = matcher.group(0);
      int countDots = (int) firstValue.chars().filter(num -> num == '.').count();
      int countDotsAll = (int) content.chars().filter(num -> num == '.').count();
      matcher = patternAlphabet.matcher(firstValue);
      boolean findAlphabet = matcher.find();

      if (countDots > 0 && (countDotsAll > DOTS_IN_IPV4 || findAlphabet)) { // для ip4
        return;
      }

      if (skipStatement(ctx, BSLParser.RULE_statement)
        || skipStatement(ctx, BSLParser.RULE_param)
        || itVersionReturn(ctx)) {
        return;
      }

      if (searchPopularVersionExclusion.matcher(content).find()) {
        return;
      }

      diagnosticStorage.addDiagnostic(ctx);
    }
  }

  private boolean itVersionReturn(ParserRuleContext ctx) {

    ParserRuleContext returnState = Trees.getAncestorByRuleIndex(ctx, BSLParser.RULE_returnStatement);
    if (returnState != null) {
      return skipStatement(returnState, BSLParser.RULE_function);
    }
    return false;
  }

  private boolean skipStatement(ParserRuleContext ctx, int ruleStatement) {

    ParserRuleContext parent = Trees.getAncestorByRuleIndex(ctx, ruleStatement);
    if (parent != null) {
      var matcher = searchWordsExclusion.matcher(parent.getText());
      return matcher.find();
    }
    return false;
  }

}
