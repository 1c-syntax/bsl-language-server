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

import com.github._1c_syntax.bsl.languageserver.context.Trees;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.CRITICAL,
  scope = DiagnosticScope.BSL,
  minutesToFix = 15
)
public class UsingHardcodePathDiagnosticDiagnostic extends AbstractVisitorDiagnostic {

  final static String REGEX_PATH = "^(?=\\/).*|^(%.*%)(?=\\\\|\\/|\\/\\/)|^(~)(?=\\\\|\\/|\\/\\/)|(^([a-z]):" +
    "(?=\\\\|\\/\\/(?![\0-\37<>:\"\\/\\\\|?*])|\\/(?![\0-\37<>:\"\\/\\\\|?*])|$)|^\\\\(?=[\\\\\\/]" +
    "[^\0-\37<>:\"\\/\\\\|?*]+)|^(?=(\\\\|\\/|\\/\\/)$)^\\.(?=(\\\\|\\/|\\/\\/)[^\0-\37<>:\"\\/\\\\|?*]+))" +
    "((\\\\|\\/|\\/\\/)[^\0-\37<>:\"\\/\\\\|?*]+|(\\\\|\\/|\\/\\/)$)*()$";

  final static String REGEX_NETWORK_ADDRESS = "(([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:" +
    "|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:)" +
    "{1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}" +
    "(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)" +
    "|fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|(2[0-4]|1{0,1}" +
    "[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|([0-9a-fA-F]{1,4}:){1,4}:" +
    "((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9]))" +
    "|((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])";

  final static String REGEX_STD_PATHS_UNIX = "bin|boot|dev|etc|home|lib|lost\\+found|misc|mnt|" +
    "media|opt|proc|root|run|sbin|tmp|usr|var";

  final static String REGEX_EXCLUSION = "Верси|Version";

  private static final Pattern patternPath = getLocalPattern(REGEX_PATH);
  private static final Pattern patternNetworkAddress = getLocalPattern(REGEX_NETWORK_ADDRESS);

  @DiagnosticParameter(
    type = String.class,
    defaultValue = REGEX_EXCLUSION,
    description = "Ключевые слова поиска для исключения выражений при поиске IP адресов"
  )
  private String searchWordsExclusion = REGEX_EXCLUSION;
  private Pattern patternExclusion = getLocalPattern(REGEX_EXCLUSION);

  @DiagnosticParameter(
    type = String.class,
    defaultValue = REGEX_EXCLUSION,
    description = "Ключевые слова поиска стандартных корневых каталогов Unix"
  )
  private String searchWordsStdPathsUnix = REGEX_STD_PATHS_UNIX;
  private Pattern patternStdPathsUnix = getLocalPattern("^\\/(" + searchWordsStdPathsUnix + ")");

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "true",
    description = "Ключевые слова поиска конфиденциальной информации в переменных, структурах, соответствиях."
  )
  private boolean enableSearchNetworkAddresses = true;

  @Override
  public void configure(Map<String, Object> configuration) {
    if (configuration == null) {
      return;
    }
    // Включение поиска ip адресов
    enableSearchNetworkAddresses = (boolean) configuration.get("enableSearchNetworkAddresses");

    // Слова исключения, при поиске IP адресов
    searchWordsExclusion = (String) configuration.get("searchWordsExclusion");
    patternExclusion = getLocalPattern(searchWordsExclusion);
  }

  /**
   * Проверяем строковые литералы на пути к файлам и папкам Windows / Unix
   * и IP4 / IP6 сетевые адреса.
   * Пример:
   * КаталогПрограмм = "C:\Program Files (x86)\";
   * <p>
   * или
   * <p>
   * СетевойПуть = "127.0.0.1";
   */
  @Override
  public ParseTree visitString(BSLParser.StringContext ctx) {
    String content = ctx.getText().replace("\"", "");
    if (content.length() > 2) {
      Matcher matcher = patternPath.matcher(content);
      if (matcher.find()) {
        processSearchingPath(ctx, content);
      } else if (enableSearchNetworkAddresses) {
        processSearchingNetworkAddress(ctx, content);
      }
    }
    return super.visitString(ctx);
  }

  private void processSearchingPath(BSLParser.StringContext ctx, String content) {
    // Проверим пути с / на стандартные корневые каталоги и обработаем их отдельно
    if (content.startsWith("/")) {
      Matcher matcher = patternStdPathsUnix.matcher(content);
      if (matcher.find()) {
        diagnosticStorage.addDiagnostic(ctx, getDiagnosticMessage());
      }
    } else {
      diagnosticStorage.addDiagnostic(ctx, getDiagnosticMessage());
    }
  }

  private void processSearchingNetworkAddress(BSLParser.StringContext ctx, String content) {
    Matcher matcher = patternNetworkAddress.matcher(content);
    if (matcher.find()) {
      ParserRuleContext parent = Trees.getAncestorByRuleIndex(ctx, BSLParser.RULE_statement);
      if (parent == null) {
        diagnosticStorage.addDiagnostic(ctx, getDiagnosticMessage());
      } else {
        matcher = patternExclusion.matcher(parent.getText());
        if (!matcher.find()) {
          diagnosticStorage.addDiagnostic(ctx, getDiagnosticMessage());
        }
      }
    }
  }

  private static Pattern getLocalPattern(String content) {
    return Pattern.compile(content, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  }

}
