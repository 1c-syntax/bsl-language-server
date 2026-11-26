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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.CRITICAL,
  scope = DiagnosticScope.BSL,
  minutesToFix = 15,
  tags = {
    DiagnosticTag.STANDARD
  }
)
public class UsingHardcodePathDiagnostic extends AbstractVisitorDiagnostic {

  private static final String REGEX_PATH =
    "^(?=\\/).*|^(%.*%)(?=\\\\|\\/|\\/\\/)|^(~)(?=\\\\|\\/|\\/\\/)|(^([a-z]):" +
      "(?=\\\\|\\/\\/(?![\0-\37<>:\"\\/\\\\|?*])|\\/(?![\0-\37<>:\"\\/\\\\|?*])|$)|^\\\\(?=[\\\\\\/]" +
      "[^\0-\37<>:\"\\/\\\\|?*]+)|^(?=(\\\\|\\/|\\/\\/)$)^\\.(?=(\\\\|\\/|\\/\\/)[^\0-\37<>:\"\\/\\\\|?*]+))" +
      "((\\\\|\\/|\\/\\/)[^\0-\37<>:\"\\/\\\\|?*]+|(\\\\|\\/|\\/\\/)$)*()$";

  private static final String REGEX_STD_PATHS_UNIX =
    "bin|boot|dev|etc|home|lib|lost\\+found|misc|mnt|" +
      "media|opt|proc|root|run|sbin|tmp|usr|var";

  private static final String REGEX_URL = "^(ftp|http|https):\\/\\/[^ \"].*";

  private static final Pattern patternPath = CaseInsensitivePattern.compile(REGEX_PATH);
  private static final Pattern patternURL = CaseInsensitivePattern.compile(REGEX_URL);

  @DiagnosticParameter(
    type = String.class,
    defaultValue = REGEX_STD_PATHS_UNIX
  )
  private Pattern searchWordsStdPathsUnix = CaseInsensitivePattern.compile("^\\/(" + REGEX_STD_PATHS_UNIX + ")");

  @Override
  public void configure(Map<String, Object> configuration) {
    // Слова поиска стандартных корневых каталогов Unix
    String searchWordsStdPathsUnixProperty =
      (String) configuration.getOrDefault("searchWordsStdPathsUnix", REGEX_STD_PATHS_UNIX);
    searchWordsStdPathsUnix = CaseInsensitivePattern.compile("^\\/(" + searchWordsStdPathsUnixProperty + ")");

  }

  /**
   * Проверяем строковые литералы на пути к файлам и папкам Windows / Unix
   * и IP4 / IP6 сетевые адреса.
   * Пример:
   * КаталогПрограмм = "C:\Program Files (x86)\";
   */
  @Override
  public ParseTree visitString(BSLParser.StringContext ctx) {
    // пропускаю 4 символа, т.к. 2 кавычки и значимые 2 символа точно не попадют под регулярки
    if (ctx.getText().length() > 4) {
      String content = ctx.getText().substring(1, ctx.getText().length() - 1);
      Matcher matcher = patternPath.matcher(content);
      Matcher matcherURL = patternURL.matcher(content);
      if (matcher.find() && !matcherURL.find()) {
        processVisitString(ctx, content);
      }
    }
    return ctx;
  }

  private void processVisitString(BSLParser.StringContext ctx, String content) {
    // Проверим пути с / на стандартные корневые каталоги и обработаем их отдельно
    if (content.startsWith("/")) {
      Matcher matcher = searchWordsStdPathsUnix.matcher(content);
      if (!matcher.find()) {
        return;
      }
    }

    diagnosticStorage.addDiagnostic(ctx);
  }

}
