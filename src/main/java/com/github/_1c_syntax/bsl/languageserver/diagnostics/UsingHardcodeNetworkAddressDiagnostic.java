/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Map;
import java.util.regex.Matcher;
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
    "^((?<ip6Address>([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:" +
      "|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:)" +
      "{1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}" +
      "(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)" +
      "|fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|(2[0-4]|1{0,1}" +
      "[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|([0-9a-fA-F]{1,4}:){1,4}:" +
      "((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9]))" +
      "|(?<ip4Address>((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])))$";

  private static final String REGEX_URL = "^(ftp|http|https):\\/\\/[^ \"].*";

  private static final String REGEX_EXCLUSION = "Верси|Version|ЗапуститьПриложение|RunApp|Пространств|" +
    "Namespace|Драйвер|Driver";

  private static final Pattern patternNetworkAddress = getLocalPattern(REGEX_NETWORK_ADDRESS);
  private static final Pattern patternURL = getLocalPattern(REGEX_URL);

  @DiagnosticParameter(
    type = String.class,
    defaultValue = REGEX_EXCLUSION
  )
  private Pattern searchWordsExclusion = getLocalPattern(REGEX_EXCLUSION);

  public UsingHardcodeNetworkAddressDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  private static Pattern getLocalPattern(String content) {
    return Pattern.compile(content, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  }

  @Override
  public void configure(Map<String, Object> configuration) {

    if (configuration == null) {
      return;
    }

    // Слова исключения, при поиске IP адресов
    String searchWordsExclusionProperty =
      (String) configuration.getOrDefault("searchWordsExclusion", REGEX_EXCLUSION);
    searchWordsExclusion = getLocalPattern(searchWordsExclusionProperty);

  }

  /**
   * Проверяем строковые литералы на пути содержание IP4 / IP6.
   * Пример:
   * <p>
   * СетевойПуть = "127.0.0.1";
   */
  @Override
  public ParseTree visitString(BSLParser.StringContext ctx) {
    String content = ctx.getText().replace("\"", "");
    if (content.length() > 2) {
      Matcher matcherURL = patternURL.matcher(content);
      if (!matcherURL.find()) {
        processVisitString(ctx, content);
      }
    }
    return ctx;
  }

  private void processVisitString(BSLParser.StringContext ctx, String content) {
    if (patternNetworkAddress.matcher(content).find()) {
      ParserRuleContext parent = Trees.getAncestorByRuleIndex(ctx, BSLParser.RULE_statement);
      if (parent != null) {
        if (searchWordsExclusion.matcher(parent.getText()).find()) {
          return;
        }
      }
      diagnosticStorage.addDiagnostic(ctx);
    }
  }
}
